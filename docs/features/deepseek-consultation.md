# DeepSeek consultation

**Last verified:** 2026-10-20

Kai Hybrid is a personal Android test build installed alongside official Kai. E2B remains the everyday local model. In Settings → Services, the user may enable DeepSeek consultation and configure a DeepSeek provider with their own API key. The switch starts off.

## Behavior

- In ordinary chat, E2B decides whether to ask DeepSeek for advice on a difficult question. This decision is fallible; it is not a guarantee that every weak answer will be escalated.
- When the downloaded bundle lacks a native tool interface, a separate short local routing pass chooses local or cloud. Only an exact, validated decision can trigger consultation; malformed output stays local. This adds local processing time. In this mode only the current user message is sent to DeepSeek, without history or memories, and its answer is shown directly with the cloud label.
- Starting a message with `/deepseek` requests cloud advice explicitly without asking E2B to make that decision. A missing key or disabled switch produces setup guidance without a cloud request.
- Only the consultation question and selected context leave the phone. No full-history or memory upload occurs automatically. The API key is retrieved by the app from its existing encrypted settings; it is never supplied to the model as an argument.
- Cloud-assisted replies are visibly labeled. A failed consultation is labeled as unavailable if E2B supplies a local answer instead.
- DeepSeek supplies text advice only. It receives no action tools and cannot delegate again. E2B retains only its normal enabled tools.
- With Brave automatic search enabled, current-information requests can take the search route first. Brave excerpts are summarized locally with source links; this path does not automatically call DeepSeek. The explicit `/deepseek` command retains its existing behavior. See [Brave Web Search](brave-search.md).

## Limits and failures

One API attempt is allowed per visible user turn, including concurrent requests from the model. Input is limited to 6,000 characters across the question and selected context. Cloud generation is capped at 2,048 tokens in non-thinking mode; at most 6,000 response characters are returned to E2B. Requests time out after 45 seconds. No automatic retry or alternate-provider fallback is made. Stop cancels the request cooperatively.

When the local router requests cloud advice without a configured key, setup guidance appears immediately. It does not make the small model attempt the difficult question while the key is missing.

With Local Model primary, automatic background checks stay local and cannot invoke consultation. Scheduled jobs with an explicitly chosen cloud model retain that explicit choice. These jobs are separate from chat consultation.

## Setup and validation

Choose E2B at 4K in the test app, configure a DeepSeek service, leave Local Model selected, and enable consultation. Try an ordinary local question, then `/deepseek` with a short non-sensitive question, and finally an instruction asking E2B to consult DeepSeek. Verify the cloud label and tool activity. A live provider test requires the user's own key and connectivity.

The S26 Ultra device check passed on 2026-10-20 with deepseek-flash: explicit cloud consultation returned the correct arithmetic answer; a complex coding question without a cloud command triggered automatic consultation and received a labeled response; ordinary arithmetic stayed local with consultation enabled. These checks validate routing and connectivity, not the correctness of every future answer.

## Key Files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/tools/DeepSeekConsultationTool.kt` | Per-turn limits, consultation schema, cancellation, failure handling |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/RemoteDataRepository.kt` | Local tool adapter, bounded cloud request, explicit routing, response labels |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/settings/DeepSeekConsultationSettings.kt` | Opt-in control and setup guidance |
| `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/network/Requests.kt` | Bounded request payload; HTTP content logging disabled |
| `composeApp/src/commonTest/kotlin/com/inspiredandroid/kai/tools/DeepSeekConsultationToolTest.kt` | Budget, privacy boundary, error, timeout, cancellation and wire-payload tests |
