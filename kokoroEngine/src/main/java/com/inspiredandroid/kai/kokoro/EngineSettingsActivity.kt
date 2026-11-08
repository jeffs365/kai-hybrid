// SPDX-License-Identifier: GPL-3.0-or-later
package com.inspiredandroid.kai.kokoro

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class EngineSettingsActivity : Activity() {
    private var tts: TextToSpeech? = null
    private lateinit var status: TextView
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }
        fun text(value: String, size: Float = 16f) {
            column.addView(
                TextView(this).apply {
                    text = value
                    textSize = size
                    setPadding(0, 12, 0, 12)
                },
            )
        }
        fun button(label: String, click: () -> Unit) {
            column.addView(
                Button(this).apply {
                    text = label
                    setOnClickListener { click() }
                },
            )
        }
        text("Kai Kokoro · Offline voice", 24f)
        text("English voices generated locally. No internet permission, microphone, text logging or saved speech audio. George is the default British male voice.")
        status = TextView(this).apply {
            text = if (ModelFiles.ready(this@EngineSettingsActivity)) "Offline voice files verified. Ready." else "Voice files need verification. Approximate model storage: 361 MiB."
            textSize = 16f
        }
        column.addView(status)
        button("Verify offline voice files") {
            status.text = "Checking voice file integrity…"
            Thread {
                val ok = runCatching { ModelFiles.verify(this) }.getOrDefault(false)
                runOnUiThread { status.text = if (ok) "Offline voice files verified. George is ready." else "Voice files are missing or differ from the expected version. Setup needs to be completed." }
            }.start()
        }
        fun sample(name: String, longTest: Boolean = false) {
            if (!ModelFiles.ready(this)) {
                status.text = "Verify offline voice files first."
                return
            }
            tts?.stop()
            tts?.shutdown()
            tts = TextToSpeech(this, { code ->
                val engine = tts
                val voice = engine?.voices?.firstOrNull { it.name == name && !it.isNetworkConnectionRequired }
                if (code == TextToSpeech.SUCCESS && engine != null && voice != null) {
                    engine.voice = voice
                    engine.setSpeechRate(0.95f)
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {
                            runOnUiThread { status.text = "Playing offline voice sample…" }
                        }
                        override fun onDone(id: String?) {
                            runOnUiThread { status.text = "Offline voice sample completed." }
                        }
                        override fun onError(id: String?) {
                            runOnUiThread { status.text = "Offline voice sample failed. Try verifying the voice files." }
                        }
                        override fun onStop(id: String?, interrupted: Boolean) {
                            runOnUiThread { status.text = "Speech stopped." }
                        }
                    })
                    val phrase = if (longTest) "This is a local cancellation test. The Stop button should silence this sample and stop further generation. ".repeat(12) else "Good evening. I am Kai. This voice is generated entirely on your phone."
                    val accepted = engine.speak(phrase, TextToSpeech.QUEUE_FLUSH, Bundle(), "kai-public-sample")
                    status.text = if (accepted == TextToSpeech.SUCCESS) "Public voice sample queued." else "Voice sample could not be queued."
                } else {
                    status.text = "Offline voice could not start."
                }
            }, packageName)
        }
        button("Play George sample") { sample("kai-kokoro-bm_george") }
        button("Play Daniel sample") { sample("kai-kokoro-bm_daniel") }
        button("Play Lewis sample") { sample("kai-kokoro-bm_lewis") }
        button("Play long Stop test") { sample("kai-kokoro-bm_george", longTest = true) }
        button("Stop voice") {
            tts?.stop()
            status.text = "Speech stopped."
        }
        button("Manage voice storage") { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        text("Personal build · GPL-3.0-or-later. Sherpa-onnx and Kokoro use Apache-2.0; ONNX Runtime uses MIT; eSpeak-NG uses GPL. Source and exact dependency pins are saved in the Kai Hybrid repository.", 13f)
        setContentView(
            ScrollView(this).apply {
                addView(column)
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    setOnApplyWindowInsetsListener { view, insets ->
                        val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                        view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                        insets
                    }
                }
            },
        )
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            )
        }
    }
    override fun onStop() {
        tts?.stop()
        super.onStop()
    }
    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}

class CheckVoiceDataActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val ready = ModelFiles.ready(this)
        val result = Intent().putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, arrayListOf<String>().apply { if (ready) add("eng-GBR") })
            .putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, arrayListOf<String>().apply { if (!ready) add("eng-GBR") })
        setResult(if (ready) TextToSpeech.Engine.CHECK_VOICE_DATA_PASS else TextToSpeech.Engine.CHECK_VOICE_DATA_FAIL, result)
        finish()
    }
}
