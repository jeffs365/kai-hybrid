# Local voice MVP handoff

Owner deferred YouTube/UI automation and directed moving to the next roadmap phase, voice, on 2026-11-08. Work is on feature/local-voice-mvp from validated v3.3.0-hybrid.8. Proposed APK version is 3.3.0-hybrid.9; retain package and signing key. No accessibility/root/microphone foreground service or cloud speech provider is added.

First slice: foreground on-device recognition, microphone permission at first explicit use, finalized transcript review before Send, existing selected-provider chat and offline system TTS. Session generation guards reject late/duplicate callbacks. Leaving the activity or changing chat/provider/draft cancels recording. Voice processing has audio-focus and time bounds. The app does not auto-send a transcript or continuously listen for follow-ups.

The native setup panel can check recognition support without recording and synthesize three fixed public phrases for performance measurement. Cache files voice-recognition-diagnostics.json, voice-tts-benchmark.json and voice-kokoro-benchmark.json contain only diagnostic metadata. Read only these files when testing; do not extract preferences, messages or memories. Permission is granted by the owner at the actual Android prompt, not by adb pm grant. A real ten-turn English/Taglish and interruption trial is still required before declaring Phase 3 complete.

Kokoro prototype provenance:

```json
{
  "repository": "csukuangfj/sherpa-onnx-apk",
  "revision": "281f8af18968f9ca9390cb1137b205cdb5c1349b",
  "file": "tts-engine-new/1.12.22/sherpa-onnx-1.12.22-arm64-v8a-zh_en-tts-engine-kokoro-multi-lang-v1_0.apk",
  "bytes": 360582649,
  "sha256": "c3f9facf1f07ab62f237dd5ffdf9f7fbc6e1d92d991e6ecf2cfaf9c9083ff93c",
  "source": "https://k2-fsa.github.io/sherpa/onnx/tts/apk-engine.html"
}
```

Downloaded asset is verified against the publisher's LFS SHA256; apksigner verifies. Its manifest reports package com.k2fsa.sherpa.onnx.tts.engine, target SDK 34, arm64-v8a and no INTERNET or dangerous runtime permission. It uses a separate Android TTS service, so no GPL phonemizer/runtime is embedded in Kai. Do not redistribute the binary with Kai without reviewing all native/model/voice licenses and corresponding-source obligations.

Important verified limitation: published v1.12.22 service source includes raw speech text in Log.i calls. This is a real privacy finding, not a hypothetical one. Kokoro chat selection is disabled in UI and enforced at runtime, including when the engine is Android's global default. It may receive only the fixed public benchmark phrases in this prototype. Remove the task-installed prototype after evaluation unless replacing it with an audited no-text-log build; it was absent at the initial voice audit. Keep the phone's global TTS choice unchanged. Runtime source reference:
https://github.com/k2-fsa/sherpa-onnx/blob/v1.12.22/android/SherpaOnnxTtsEngine/app/src/main/java/com/k2fsa/sherpa/onnx/tts/engine/TtsService.kt

The SDK and model/runtime references were checked before implementation:
- https://developer.android.com/reference/android/speech/SpeechRecognizer
- https://developer.android.com/reference/android/speech/RecognitionSupport
- https://k2-fsa.github.io/sherpa/onnx/tts/apk-engine.html
- https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/kokoro.html (George maps to speaker 26 in the selected 53-speaker distribution.)

Current device blocker: USB went offline during the 360 MB prototype-engine install. Transfer reported EOF; installation status is unknown. Asked owner asynchronously to reconnect/toggle debugging. On reconnection first check pm path/version for the engine and Kai, then install/verify only what is missing. Do not clear data or grant microphone through ADB. Install hybrid.9, inspect own voice UI, run local diagnostic buttons, benchmark system TTS then the fixed-phrase prototype, and request the owner's explicit microphone/live speech test. Confirm no active RECORD_AUDIO operation before handoff. Keep safe-agent/cloud-event preferences unchanged from the prior validated build.

No hybrid.9 release tag until final installation and truthful device verification. Keep GitHub Actions disabled and push only the personal feature branch. Full voice streaming/follow-up/wake word and natural Kokoro chat are not complete yet.
