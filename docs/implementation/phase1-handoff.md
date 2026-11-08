# Kai 9000 Phase 1 handoff

Owner approved the repository audit and concrete Phase 1 plan on 2026-11-08. Work extends Kai Hybrid from v3.3.0-hybrid.7 on feature/kai9000-safe-agent-foundation; it does not replace the application or change its package/signing identity.

The feature contract is in [agent execution](../features/agent-execution.md), with checkpoint validation in [hybrid.8](../validation/3.3.0-hybrid.8.json). Read HYBRID.md before future work. No upstream runtime or model-pin upgrades are part of this change.

Phase 1 uses a separate SQLDelight journal, strict finite phone tools, host-side authorization, one execution coordinator, bounded calls, cancellation, review infrastructure, privacy/source consent and diagnostic history. Direct commands and structured calls share the new execution path; the existing permissive multi-provider executor is intentionally preserved outside opt-in safe mode rather than claimed to be universally migrated. Safe mode pauses legacy background AI work and preserves direct Android reminder delivery.

Review infrastructure is tested with fake operations. Sensitive actions remain unsupported in this mode. Android’s permission gates and current-user scope are enforced before writes. No root, Shizuku, AccessibilityService, microphone permission, speech model or screen automation was added.

The next proposed phase is a personal-build YouTube accessibility prototype: inspect semantic nodes, act visibly on an unlocked screen, observe/verify, bound retries/steps/time, and provide persistent Stop. General AI accessibility automation has Google Play policy constraints. A concrete Phase 2 plan and device setup should be reviewed before implementing it; do not pretend the service exists yet.

Voice follows separately: local push-to-talk STT and measured Kokoro feasibility, then streaming, interruption and follow-ups, with wake word last. Existing Samsung/system TTS is not proof of an offline conversational voice pipeline. Verify all runtime/phonemizer/voice licenses and device benchmarks before packaging.

Structured observation memory, importance scoring, notification budgets, daily briefing, Room-backed event/memory storage and proactive actions remain later phases. Current stored notes/conversations are preserved; notification privacy filtering is not automatic long-term memory extraction.
