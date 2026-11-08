package com.inspiredandroid.kai

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.data.ReminderDelivery
import com.inspiredandroid.kai.data.ThemeMode
import com.inspiredandroid.kai.ui.DarkColorScheme
import com.inspiredandroid.kai.ui.LightColorScheme
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import nl.marc_apps.tts.TextToSpeechEngine
import nl.marc_apps.tts.TextToSpeechFactory
import nl.marc_apps.tts.TextToSpeechInstance
import org.koin.android.ext.android.get

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        FileKit.init(this)
        handleDeepLinkIntent(intent)

        val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val appSettings: AppSettings = get()
        setContent {
            val themeMode by appSettings.themeModeFlow.collectAsStateWithLifecycle()
            val systemInDark = isSystemInDarkTheme()
            val isDarkTheme = when (themeMode) {
                ThemeMode.System -> systemInDark
                ThemeMode.Light -> false
                ThemeMode.Dark, ThemeMode.OledBlack -> true
            }
            LaunchedEffect(isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    },
                    navigationBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    },
                )
            }
            val context = LocalContext.current
            val lightScheme: ColorScheme = if (dynamicColor) dynamicLightColorScheme(context) else LightColorScheme
            val darkScheme: ColorScheme = if (dynamicColor) dynamicDarkColorScheme(context) else DarkColorScheme
            val navController = rememberNavController()
            // Defer TTS initialization until after the first frame
            var ttsReady by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { ttsReady = true }
            val textToSpeech = if (ttsReady) {
                val voiceEngine by appSettings.localVoiceEngine.collectAsStateWithLifecycle()
                val voiceName by appSettings.kokoroVoice.collectAsStateWithLifecycle()
                val voiceSpeed by appSettings.kokoroSpeed.collectAsStateWithLifecycle()
                val voiceRevision by appSettings.localVoiceRevision.collectAsStateWithLifecycle()
                rememberSystemTextToSpeechOrNull(voiceEngine, voiceName, voiceSpeed, voiceRevision)
            } else {
                null
            }
            App(
                navController = navController,
                lightColorScheme = lightScheme,
                darkColorScheme = darkScheme,
                textToSpeech = textToSpeech,
                isKoinStarted = true,
                onAppOpens = { appOpens ->
                    if (appOpens % 5 == 0) {
                        requestReview(this@MainActivity)
                    }
                },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        get<ReminderDelivery>().refresh()
        // Re-assert the daemon every time the activity is brought to the foreground.
        // `onCreate`-only is not enough: aggressive OEM battery managers (MIUI,
        // EMUI/Huawei) sometimes kill the foreground service while the activity
        // is still alive in the background — without this, the user has to fully
        // close and reopen the app for scheduling to resume. `startForegroundService`
        // is idempotent when the service is already up.
        autoStartDaemon()
    }

    private fun autoStartDaemon() {
        val daemonController: DaemonController = get()
        if (daemonController is AndroidDaemonController && daemonController.shouldAutoStart()) {
            daemonController.start()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_HEARTBEAT, false) == true) {
            val dataRepository: DataRepository = get()
            dataRepository.requestOpenHeartbeat()
            // Drop the extra so a configuration change (screen rotation) doesn't re-trigger
            // the deep-link after ChatViewModel has already consumed it.
            intent.removeExtra(EXTRA_OPEN_HEARTBEAT)
        }
        if (intent?.action == Intent.ACTION_ASSIST) {
            val dataRepository: DataRepository = get()
            dataRepository.requestOpenAssist()
            // Clear the action so a configuration change doesn't re-trigger a fresh
            // chat after ChatViewModel has already consumed the request.
            intent.action = null
        }
        if (intent?.action == Intent.ACTION_SEND) {
            val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
                ?.toString()
                ?.trim()
                .orEmpty()
            if (sharedText.isNotEmpty()) {
                val dataRepository: DataRepository = get()
                dataRepository.requestOpenShare(sharedText)
            }
            // Drop the action and extra so rotation doesn't re-apply the share after
            // ChatViewModel has already consumed it.
            intent.action = null
            intent.removeExtra(Intent.EXTRA_TEXT)
        }
    }
}

/**
 * Uses the explicitly selected engine with an installed offline English voice.
 * Kokoro is a separate Android engine; no native runtime is bundled in Kai.
 * Missing engines or offline voices leave speech unavailable rather than choosing a network voice.
 */
@OptIn(nl.marc_apps.tts.experimental.ExperimentalVoiceApi::class)
@Composable
private fun rememberSystemTextToSpeechOrNull(engine: String, voiceName: String, speed: Float, revision: Int): TextToSpeechInstance? {
    val context = LocalContext.current.applicationContext
    var textToSpeech by remember { mutableStateOf<TextToSpeechInstance?>(null) }

    LaunchedEffect(engine, voiceName, speed, revision) {
        textToSpeech?.stop()
        textToSpeech?.close()
        textToSpeech = null
        val factory = if (engine == "kokoro") {
            TextToSpeechFactory(context, TextToSpeechEngine.Custom(com.inspiredandroid.kai.voice.KOKORO_ENGINE_PACKAGE))
        } else {
            TextToSpeechFactory(context, TextToSpeechEngine.SystemDefault)
        }
        val systemEngine = android.provider.Settings.Secure.getString(context.contentResolver, "tts_default_synth")
        val requestsKokoro = engine == "kokoro" || (engine == "system" && systemEngine == com.inspiredandroid.kai.voice.KOKORO_ENGINE_PACKAGE)
        val unsafeSystemEngine = engine == "system" && systemEngine == com.inspiredandroid.kai.voice.LEGACY_KOKORO_ENGINE_PACKAGE
        val allowed = !unsafeSystemEngine && (!requestsKokoro || com.inspiredandroid.kai.voice.trustedKokoroEngine(context))
        val candidate = if (allowed && factory.isSupported) factory.createOrNull() else null
        if (candidate != null) {
            try {
                val voices = candidate.voices.filter { !it.isOnline && it.languageTag.startsWith("en", ignoreCase = true) }.toList()
                val voice = if (requestsKokoro) {
                    voices.firstOrNull { it.name == voiceName && it.name in com.inspiredandroid.kai.voice.KokoroProfiles.voices }
                } else {
                    candidate.currentVoice?.takeIf { it in voices }
                        ?: voices.firstOrNull { it.languageTag.startsWith("en-GB", ignoreCase = true) }
                        ?: voices.firstOrNull()
                }
                if (voice == null) {
                    candidate.close()
                } else {
                    candidate.currentVoice = voice
                    if (requestsKokoro) candidate.rate = speed
                    textToSpeech = candidate
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                candidate.close()
                throw e
            } catch (_: Exception) {
                candidate.close()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            textToSpeech?.close()
            textToSpeech = null
        }
    }

    return textToSpeech
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(navController = rememberNavController())
}
