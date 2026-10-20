# Photo input

**Last verified:** 2026-10-20

Kai Hybrid adds local photo analysis for the verified Gemma 4 E2B IT bundle on Android. The chat composer shows an attachment button for JPEG, PNG, and WebP photos when this model is selected. The existing user-message thumbnail and full-screen image viewer are reused.

## Use

Tap the paperclip, choose a photo, and type a question. Sending a photo without text asks for a description. A filename chip lets the user remove the queued photo. One photo may be queued for local analysis; choosing another replaces it. The composer says that the photo stays on the phone.

E2B receives actual image bytes through its vision encoder. A photo answer is labeled On-device photo analysis. Follow-up questions reuse the most recent photo in that chat. Earlier photos remain visible in the chat, but only the latest photo is passed to local inference to bound image memory and context use. Starting a new chat clears that context.

Photo conversations stay local: automatic DeepSeek consultation, automatic Brave routing, and native action tools are withheld while a photo is in local context. `/search` and `/deepseek` remain explicit text-only commands; a new photo attached to either command produces guidance rather than silently dropping or uploading the image.

## Cloud choice

DeepSeek's documented `deepseek-flash` vision alias and its legacy vision-exp alias now accept image parts in the existing remote-provider path. Selecting DeepSeek as the chat provider permits cloud photo analysis; the composer explicitly says the photo will be sent to that provider when submitted. This is separate from the bounded text-only DeepSeek consultation helper. Local photo errors do not automatically upload photos to the cloud.

The model support change follows the official [DeepSeek Vision documentation](https://api-docs.deepseek.com/guides/vision/). Other DeepSeek text families remain gated as before.

## Bounds and errors

Raw images are limited to 50 MB. On Android, decoding is sampled before loading large camera images; EXIF orientation is applied and the result is scaled to at most 1,024 pixels on its longer side, then re-encoded as JPEG. Local compressed images are limited to 2 MB. Re-encoding does not copy source EXIF metadata into the processed image.

The runtime checks the model bundle's actual vision capability before sending pixels. Unsupported models or runtimes produce clear guidance. Malformed or oversized stored image data is rejected before native inference. The current Android integration enables the GPU vision encoder and permits one image per conversation request. Other platform paths retain text-only local input.

## Validation

Automated checks cover latest-photo retention, image follow-ups, rejection of malformed and oversized data, unchanged text messages, and vision-model image payloads. The Android build, formatting check, and 101 selected tests passed. S26 Ultra hardware validation used a synthetic text-and-shapes picture rather than personal photos: an image-only send recognized “TEST CARD” and “42”, described three red circles and a blue square, and a follow-up correctly answered “Blue” without reattachment. Native logs confirmed vision support and one image in each local request. The initial description also had an inconsistent shape-count phrase, so visual answers remain fallible. Cloud image serialization passed automated checks; the optional DeepSeek photo path has not been live-tested.

## Key Files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/LocalPhotoInput.kt` | Bounded latest-photo history mapping |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/RemoteDataRepository.kt` | Local photo routing, privacy boundary and picker eligibility |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/inference/LocalInferenceEngine.kt` | Image-bearing inference message contract |
| `composeApp/src/jvmShared/kotlin/com/inspiredandroid/kai/inference/LiteRTInferenceEngine.kt` | Native vision backend and image content |
| `composeApp/src/androidMain/kotlin/com/inspiredandroid/kai/Platform.android.kt` | Sampled image decoding, rotation and resizing |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/chat/composables/QuestionInput.kt` | Attachment button, image-only send and provider notice |
| `composeApp/src/commonTest/kotlin/com/inspiredandroid/kai/data/LocalPhotoInputTest.kt` | Local photo history and bounds checks |
| `composeApp/src/commonTest/kotlin/com/inspiredandroid/kai/data/PhotoModelCapabilitiesTest.kt` | Remote vision model and wire-format checks |
