# Local voice and Kokoro handoff

The owner deferred YouTube/accessibility and moved to voice on 2026-11-08. The owner confirmed the initial push-to-talk → review → Send → spoken reply flow works, then explicitly requested Kokoro. Current work is on `feature/kokoro-offline-voice`, from `feature/local-voice-mvp` at 5a90451. Hybrid.10 includes the unsupported-language guard from the prior source checkpoint. Keep the existing hybrid package and signing key. GitHub Actions remain disabled.

Kokoro is now a separate personal engine (`com.inspiredandroid.kai.kokoro`) built from the pinned sherpa source, not the unsafe publisher service. Its native Android logs are compiled out, its own process discards native stdout/stderr, and JNI exceptions are sanitized. The JNI callback invokes the erased Function1 method for Kotlin 2.4 compatibility. British phonemization uses voice identifier `en` (file gmw/en), rather than the unselectable language tag en-gb. George maps to speaker26, Daniel24, Lewis27. Main Kai remains Apache-2.0; the separate engine is GPL-3.0-or-later and communicates through Android TTS IPC. It has no requested permissions.

All 360 model/data files (377,679,880 bytes) were installed into the engine’s private files directory and SHA-256 verified. Keep models, runtime binaries, app data and signing keys out of Git. Exact inputs, checked-in safety patch, build instructions and notices are in `kokoroEngine/`. Never install/enable the publisher’s original package for private chat: it logs raw input and remains blocked, including as global system engine. Android’s actual global default remains com.samsung.SMT.

Tests use only fixed public phrases and metadata. Read only `files/last-synthesis.json` in the engine and `cache/voice-kokoro-benchmark.json`, `voice-tts-benchmark.json`, `voice-recognition-diagnostics.json` in Kai. Do not extract preferences, messages, API keys or memories. The benchmark deletes temporary WAV files. No automation records from the microphone or grants its permission; the owner did the real microphone trial.

Runtime tests confirmed all three preset voices synthesize, George benchmarks complete, and Stop cancels both before first audio and during playback. Stopping native inference can take until the current inference returns, but cancelled PCM is discarded. Engine RSS after synthesis was about 995 MiB (peak about1228MiB) in the initial measurement. Full metrics and installed hashes are in `docs/validation/3.3.0-hybrid.10.json`. These figures are synthesis metrics, not end-to-end audible assistant latency.

The engine setup is currently a developer-assisted USB installation; there is no in-app model download. Use the existing `install_models.py` script for resumable 8MiB transfers and verify in the setup UI. It preserves unrelated phone data. Small APK/model-piece transfers succeeded where monolithic transfers previously dropped USB. Update both packages with adb install -r; do not uninstall Kai to update it.

Remaining Phase3 work: an owner-run ten-turn natural English/Taglish trial, pronunciation evaluation, Bluetooth/call/focus interruption tests, streaming replies, follow-ups, acoustic echo/barge-in and wake word. None is claimed by hybrid.10. en-US recognition is installed; en-PH and fil-PH are unsupported on this recognizer and are explicitly rejected without cloud fallback.

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


Current personal engine build and setup: [kokoroEngine/README.md](../../kokoroEngine/README.md). Device validation: [hybrid.10](../validation/3.3.0-hybrid.10.json). No upstream PR or public binary release is authorized or created.
