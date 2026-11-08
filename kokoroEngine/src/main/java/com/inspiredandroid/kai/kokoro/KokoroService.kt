// SPDX-License-Identifier: GPL-3.0-or-later
package com.inspiredandroid.kai.kokoro

import android.media.AudioFormat
import android.os.SystemClock
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.system.Os
import android.system.OsConstants
import com.k2fsa.sherpa.onnx.*
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock

/** This process owns no network permission, microphone, message log or audio archive. */
class KokoroService : TextToSpeechService() {
    private val gate = SynthesisGate { SystemClock.elapsedRealtime() }
    private val lane = ReentrantLock()
    private var model: OfflineTts? = null

    @Volatile private var destroyed = false

    @Volatile private var stopAt = 0L

    override fun onCreate() {
        // Native phonemizer error paths may write input fragments to stdio. Discard only
        // this speech engine process's stdio. sherpa Android logging is compiled out too.
        val sink = Os.open("/dev/null", OsConstants.O_WRONLY, 0)
        try {
            Os.dup2(sink, 1)
            Os.dup2(sink, 2)
        } finally {
            Os.close(sink)
        }
        super.onCreate()
    }
    override fun onGetLanguage() = arrayOf("eng", "GBR", "")
    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int = when {
        lang !in setOf("en", "eng") -> TextToSpeech.LANG_NOT_SUPPORTED
        !ModelFiles.ready(this) -> TextToSpeech.LANG_MISSING_DATA
        country in setOf("GB", "GBR") -> TextToSpeech.LANG_COUNTRY_AVAILABLE
        else -> TextToSpeech.LANG_AVAILABLE
    }
    override fun onLoadLanguage(lang: String?, country: String?, variant: String?) = onIsLanguageAvailable(lang, country, variant)
    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? = SpeechPolicy.DEFAULT_VOICE.takeIf { lang in setOf("en", "eng") && ModelFiles.ready(this) }
    override fun onGetVoices(): List<Voice> = if (ModelFiles.ready(this)) {
        SpeechPolicy.speakers.keys.map {
            Voice(it, Locale.forLanguageTag(SpeechPolicy.languageTag(it)), Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, false, emptySet())
        }
    } else {
        emptyList()
    }
    override fun onIsValidVoiceName(voiceName: String?) = if (voiceName in SpeechPolicy.speakers && ModelFiles.ready(this)) TextToSpeech.SUCCESS else TextToSpeech.ERROR
    override fun onLoadVoice(voiceName: String?) = onIsValidVoiceName(voiceName)
    override fun onStop() {
        stopAt = SystemClock.elapsedRealtime()
        gate.stop()
    }
    override fun onDestroy() {
        destroyed = true
        gate.stop()
        if (lane.tryLock()) {
            try {
                model?.release()
                model = null
            } finally {
                lane.unlock()
            }
        }
        super.onDestroy()
    }
    private fun engine(): OfflineTts {
        model?.let { return it }
        check(ModelFiles.verify(this)) { "model_unavailable" }
        val dir = ModelFiles.directory(this).absolutePath
        return OfflineTts(
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    // eSpeak's British voice file is gmw/en; en-gb is a language
                    // tag, not a selectable voice name in this pinned data set.
                    kokoro = OfflineTtsKokoroModelConfig(model = "$dir/model.onnx", voices = "$dir/voices.bin", tokens = "$dir/tokens.txt", dataDir = "$dir/espeak-ng-data", lexicon = "$dir/lexicon-gb-en.txt", lang = "en"),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                ),
                maxNumSentences = 1,
            ),
        ).also { model = it }
    }
    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        if (request == null || callback == null) return
        if (!lane.tryLock()) {
            callback.error(TextToSpeech.ERROR_SERVICE)
            return
        }
        stopAt = 0L
        val ticket = gate.begin()
        val started = SystemClock.elapsedRealtime()
        var status = "failed"
        var pcmBytes = 0L
        var firstAudio = -1L
        var segments = 0
        var stage = "validation"
        val name = request.voiceName?.takeIf { it.isNotBlank() } ?: SpeechPolicy.DEFAULT_VOICE
        try {
            val sid = SpeechPolicy.speakers[name]
            val chunks = SpeechPolicy.chunks(request.charSequenceText.toString())
            if (sid == null || chunks == null || destroyed) {
                callback.error(TextToSpeech.ERROR_INVALID_REQUEST)
                status = "invalid_request"
                return
            }
            if (!ModelFiles.ready(this)) {
                callback.error(TextToSpeech.ERROR_NOT_INSTALLED_YET)
                status = "model_unavailable"
                return
            }
            if (!gate.active(ticket)) {
                status = "stopped"
                return
            }
            if (chunks.isEmpty()) {
                callback.start(24_000, AudioFormat.ENCODING_PCM_16BIT, 1)
                callback.done()
                status = "completed"
                return
            }
            stage = "initialization"
            val tts = engine()
            if (!gate.active(ticket) || destroyed) {
                status = "stopped"
                return
            }
            if (callback.start(tts.sampleRate(), AudioFormat.ENCODING_PCM_16BIT, 1) != TextToSpeech.SUCCESS) {
                status = "stopped"
                return
            }
            val speed = (request.speechRate / 100f).coerceIn(0.8f, 1.2f)
            for (chunk in chunks) {
                if (!gate.active(ticket) || destroyed || callback.hasFinished()) {
                    status = "stopped"
                    return
                }
                var delivered = false
                var accepted = true
                stage = "generation"
                tts.generateWithCallback(chunk, sid, speed) { samples ->
                    stage = "delivery"
                    if (!gate.active(ticket) || destroyed || callback.hasFinished() || samples.size > 1_440_000) {
                        accepted = false
                        return@generateWithCallback 0
                    }
                    val pcm = ByteArray(samples.size * 2)
                    for (index in samples.indices) {
                        val value = (samples[index].takeIf { it.isFinite() } ?: 0f).coerceIn(-1f, 1f)
                        val scaled = (value * 32767).toInt()
                        pcm[index * 2] = scaled.toByte()
                        pcm[index * 2 + 1] = (scaled shr 8).toByte()
                    }
                    var offset = 0
                    val maxBytes = (callback.maxBufferSize / 2) * 2
                    if (maxBytes < 2) {
                        accepted = false
                        return@generateWithCallback 0
                    }
                    while (offset < pcm.size) {
                        if (!gate.active(ticket) || destroyed || callback.hasFinished()) {
                            accepted = false
                            return@generateWithCallback 0
                        }
                        val size = minOf(maxBytes, pcm.size - offset)
                        if (callback.audioAvailable(pcm, offset, size) != TextToSpeech.SUCCESS) {
                            accepted = false
                            return@generateWithCallback 0
                        }
                        if (firstAudio < 0) firstAudio = SystemClock.elapsedRealtime() - started
                        pcmBytes += size
                        offset += size
                        delivered = true
                    }
                    1
                }
                if (!accepted || !gate.active(ticket)) {
                    status = "stopped"
                    return
                }
                if (!delivered) {
                    callback.error(TextToSpeech.ERROR_SYNTHESIS)
                    status = "no_audio"
                    return
                }
                segments++
            }
            if (gate.active(ticket) && !callback.hasFinished()) {
                callback.done()
                status = "completed"
            } else {
                status = "stopped"
            }
        } catch (error: Throwable) {
            if (gate.active(ticket)) callback.error(TextToSpeech.ERROR_SYNTHESIS)
            status = if (!gate.active(ticket)) {
                "stopped"
            } else {
                when {
                    error is NoSuchMethodError -> "callback_unavailable"
                    error is NullPointerException -> "null_result"
                    error.message == "offline_voice_selection_failed" -> "voice_unavailable"
                    error.message == "offline_voice_native_failure" -> "native_failed"
                    else -> "failed"
                }
            }
        } finally {
            // Fixed metadata only: no utterance, fragments, hashes, request IDs or raw exceptions.
            runCatching {
                val pending = File(filesDir, "last-synthesis.pending")
                pending.writeText(JSONObject().put("status", status).put("stage", stage).put("voice", name.takeIf { it in SpeechPolicy.speakers } ?: "unsupported").put("elapsed_ms", SystemClock.elapsedRealtime() - started).put("first_audio_ms", firstAudio).put("pcm_bytes", pcmBytes).put("segments", segments).put("stop_latency_ms", if (stopAt >= started) SystemClock.elapsedRealtime() - stopAt else -1).toString())
                check(pending.renameTo(File(filesDir, "last-synthesis.json")))
            }
            if (destroyed) {
                model?.release()
                model = null
            }
            lane.unlock()
        }
    }
}
