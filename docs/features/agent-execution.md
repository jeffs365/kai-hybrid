# Agent execution

**Last verified:** 2026-11-08

Kai Hybrid on Android has an opt-in safe agent mode for DeepSeek in Settings → Agent. It supports only battery, phone status, and flashlight. It does not add screen control, microphone access, wake word, autonomous messaging, purchases, deletion or proactive memory extraction.

## Use and scope

Select DeepSeek and enable Safe agent · DeepSeek. Ask a normal text question such as “Read the battery charge from Android using a tool” or “Turn on the flashlight”. The request may use DeepSeek, and its tool result is labeled accordingly. The exact immediate on/off command must be recognized by the app before a write is authorized. Unrecognized or ambiguous write requests are denied. Turn off the flashlight explicitly afterward.

Slash commands /battery, /phone status and /flashlight on, off or status always use Android directly. They remain available without a network connection. Existing E2B advice consultation and photo boundaries are preserved. A conversation containing attachments uses the selected provider’s ordinary photo/file behavior without new agent action tools.

The application default is off. The mode applies to DeepSeek; other providers retain their legacy tool sets. While the mode is enabled on Android, legacy background AI jobs pause and stay pending. Direct Android reminders still deliver without model calls. Turning it off restores the legacy scheduler, subject to its permissions and operating-system limits. Legacy tools outside this mode have not all been migrated to this execution policy.

## Authorization and execution

The app validates the finite argument types and allowed names. Model text, web/UI content, old history and background events cannot grant permissions. A write needs a current foreground user request naming the exact target, the enabled tool, Android camera permission, and Kai visible on an unlocked screen. Generated interactive UI submissions cannot authorize phone actions.

Tasks run through one coordinator with at most eight reasoning rounds, eight calls per batch, and a 90-second deadline. Each native operation has a 30-second timeout; each cloud request has a 30-second timeout and a 2,048-token output cap, with no application retry or provider fallback. Requests use the most recent 24 history messages within a 16,384-token estimated context budget. Three repeated batches stop the loop.

Repeated provider call IDs with the same typed action reuse the prior result in that run. Reuse with a different action is rejected. Provider IDs, raw arguments and observations remain transient; persistent history contains host-generated identifiers and fixed metadata. After process death, unfinished records become interrupted; they are never automatically replayed. A crash between a hardware action and its completion record can leave the final state unknown. This is not a universal exactly-once guarantee.

The app supplies a finite classification of the current direct request to the model, without inserting raw user text into that instruction. Earlier messages cannot substitute for a fresh measurement. If a recognized phone request produces no tool call, the task is recorded as failed and the app reports that no operation or current state was verified. It does not retry automatically or execute a fallback action.

Android callbacks confirm flashlight state and results carry observation timestamps. An unavailable camera, missing permission, cancellation, timeout or rejected authorization cannot become a success result. Another app or Android can subsequently change the light; a snapshot does not continuously monitor it.

## Stop and review

The chat shows an active task and Stop. Stop cancels the request, tool work and pending review/permission waits, clears indicators and requests existing TTS playback to stop. It cannot reverse an operation already completed. Permission waits reset on cancellation.

A host-owned approval interface is connected to chat for future reviewed actions. One-use, expiry, denial and cancellation are tested with fake operations. This release does not expose sensitive actions or offer a generic approval that unlocks arbitrary tools.

## History and privacy

Settings → Agent shows the latest 20 records, with action names, local times, outcome, duration and diagnostic reason. Completed entries can be deleted individually or cleared together. Active records are protected. History is bounded to 500 terminal entries with configurable retention of 1–30 days, default seven.

The journal is a separate private SQLite database and is excluded from cloud backup and device transfer, including journal sidecars. It contains no messages, API keys, screenshots, raw tool arguments, provider call IDs or observation bodies. Existing conversations and saved notes are preserved in their existing stores.

Notification/SMS capture does not imply consent to cloud processing. New local records are filtered for known sensitive patterns; cloud tools and heartbeat recheck stored content. AI sharing is off by default and needs an app/sender allowlist. Notification tools use temporary opaque references rather than exposing raw system notification keys. Mixed legacy heartbeat summaries are not replayed because their source consent cannot be reconstructed. Rules are conservative and can exclude legitimate content; they cannot recognize every secret. This controls future processing and does not erase prior conversations or undo earlier sharing.

## Verification scope

Automated coverage includes actual DeepSeek-format response fixtures, call/result pairing, duplicate IDs, strict argument checks, policy rejection, cancellation/time limits, permission-wait cleanup, approval tokens, privacy/deduplication, settings import/export, retention and journal recovery. Device validation is recorded with the release checkpoint. Force-stop, deep-Doze, extended background operation and speech routing remain later hardening work.

## Key files

| File | Purpose |
|---|---|
| agent/AgentRuntime.kt in common source | Shared coordination, execution policy, deadlines and observed results |
| agent/AgentToolRegistry.kt in common source | Finite schemas and strict argument validation |
| agent/ToolPolicy.kt in common source | Host-side authorization |
| agent/AgentPrompt.kt in common source | Current-turn guidance from deterministic host classification |
| agent/AgentModelClient.kt in common source | Structured response adapter that separates reasoning from user text |
| agent/ApprovalGate.kt in common source | Host-owned, expiring one-use review |
| agent/AgentExecutionStore.kt in common source | Durable metadata, retention and interrupted-state recovery |
| sqldelightAgent/com/inspiredandroid/kai/agentdb/agent_execution.sq | Independent journal schema |
| events/EventPrivacyPolicy.kt in common source | Local filtering and source/consent rules |
| ui/settings/AgentExecutionSettings.kt in common source | Feature/privacy controls and activity history |
| ui/chat/AgentStatusBanner.kt in common source | Active task, Stop and future review UI |
