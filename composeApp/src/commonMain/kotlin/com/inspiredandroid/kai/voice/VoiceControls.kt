package com.inspiredandroid.kai.voice

import androidx.compose.runtime.Composable

@Composable
expect fun VoiceInputControl(enabled: Boolean, contextKey: String, replyAvailable: Boolean, onStart: () -> Unit, onTranscript: (String) -> Unit)

@Composable
expect fun LocalVoiceSettings()
