# Local voice input and reply output

**Last verified:** 2026-11-08

Hybrid.9 adds a foreground Android speech button to regular chat; hybrid.10 adds a separate offline Kokoro reply engine. The owner confirmed the first dictation/spoken reply flow works. Source/build verification and device verification are separate; the release validation record identifies what actually passed. YouTube/accessibility work was deferred by the owner.

## Speak, review, send

Start with an empty composer and no attached files. Tap Speak offline. On first use Android asks for microphone permission; granting it does not automatically start recording. Tap Speak offline again when ready. Speak normally, allow the recognizer to finish, or tap Finish speech. The final transcript appears in the composer for correction. Tap Send to use the current chat provider and its existing tool boundaries.

The microphone is used only in the foreground after an explicit tap and permission. There is no background microphone service, wake word or automatic follow-up listening. Audio is not saved by Kai or placed in API requests. Only a reviewed transcript submitted through Send enters the existing chat/history/provider flow. DeepSeek receives that text when selected; Local Model handles it locally subject to existing explicit consultation/search rules.

Voice input uses Android's specifically on-device recognizer, rather than relying on an offline-preference hint in a generic recognizer. No network-recognition fallback is made. Android must report availability, and supported versions check the selected language before listening. English US, English Philippines and Filipino can be selected; installed-language support and actual English/Taglish accuracy must be measured on the phone. A locale the recognizer does not support is rejected explicitly, without suggesting a download or substituting another region. A language download button asks the on-device recognizer to obtain missing language data. This download may need a network connection but does not send an utterance.

Recognition preparation/finish waits are bounded to eight seconds, with a 30-second listening limit and a 46-second absolute session deadline. Cancel speech discards the result. Leaving/resuming the app, changing provider or conversation, submitting a draft, editing the composer, or losing audio focus stops active recognition. A cancelled, completed or stale session cannot deliver another transcript. Partial recognition never submits a request or runs a tool.

## Spoken replies

Starting the microphone stops current TTS playback and enables reply speech for that chat UI session. The current turn still requires Send after transcript review. Existing speech controls can mute/read messages. The Android speech backend selects an engine-reported offline English voice from the chosen system engine. It does not silently choose a network voice or Google fallback when no local voice is available. An unavailable reply voice is indicated by the composer; dictation may still be usable.

Settings → Agent → Local voice offers recognition diagnostics and a local synthesis benchmark. These diagnostics do not record microphone audio. Recognition diagnostics contain availability, language tags and numeric errors only. Synthesis uses three fixed public test phrases, reports initialization/first synthesized audio/total synthesis/real-time factor, and deletes temporary WAV files afterward. First synthesized audio is not the same as first audible assistant response latency. Results are stored in app cache as metadata; the speech engine preference is device-local and is not included in settings export.

## Offline Kokoro replies

Settings → Agent → Local voice offers Use Kokoro when the matching personal engine is installed. George is the default British English voice; Daniel and Lewis can be selected for comparison. Speaking speed is configurable from 80% to 120%, initially 95%. These preferences stay on this device. Use system voice returns to the offline system voice without changing Android’s global TTS default.

Kokoro runs in a separate optional Android application with no internet or microphone permission. Kai checks the engine’s signing certificate, compatible version, protocol, permissions and exact offline voice names. An unavailable or untrusted engine cannot silently fall back to a different voice. The published upstream prototype remains blocked because its service logs speech input. The personal engine removes Java text logging, compiles out native Android speech logs, discards native stdout/stderr and sanitizes native exceptions. Its diagnostics contain only fixed status/stage/voice values, timings, PCM byte count and segment count.

Voice weights and phonemizer data occupy about 361 MiB in the engine’s private storage. All 360 files are checked against pinned SHA-256 values before use. The current personal installation uses local setup scripts; there is no in-app model downloader. Model weights and native binaries are excluded from Git. The main app remains Apache-2.0 and communicates through Android TTS; the separate engine includes a GPL phonemizer and carries its own GPL license and dependency notices.

Synthesis is serialized and bounded to 4,000 characters, 32 segments, 220 characters per segment and a 90-second deadline. Stop invalidates the request and checks every outgoing audio buffer. An in-flight native inference can finish before it observes cancellation; its audio is discarded. This does not implement acoustic barge-in. Fixed public samples of all three voices, a three-phrase George benchmark and Stop were tested on the S26 Ultra; exact timing and memory measurements are recorded in the hybrid.10 validation record. These timings measure synthesis, not full assistant response latency.

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
| data/AppSettings.kt in common source | Device-local engine, voice and speed preferences |
| voice/KokoroProfiles.kt in common source | Finite British English presets |
| voice/KokoroEngineTrust.kt in Android source | Matching-signature engine checks |
| kokoroEngine/ | Separate GPL offline speech app, model manifest, native build patch and setup scripts |
| androidApp/src/main/kotlin/com/inspiredandroid/kai/MainActivity.kt | Selected engine initialization and offline voice filtering |
| androidApp/src/main/AndroidManifest.xml | Foreground microphone permission and service visibility |
