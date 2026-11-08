# Kai Kokoro offline voice

Optional, separate Android TTS application for Kai Hybrid. Package `com.inspiredandroid.kai.kokoro`; arm64-v8a, Android 8+ build target, tested on the owner's Android 17 S26 Ultra. Kai uses Android TTS IPC, with no Kokoro or eSpeak library linked into its main Apache-2.0 app. This engine is GPL-3.0-or-later; see LICENSE, NOTICE.md and licenses/.

English presets: George (default, speaker 26), Daniel (24), Lewis (27), with speed 80–120% in Kai Settings → Agent → Local voice. The pinned eSpeak British voice identifier is `en`, backed by `lang/gmw/en`; `en-gb` is a language tag that this phonemizer cannot select by name. George's speaker embedding supplies his voice identity.

The engine requests no permissions, including INTERNET or microphone. It emits no speech text, audio files, utterance hashes or raw exception details. Android native log macros are compiled out, and the engine process discards native stdout/stderr. `files/last-synthesis.json` contains fixed status/stage/voice names, timings, segment counts and PCM byte count only. JNI exceptions are sanitized, and callback calls use the erased Function1 signature for Kotlin 2.4 compatibility. The published upstream engine APK is **not installed**: its service logs raw text and is blocked for Kai chat.

Synthesis is serialized, limited to 4,000 UTF-16 characters, 32 segments, 220 characters per segment, and a 90-second request deadline. Stop invalidates generation and every outgoing PCM buffer. An in-flight ONNX inference cannot be preempted; a cancelled result is discarded. This is local TTS, not wake word, streaming chat or acoustic barge-in. Android's global TTS selection is not changed.

## Rebuild

Use JDK 21, SDK 37, CMake/Ninja 3.22.1 and NDK 26.1.10909125. `native-lock.json` pins sherpa-onnx and publisher asset hashes. Upstream's pinned CMake files pin its static dependencies. Download the asset from [the publisher's pinned repository](https://huggingface.co/csukuangfj/sherpa-onnx-apk/tree/281f8af18968f9ca9390cb1137b205cdb5c1349b/tts-engine-new/1.12.22), and keep it in a local cache outside Git. Its exact filename and SHA-256 are in the lock. This build takes only model data and ONNX Runtime from that verified asset; sherpa JNI is rebuilt from source.

```sh
python3 kokoroEngine/tools/prepare_models.py --apk /path/to/cache/sherpa-onnx-1.12.22-arm64-v8a-zh_en-tts-engine-kokoro-multi-lang-v1_0.apk
python3 kokoroEngine/tools/build_native.py --cache /path/to/cache --sdk "$ANDROID_HOME"
./gradlew spotlessApply
# Formatting and builds must be separate invocations.
./gradlew :kokoroEngine:testDebugUnitTest :kokoroEngine:assembleDebug :androidApp:assembleFossDebug
adb -s SERIAL install -r kokoroEngine/build/outputs/apk/debug/kokoroEngine-debug.apk
python3 kokoroEngine/tools/install_models.py --adb "$ANDROID_HOME/platform-tools/adb" --serial SERIAL
```

Use the existing private Kai signing key for both packages. Do not commit the key, native binaries, model weights or private phone data. The resumable installer transfers the weights in 8 MiB checksum-verified pieces into the engine's private storage and removes its shell staging files after success. Then open Kai Kokoro and tap **Verify offline voice files**; all 360 files (377,679,880 bytes) must match their manifest before voices are offered. App updates preserve these files. Model data occupies approximately 361 MiB, separate from the ~10 MiB engine APK. Failed verification leaves the engine unavailable; there is no network fallback.

Kai accepts this engine only with matching app signatures, protocol 1, compatible version, no INTERNET/microphone permission, and one of the exact offline voice names. Select **Use Kokoro** in Kai. **Use system voice** restores the existing offline system path. Runtime binaries and weights stay local and ignored. Any future binary redistribution requires the full corresponding source/build materials and notices for GPL components; this repository currently publishes source only.

## Device evidence

See `../docs/validation/3.3.0-hybrid.10.json` for final installed hashes, tests, memory, fixed-phrase benchmarks and Stop measurements. Synthesis timing differs from end-to-end audible assistant latency. Speaker quality and accent are the owner's listening decision. Bluetooth/call interruptions, acoustic echo, multilingual pronunciation and ten successive natural turns remain outside this checkpoint.
