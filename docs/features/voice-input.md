# Local voice input and reply output

**Last verified:** 2026-11-08

Hybrid.9 development adds a foreground Android speech button to regular chat. Source/build verification and device verification are separate; the release validation record identifies what actually passed. YouTube/accessibility work was deferred by the owner.

## Speak, review, send

Start with an empty composer and no attached files. Tap Speak offline. On first use Android asks for microphone permission; granting it does not automatically start recording. Tap Speak offline again when ready. Speak normally, allow the recognizer to finish, or tap Finish speech. The final transcript appears in the composer for correction. Tap Send to use the current chat provider and its existing tool boundaries.

The microphone is used only in the foreground after an explicit tap and permission. There is no background microphone service, wake word or automatic follow-up listening. Audio is not saved by Kai or placed in API requests. Only a reviewed transcript submitted through Send enters the existing chat/history/provider flow. DeepSeek receives that text when selected; Local Model handles it locally subject to existing explicit consultation/search rules.

Voice input uses Android's specifically on-device recognizer, rather than relying on an offline-preference hint in a generic recognizer. No network-recognition fallback is made. Android must report availability, and supported versions check the selected language before listening. English US, English Philippines and Filipino can be selected; installed-language support and actual English/Taglish accuracy must be measured on the phone. A language download button asks the on-device recognizer to obtain missing language data. This download may need a network connection but does not send an utterance.

Recognition preparation/finish waits are bounded to eight seconds, with a 30-second listening limit and a 46-second absolute session deadline. Cancel speech discards the result. Leaving/resuming the app, changing provider or conversation, submitting a draft, editing the composer, or losing audio focus stops active recognition. A cancelled, completed or stale session cannot deliver another transcript. Partial recognition never submits a request or runs a tool.

## Spoken replies

Starting the microphone stops current TTS playback and enables reply speech for that chat UI session. The current turn still requires Send after transcript review. Existing speech controls can mute/read messages. The Android speech backend selects an engine-reported offline English voice from the chosen system engine. It does not silently choose a network voice or Google fallback when no local voice is available. An unavailable reply voice is indicated by the composer; dictation may still be usable.

Settings → Agent → Local voice offers recognition diagnostics and a local synthesis benchmark. These diagnostics do not record microphone audio. Recognition diagnostics contain availability, language tags and numeric errors only. Synthesis uses three fixed public test phrases, reports initialization/first synthesized audio/total synthesis/real-time factor, and deletes temporary WAV files afterward. First synthesized audio is not the same as first audible assistant response latency. Results are stored in app cache as metadata; the speech engine preference is device-local and is not included in settings export.

## Kokoro evaluation boundary

Kokoro can be tested as a separate Android TTS engine through platform IPC, with no native Kokoro runtime or phonemizer embedded in Kai. The official prototype asset and its integrity/source pin are recorded in the implementation handoff. That prototype is not enabled for private chat replies: its published service source logs speech input text, and interruption behavior/performance have not been accepted. A fixed-phrase benchmark is available only when the separate engine is installed. The disabled Kokoro reply choice and runtime gate also prevent selecting this engine through the system-default route.

Before enabling Kokoro for chat, use an audited engine build without text logging, verify all runtime/phonemizer/model/voice licenses, configure and compare the actual British male voices, benchmark the phone, and test Stop. The model's voice table lists George, but this is not evidence that the voice is configured or working on the device. System TTS remains the first-slice fallback.

## Verification and limits

Automated session tests cover exactly-once final delivery, stale callbacks, cancellation, provider/conversation mismatch, invalid transcripts and device-local engine settings. Existing chat/agent/photo/memory/reminder/search regressions also run. Native recognizer permissions, installed languages, microphone input, actual audio output, Bluetooth/phone-call interruptions and Kokoro performance require device tests. A ten-turn natural conversation, streaming, automatic follow-ups, acoustic barge-in and wake word are not claimed by this checkpoint.

## Key Files

| File | Purpose |
|---|---|
| voice/VoiceSession.kt in common source | Generation/context gate and bounded final transcript validation |
| voice/VoiceControls.kt in common source | Android-only UI entry points with other-platform no-op implementations |
| voice/VoiceInput.android.kt in Android source | Permission, on-device recognition, language checks, focus, deadlines and cleanup |
| voice/VoiceSettings.android.kt in Android source | Local engine controls, metadata-only diagnostics and fixed-phrase benchmarks |
| ui/chat/composables/QuestionInput.kt in common source | Reviewable transcript insertion and draft protection |
| ui/chat/ChatViewModel.kt, ChatActions.kt and ChatUiState.kt in common source | Voice context invalidation and speech interruption request |
| ui/chat/ChatScreen.kt in common source | Immediate TTS stop before recording and reply availability |
| ui/settings/AgentSettings.kt in common source | Local voice panel |
| data/AppSettings.kt in common source | Device-local engine preference |
| androidApp/src/main/kotlin/com/inspiredandroid/kai/MainActivity.kt | Selected engine initialization and offline voice filtering |
| androidApp/src/main/AndroidManifest.xml | Foreground microphone permission and service visibility |
