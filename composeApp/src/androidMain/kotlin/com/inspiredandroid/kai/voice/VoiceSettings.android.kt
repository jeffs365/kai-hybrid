package com.inspiredandroid.kai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inspiredandroid.kai.data.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.koin.compose.koinInject
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Official separate Android TTS engine, not a native runtime bundled into Kai.
const val KOKORO_CHAT_READY = false
const val KOKORO_ENGINE_PACKAGE = "com.k2fsa.sherpa.onnx.tts.engine"

@Composable
actual fun LocalVoiceSettings() {
    if (LocalInspectionMode.current) return
    val context = LocalContext.current
    val settings: AppSettings = koinInject()
    val selected by settings.localVoiceEngine.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Microphone starts only from Speak offline in chat. Recognition never falls back to a network service.") }
    val installed = remember { context.packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0).any { it.serviceInfo.packageName == KOKORO_ENGINE_PACKAGE } }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Local voice", style = MaterialTheme.typography.titleMedium)
            Text("Selected reply engine: ${if (selected == "kokoro") "Kokoro" else "System default"}. Kai selects an installed offline English voice; otherwise speech stays unavailable.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !busy, onClick = { settings.setLocalVoiceEngine("system") }) { Text("Use system voice") }
                TextButton(enabled = installed && KOKORO_CHAT_READY && !busy, onClick = { settings.setLocalVoiceEngine("kokoro") }) { Text("Use Kokoro") }
            }
            if (installed) {
                TextButton(enabled = !busy, onClick = {
                    context.packageManager.getLaunchIntentForPackage(KOKORO_ENGINE_PACKAGE)?.let { context.startActivity(it) }
                }) { Text("Configure Kokoro voice") }
            }
            Text("Kokoro is available for fixed-phrase testing only while its privacy and interruption behavior are reviewed. Chat replies use an offline system voice. Android's global default is unchanged.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    message = "Checking on-device recognition…"
                    scope.launch {
                        try {
                            val result = probeOfflineSpeech(context)
                            File(context.cacheDir, "voice-recognition-diagnostics.json").writeText(result.toString(2))
                            message = result.optString("summary")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            message = "Offline recognition check could not finish. Grant microphone from the chat button if requested, then try again."
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Check offline speech") }
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    message = "Synthesizing three fixed test phrases locally…"
                    val engine = if (selected == "kokoro") KOKORO_ENGINE_PACKAGE else null
                    scope.launch {
                        try {
                            val result = benchmarkLocalVoice(context, engine)
                            File(context.cacheDir, "voice-tts-benchmark.json").writeText(result.toString(2))
                            message = result.optString("summary")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            message = "Local voice benchmark failed or no offline English voice is installed. No network voice was selected."
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Benchmark local voice") }
            }
            if (installed) {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    message = "Testing Kokoro with fixed public phrases only…"
                    scope.launch {
                        try {
                            val result = benchmarkLocalVoice(context, KOKORO_ENGINE_PACKAGE)
                            File(context.cacheDir, "voice-kokoro-benchmark.json").writeText(result.toString(2))
                            message = result.optString("summary")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            message = "Kokoro test could not finish. System voice remains selected for replies."
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Benchmark Kokoro prototype") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private suspend fun probeOfflineSpeech(context: Context): JSONObject {
    val result = JSONObject().put("on_device_available", android.os.Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))
    if (!result.getBoolean("on_device_available")) return result.put("summary", "Android reports no on-device recognizer. Voice input stays unavailable.")
    if (android.os.Build.VERSION.SDK_INT < 33) return result.put("summary", "On-device recognizer exists. Language support must be tested by speaking.")
    val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
    try {
        return withTimeout(10_000) {
            suspendCancellableCoroutine { continuation ->
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US").putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                recognizer.checkRecognitionSupport(
                    intent,
                    ContextCompat.getMainExecutor(context),
                    object : RecognitionSupportCallback {
                        override fun onSupportResult(support: RecognitionSupport) {
                            if (!continuation.isActive) return
                            result.put("installed_on_device_languages", JSONArray(support.installedOnDeviceLanguages))
                            result.put("supported_on_device_languages", JSONArray(support.supportedOnDeviceLanguages))
                            result.put("pending_on_device_languages", JSONArray(support.pendingOnDeviceLanguages))
                            result.put("summary", "On-device recognizer available. Installed languages: ${support.installedOnDeviceLanguages.joinToString().ifBlank { "none reported" }}. Live speech accuracy is not yet verified.")
                            continuation.resume(result)
                        }
                        override fun onError(error: Int) {
                            if (continuation.isActive) continuation.resume(result.put("error_code", error).put("summary", "On-device recognizer exists; language check returned code $error. Live speech is not yet verified."))
                        }
                    },
                )
            }
        }
    } finally {
        recognizer.destroy()
    }
}

private suspend fun createNativeTts(context: Context, engine: String?): TextToSpeech = withTimeout(30_000) {
    suspendCancellableCoroutine { continuation ->
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context, { code ->
            val value = instance
            if (continuation.isActive) {
                if (code == TextToSpeech.SUCCESS && value != null) {
                    continuation.resume(value)
                } else {
                    value?.shutdown()
                    continuation.resumeWithException(IllegalStateException("TTS unavailable"))
                }
            } else {
                value?.shutdown()
            }
        }, engine)
        continuation.invokeOnCancellation { instance?.shutdown() }
    }
}

private suspend fun benchmarkLocalVoice(context: Context, engine: String?): JSONObject {
    val start = SystemClock.elapsedRealtime()
    val tts = createNativeTts(context, engine)
    try {
        val voice = tts.voices.orEmpty().filter { !it.isNetworkConnectionRequired && it.locale.language == "en" }
            .sortedByDescending { it.locale.country == "GB" }.firstOrNull() ?: error("Offline English voice unavailable")
        check(tts.setVoice(voice) == TextToSpeech.SUCCESS)
        val result = JSONObject().put("requested_engine", engine ?: "system").put("voice", voice.name).put("offline_voice", true)
            .put("initialization_ms", SystemClock.elapsedRealtime() - start)
        val rows = JSONArray()
        val samples = listOf("Hello. I am Kai, and this voice is generated on your phone.", "Your next reminder is ready. You can ask me to change it.", "Good evening. How can I help you today?")
        for ((index, text) in samples.withIndex()) {
            val file = File(context.cacheDir, "kai-voice-benchmark-$index.wav")
            try {
                val metrics = withTimeout(60_000) {
                    suspendCancellableCoroutine<JSONObject> { continuation ->
                        val began = SystemClock.elapsedRealtime()
                        var first = -1L
                        var bytes = 0L
                        var sampleRate = 0
                        var encoding = 0
                        var channels = 1
                        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) = Unit
                            override fun onBeginSynthesis(utteranceId: String?, rate: Int, format: Int, channelCount: Int) {
                                sampleRate = rate
                                encoding = format
                                channels = channelCount
                            }
                            override fun onAudioAvailable(utteranceId: String?, buffer: ByteArray?) {
                                if (first < 0 && buffer != null && buffer.isNotEmpty()) first = SystemClock.elapsedRealtime() - began
                                bytes += buffer?.size ?: 0
                            }
                            override fun onDone(utteranceId: String?) {
                                if (!continuation.isActive) return
                                val duration = if (sampleRate > 0 && encoding == android.media.AudioFormat.ENCODING_PCM_16BIT) bytes.toDouble() / (sampleRate * channels * 2) else 0.0
                                val elapsed = SystemClock.elapsedRealtime() - began
                                continuation.resume(JSONObject().put("sample", index).put("first_synthesized_audio_ms", first).put("total_synthesis_ms", elapsed).put("audio_seconds", duration).put("real_time_factor", if (duration > 0) elapsed / 1000.0 / duration else JSONObject.NULL))
                            }
                            override fun onError(utteranceId: String?) {
                                if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Synthesis failed"))
                            }
                        })
                        continuation.invokeOnCancellation { tts.stop() }
                        if (tts.synthesizeToFile(text, Bundle(), file, "kai-benchmark-$index") != TextToSpeech.SUCCESS && continuation.isActive) continuation.resumeWithException(IllegalStateException("Synthesis rejected"))
                    }
                }
                rows.put(metrics)
            } finally {
                file.delete()
            }
        }
        result.put("samples", rows)
        result.put("summary", "Offline synthesis completed. First audio: ${(0 until rows.length()).joinToString { rows.getJSONObject(it).getLong("first_synthesized_audio_ms").toString() }} ms. This measures synthesis, not audible response latency.")
        return result
    } finally {
        tts.stop()
        tts.shutdown()
    }
}
