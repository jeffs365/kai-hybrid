# Brave Web Search

**Last verified:** 2026-10-20

Kai Hybrid adds Brave Search for visible chat with a local model. Settings → Tools → Web Search · Brave contains a masked API-key field, saved automatically in the phone's existing encrypted settings. A separate Search automatically switch starts off. The existing Web Search tool switch must also be enabled.

## Behavior

Starting a message with `/search` performs a manual lookup even when automatic search is off. With automatic search enabled, a short local routing pass can request a search for current facts, news, prices, weather, or explicit lookup requests. Decisions must contain a valid route and a bounded search query; malformed decisions stay local. Routing is fallible. It does not turn on native action tools in bundles that lack them.

Only the query goes to Brave. Chat history, memories, attachments, and the local system prompt are not automatically sent. The key is supplied by the app as an authentication header, never as a model argument. E2B summarizes up to five returned excerpts locally. The answer shows a Web search · Brave label and source links appended by the app. Full pages are not fetched; the summary must acknowledge when excerpts do not establish an answer. Search content is treated as untrusted data. Search summaries do not automatically initiate a DeepSeek call.

The summary receives the current UTC date and the bounded source URLs alongside titles and excerpts. It is instructed to compare dates across all results, prefer official sources, and distinguish the newest item in these results from a verified latest item. Search ranking does not establish recency; short excerpts can still be incomplete or stale.

## Limits and failures

One search is allowed per user turn, including concurrent invocations of the same turn's search. Queries are limited to 600 characters and 75 words. Up to five unique HTTP/HTTPS source results are retained, with bounded titles and excerpts. The network request has a 15-second timeout. There is no application retry or alternate search-provider fallback. Searches are not exposed to background jobs.

Missing keys, disabled Web Search, rate limits, rejected credentials, malformed provider responses, and network failures produce clear guidance without fabricated evidence. Provider error bodies and exception details are not shown. Stop propagates cancellation. If the local summary fails after a successful search, the source links are still available.

The original DuckDuckGo tool remains available to other tool-capable chat paths. Brave's manual command and automatic routing are separate app-managed paths for this personal build. A Brave key is not included in settings exports and must be entered again after a settings import on another device.

## Validation

Automated checks cover disabled or missing configuration, query limits, one-attempt concurrency, result limits, unsafe links, error redaction, timeout, cancellation, and strict routing. Device checks cover the settings field, missing-key flow, live manual lookup, and live automatic lookup using the user's key entered directly on the phone.

The S26 Ultra checks passed: the masked field is visible, `/search` shows setup guidance without a key, a current-information request selects the search route, and ordinary arithmetic remains local with automatic search enabled. Live manual search returned five sources and a local summary. A live automatic lookup exposed a date-selection error, corrected in hybrid.3 by supplying current time and source URLs with stronger date/source guidance. Repeating the same question selected the newer official result. The hybrid.3 build and 95 selected automated tests passed. These checks establish retrieval and routing, not correctness of every future summary.

The integration uses Brave's [Web Search API](https://api-dashboard.search.brave.com/api-reference/web/search/get), with a fixed HTTPS endpoint, authentication header, and a result count of five. Redirects are disabled for the credential-bearing request.

## Key Files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/tools/BraveSearchTool.kt` | Bounded retrieval, credential handling, safe sources and error handling |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/tools/LocalAssistantRouting.kt` | Strict local/search/DeepSeek decisions |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/RemoteDataRepository.kt` | Manual command, automatic routing and local summaries |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/settings/BraveSearchSettings.kt` | Masked key entry and automatic-search control |
| `composeApp/src/commonTest/kotlin/com/inspiredandroid/kai/tools/BraveSearchToolTest.kt` | Search, routing, privacy boundary and failure checks |
