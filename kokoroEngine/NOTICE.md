# Optional Kai Kokoro engine

This separate Android application is GPL-3.0-or-later because its native phonemizer includes eSpeak-NG. Kai's main application remains under its existing Apache-2.0 license and communicates with this engine using Android's TTS service API. No speech native library is linked into the main app.

Sherpa-onnx Kotlin JNI bindings and native source: copyright Xiaomi Corporation, Apache-2.0; pinned commit in native-lock.json. ONNX Runtime: Microsoft, MIT. Kokoro model/voices: hexgrad, Apache-2.0. eSpeak-NG: its contributors, GPL-3.0-or-later. Piper phonemize integration is built from the pinned sources selected by sherpa-onnx. The reproducible native script records its inputs and disables native speech logging; stdout/stderr are discarded only in this engine process.

Runtime binaries and model files are not committed or published as a release asset. Before distributing binaries, preserve all notices and provide corresponding source for the GPL components, including exact upstream commits and the privacy patch/build instructions. This personal build installs only on the owner's phone. See README.md for build and data integrity instructions.
