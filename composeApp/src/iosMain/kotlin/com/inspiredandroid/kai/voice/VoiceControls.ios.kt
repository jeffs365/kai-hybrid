package com.inspiredandroid.kai.voice

import androidx.compose.runtime.Composable

@Composable
actual fun VoiceInputControl(enabled: Boolean, contextKey: String, replyAvailable: Boolean, onStart: () -> Unit, onTranscript: (String) -> Unit) = Unit

@Composable
actual fun LocalVoiceSettings() = Unit
