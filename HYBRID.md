# Kai Hybrid

Personal Android fork based on upstream Kai v3.3.0 (78bbb326). Apache-2.0 licensing and upstream notices remain in place. This is not an official Kai release.

## Saved baseline and future changes

Repository: [jeffs365/kai-hybrid](https://github.com/jeffs365/kai-hybrid). Development branch: `feature/local-deepseek-consultation`. The installed baseline is **3.3.0-hybrid.5**, tagged `v3.3.0-hybrid.5`. The earlier photo-enabled checkpoint remains tagged `v3.3.0-hybrid.4`.

Continue work from that branch. Read this file and the linked feature docs, make the change, run the relevant checks, then increment the Android hybrid version suffix and install the APK with `adb install -r`. Keep the same application ID and signing key to update the app while preserving its data. The upstream repository is available as the `upstream` remote for deliberate future merges.

This repository backs up source code, feature notes, and the [validation record](docs/validation/3.3.0-hybrid.5.json). API keys, chat history, memory, and downloaded model weights remain in the app's storage on the phone; they are not backed up here. Built APKs are kept locally in `dist/` and ignored by Git.

## Build

Use JDK 21 and Android SDK 37. Run `./gradlew :androidApp:assembleFossDebug`. The test package is `com.inspiredandroid.kai.hybrid`, labeled Kai Hybrid. The app has its own storage, so select/download E2B inside the test app. No memory or credentials are automatically copied from official Kai. The original Kai was removed from the current phone after the hybrid was validated.

The installed build uses this workstation's Android debug signing key at `~/.android/debug.keystore`. Keep that key private and retain it for future updates. A new workstation needs the same key to update this installation without uninstalling it. Signing keys and credentials are excluded from Git.

Run `./gradlew spotlessCheck` separately from builds/tests, because the upstream formatter also scans generated Kotlin files. The current baseline passed these targeted tests:

```sh
./gradlew :composeApp:desktopTest \
  --tests '*AppSettingsExportImportTest' \
  --tests '*AppSettingsTest' \
  --tests '*LocalPhotoInputTest' \
  --tests '*PhotoModelCapabilitiesTest' \
  --tests '*LocalMemoryCommandsTest' \
  --tests '*ChatSystemPromptBuilderTest' \
  --tests '*ToolExecutorTest' \
  --tests '*LocalModelCatalogTest' \
  --tests '*BraveSearchToolTest' \
  --tests '*DeepSeekConsultationToolTest' \
  --tests '*ToolDefinitionsTest'
```

The version generator also rewrites the iOS configuration during Android builds. Review that generated change before committing an Android-only update.

## Use

In Services settings add Local Model, download Gemma 4 E2B IT, and start with a 4K context. The current phone configuration was also validated at 12,288 tokens. Add DeepSeek, entering the API key directly on the phone. Enable Allow DeepSeek consultation, then keep Local Model selected in chat. A restart may be needed to refresh the chat provider list after downloading a model.

Ask ordinary questions locally. E2B can request DeepSeek advice automatically; bundles without native tools use a short local routing pass and send only the current message. Routing is fallible. Use `/deepseek your question` for an explicit bounded consultation. The expert uses the selected DeepSeek model, or deepseek-flash if no model is selected, with thinking disabled and a 2,048-token cap. Its output is advice only.

See [feature behavior](docs/features/deepseek-consultation.md).

### Brave Search (hybrid.3)

In Settings → Tools → Web Search · Brave, paste your Brave Search API key into the masked field. It saves automatically. Use `/search your query` for a manual lookup, and turn on Search automatically for current-information requests. Keep the Web Search tool enabled. Brave receives only the query; E2B summarizes excerpts locally and the app appends source links. One search is allowed per turn, with no application retry. See [Brave search behavior](docs/features/brave-search.md).

### Photos (hybrid.4)

With Local Model / Gemma 4 E2B IT selected on Android, tap the paperclip to attach a JPEG, PNG, or WebP photo. Ask a question, or send the photo alone for a description. E2B analyzes it on the phone and retains the latest photo for follow-up questions. Automatic web/cloud routing is disabled in photo conversations. Select a cloud vision provider explicitly to upload a photo there; the composer shows an upload notice. See [photo input behavior](docs/features/photo-input.md).

### Persistent memory (hybrid.5)

Keep Settings → Agent → Memories on. Say `Remember that I prefer concise answers`, or use `/remember reply-style = concise answers` to choose a key. Ask `what's in your memory` or use `/memories` to read the actual saved notes. Use `/forget reply-style` to delete a note. These app-managed actions work locally even though the E2B bundle cannot call native memory tools. Saved notes persist across chats and restarts; ordinary conversation does not automatically save every fact. See [memory behavior](docs/features/memories.md).

## Device validation

Hybrid.5 passed the Android build, formatting check, and 152 selected automated tests. On the S26 Ultra, an explicit natural-language request saved a synthetic memory. After an app restart and a new chat, E2B recalled the test fact correctly using its saved notes. The actual-memory list also showed the note after restart. The temporary note was then deleted. These checks made no cloud calls. The Memories switch remains on.

Hybrid.4 passed the Android build, formatting check, and 101 selected automated tests. An image-only send on the S26 Ultra recognized text and colored shapes in a synthetic picture; a follow-up identified the blue square without reattachment. The local vision path was tested on the phone. Cloud photo payloads were checked in automated tests, without a live DeepSeek photo request.

Verified on the S26 Ultra on 2026-10-20: E2B answered ordinary arithmetic locally after cloud configuration. In hybrid.1, an explicit `/deepseek` request and an automatically routed complex coding question both received live DeepSeek responses with the cloud label. The configured cloud model is deepseek-flash. Live manual and automatic Brave search returned local summaries with source links. Hybrid.3 improves date selection using current UTC time, source URLs and official-source guidance; the repeated freshness check selected the newer official result. Detailed historical results are in [the hybrid.4 validation record](docs/validation/3.3.0-hybrid.4.json).
