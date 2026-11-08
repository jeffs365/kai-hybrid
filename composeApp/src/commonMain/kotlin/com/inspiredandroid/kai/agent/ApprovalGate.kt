package com.inspiredandroid.kai.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

data class AgentApproval(val id: String, val runId: String, val label: String, val expiresAt: Long)

/** Host-created approvals only. No model-supplied label or approval token is accepted. */
class ApprovalGate(private val store: AgentExecutionStore, private val now: () -> Long = ::agentNow) {
    private val lane = Mutex()
    private val _pending = MutableStateFlow<AgentApproval?>(null)
    val pending: StateFlow<AgentApproval?> = _pending
    private var decision: CompletableDeferred<Boolean>? = null
    suspend fun await(runId: String, hostLabel: String, timeoutMs: Long = 60_000): Boolean {
        if (!lane.tryLock()) return false
        val approval = AgentApproval(agentId(), runId, hostLabel, now() + timeoutMs)
        val deferred = CompletableDeferred<Boolean>()
        var outcome = ExecutionStatus.CANCELLED
        try {
            decision = deferred
            store.put(ExecutionEntry(approval.id, runId, "approval", "approval", "HOST", ExecutionStatus.WAITING_APPROVAL, now()))
            _pending.value = approval
            val answer = withTimeoutOrNull(timeoutMs) { deferred.await() }
            val accepted = answer == true && now() < approval.expiresAt
            outcome = if (answer == null || now() >= approval.expiresAt) {
                ExecutionStatus.EXPIRED
            } else if (accepted) {
                ExecutionStatus.SUCCEEDED
            } else {
                ExecutionStatus.DENIED
            }
            return accepted
        } finally {
            _pending.value = null
            decision = null
            deferred.cancel()
            store.find(approval.id)?.let { entry ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { store.put(entry.copy(status = outcome, updatedAt = now())) }
            }
            lane.unlock()
        }
    }
    fun respond(id: String, allow: Boolean) {
        val pending = _pending.value ?: return
        if (pending.id != id) return
        decision?.complete(allow && now() < pending.expiresAt)
    }
    fun cancel() {
        decision?.cancel()
    }
}
