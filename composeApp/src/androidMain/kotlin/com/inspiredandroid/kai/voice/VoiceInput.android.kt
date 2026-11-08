package com.inspiredandroid.kai.voice

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** On-device recognizer only. No cloud recognizer fallback, recording file or auto-send. */
@Composable
actual fun VoiceInputControl(enabled: Boolean, contextKey: String, replyAvailable: Boolean, onStart: () -> Unit, onTranscript: (String) -> Unit) {
    if (LocalInspectionMode.current) return
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val gate = remember { VoiceSession() }
    val currentEnabled by rememberUpdatedState(enabled)
    val currentKey by rememberUpdatedState(contextKey)
    val startCallback by rememberUpdatedState(onStart)
    val transcriptCallback by rememberUpdatedState(onTranscript)
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var timeout by remember { mutableStateOf<Job?>(null) }
    var hardTimeout by remember { mutableStateOf<Job?>(null) }
    var focus by remember { mutableStateOf<AudioFocusRequest?>(null) }
    var busy by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var activeToken by remember { mutableStateOf(0L) }
    var permissionPending by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf("en-US") }
    var languagesOpen by remember { mutableStateOf(false) }
    var needsDownload by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Tap Speak offline, review the transcript, then Send.") }
    val available = remember { android.os.Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }
    val audio = remember { context.getSystemService(AudioManager::class.java) }

    fun release() {
        timeout?.cancel()
        timeout = null
        hardTimeout?.cancel()
        hardTimeout = null
        gate.cancel()
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
        busy = false
        finishing = false
        listening = false
    }
    fun visibleUnlocked() = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
        context.getSystemService(PowerManager::class.java).isInteractive &&
        !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
    fun fail(message: String) {
        release()
        status = message
    }
    fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required. Tap Speak offline to request it."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "This language is not installed for offline recognition. Download it or choose another language."
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No clear speech was recognized. Tap Speak offline to try again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The recognizer is busy. Wait briefly, then tap Speak offline."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "The on-device recognizer could not finish. No network recognizer was used."
        SpeechRecognizer.ERROR_AUDIO -> "Audio input was interrupted or unavailable. No transcript was sent."
        else -> "Offline recognition could not finish (code $code). Nothing was sent."
    }
    fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }
    fun begin() {
        if (!currentEnabled || !available || !visibleUnlocked()) return
        release()
        val token = gate.begin(currentKey)
        activeToken = token
        hardTimeout = scope.launch {
            delay(46_000)
            if (gate.isActive(token, currentKey)) fail("The voice session reached its time limit. Nothing was sent.")
        }
        busy = true
        needsDownload = false
        status = "Checking offline language…"
        val request = intent()
        val asr = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer = asr
        fun live() = gate.isActive(token, currentKey) && currentEnabled && visibleUnlocked()
        asr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (live()) {
                    listening = true
                    status = "Listening on this phone… Tap Finish when done."
                } else if (gate.isActive(token, currentKey)) {
                    release()
                }
            }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                if (!live()) {
                    if (gate.isActive(token, currentKey)) release()
                    return
                }
                finishing = true
                listening = false
                status = "Finishing offline transcription…"
                timeout?.cancel()
                timeout = scope.launch {
                    delay(8_000)
                    if (gate.isActive(token, currentKey)) fail("Recognition timed out. Nothing was sent.")
                }
            }
            override fun onError(error: Int) {
                if (gate.isActive(token, currentKey)) {
                    needsDownload = error in setOf(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
                    fail(errorMessage(error))
                }
            }
            override fun onResults(results: Bundle?) {
                if (!live()) {
                    if (gate.isActive(token, currentKey)) release()
                    return
                }
                val text = gate.complete(token, currentKey, results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                release()
                if (text != null && currentEnabled && visibleUnlocked()) {
                    status = "Transcript ready. Review it, then tap Send."
                    transcriptCallback(text)
                } else {
                    status = "No clear transcript was returned. Nothing was sent."
                }
            }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        fun listen() {
            if (!live()) {
                if (gate.isActive(token, currentKey)) release()
                return
            }
            startCallback()
            val f = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener { change -> if (change < 0 && gate.isActive(token, currentKey)) fail("Audio focus changed. Listening stopped; nothing was sent.") }.build()
            focus = f
            if (audio.requestAudioFocus(f) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                fail("Audio is busy. Listening did not start.")
                return
            }
            timeout?.cancel()
            timeout = scope.launch {
                delay(30_000)
                if (gate.isActive(token, currentKey)) fail("Listening reached 30 seconds and stopped. Nothing was sent.")
            }
            status = "Starting offline microphone…"
            try {
                asr.startListening(request)
            } catch (_: Exception) {
                fail("Offline microphone could not start. Nothing was sent.")
            }
        }
        timeout = scope.launch {
            delay(8_000)
            if (gate.isActive(token, currentKey)) fail("Offline language check timed out. Nothing was sent.")
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            asr.checkRecognitionSupport(
                request,
                ContextCompat.getMainExecutor(context),
                object : RecognitionSupportCallback {
                    override fun onSupportResult(result: RecognitionSupport) {
                        if (!live()) {
                            if (gate.isActive(token, currentKey)) release()

                            return
                        }
                        val installed = result.installedOnDeviceLanguages.map { it.replace('_', '-').lowercase() }
                        if (language.lowercase() in installed) {
                            listen()
                        } else {
                            needsDownload = true
                            fail("Offline $language is not installed. Use Download language, then try again.")
                        }
                    }
                    override fun onError(error: Int) {
                        if (!live()) {
                            if (gate.isActive(token, currentKey)) release()

                            return
                        }
                        if (error == SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT) listen() else fail(errorMessage(error))
                    }
                },
            )
        } else {
            listen()
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionPending = false
        status = if (granted) "Microphone allowed. Tap Speak offline to start." else "Microphone denied. Typing remains available."
    }
    LaunchedEffect(enabled, contextKey) {
        if (!enabled || busy) {
            if (busy) status = "Listening stopped because the chat or draft changed."
            release()
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                release()
                status = "Listening stopped when Kai left the foreground."
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            release()
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (busy) {
                if (listening && !finishing) {
                    TextButton(onClick = {
                        val token = activeToken
                        finishing = true
                        listening = false
                        status = "Finishing offline transcription…"
                        timeout?.cancel()
                        timeout = scope.launch {
                            delay(8_000)
                            if (gate.isActive(token, currentKey)) fail("Recognition timed out. Nothing was sent.")
                        }
                        try {
                            recognizer?.stopListening()
                        } catch (_: Exception) {
                            fail("Offline recognition stopped. Nothing was sent.")
                        }
                    }) { Text("Finish speech") }
                }
                TextButton(onClick = {
                    release()
                    status = "Listening cancelled. Nothing was sent."
                }) { Text("Cancel speech") }
            } else {
                TextButton(enabled = enabled && available && !permissionPending, onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        permissionPending = true
                        permission.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        try {
                            begin()
                        } catch (_: Exception) {
                            fail("Offline recognition could not start. Nothing was sent.")
                        }
                    }
                }) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Text("Speak offline")
                }
                Box {
                    TextButton(enabled = enabled && !permissionPending, onClick = { languagesOpen = true }) { Text(language) }
                    DropdownMenu(expanded = languagesOpen, onDismissRequest = { languagesOpen = false }) {
                        listOf("en-US" to "English (US)", "en-PH" to "English (Philippines)", "fil-PH" to "Filipino").forEach { (tag, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                release()
                                language = tag
                                needsDownload = false
                                languagesOpen = false
                                status = "Selected $label. Offline availability is checked before listening."
                            })
                        }
                    }
                }
            }
        }
        Text(if (available) status else "On-device recognition is unavailable. Voice input will not use a network recognizer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (needsDownload && available && android.os.Build.VERSION.SDK_INT >= 33) {
            TextButton(enabled = enabled && !busy, onClick = {
                release()
                try {
                    recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                    recognizer?.triggerModelDownload(intent())
                    status = "Requested the offline language download from Android. Try Speak offline after it finishes."
                } catch (_: Exception) {
                    fail("Android could not request this offline language. No network recognizer was used.")
                }
            }) { Text("Download offline language") }
        }
        if (!replyAvailable) Text("Offline reply voice is not ready. You can still dictate; check Local voice in Settings → Agent.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Audio stays on this phone. Sending the transcript uses the selected chat provider.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
