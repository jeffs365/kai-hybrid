@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.inspiredandroid.kai.agent

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class ApprovalGateTest {
    @Test fun approvalIsHostScopedOneUseAndWrongTokenCannotApprove() = runTest {
        val gate = ApprovalGate(AgentExecutionStore(), now = { agentNow() + testScheduler.currentTime })
        val answer = async { gate.await("run", "Controlled test action", 1000) }
        runCurrent()
        val id = gate.pending.value!!.id
        gate.respond("wrong", true)
        runCurrent()
        assertFalse(answer.isCompleted)
        gate.respond(id, true)
        gate.respond(id, false)
        assertTrue(answer.await())
        assertNull(gate.pending.value)
        val next = async { gate.await("run", "Second controlled action", 1000) }
        runCurrent()
        gate.respond(id, true)
        runCurrent()
        assertFalse(next.isCompleted)
        gate.respond(gate.pending.value!!.id, false)
        assertFalse(next.await())
    }

    @Test fun expiredApprovalNeverExecutesAndStopClearsPendingState() = runTest {
        val store = AgentExecutionStore()
        val gate = ApprovalGate(store, now = { agentNow() + testScheduler.currentTime })
        val answer = async { gate.await("run", "Test", 100) }
        runCurrent()
        advanceTimeBy(101)
        runCurrent()
        assertFalse(answer.await())
        assertNull(gate.pending.value)
        assertTrue(store.entries.value.any { it.status == ExecutionStatus.EXPIRED })
        val pending = launch { gate.await("run", "Test", 1000) }
        runCurrent()
        gate.cancel()
        pending.join()
        assertTrue(pending.isCancelled)
        assertNull(gate.pending.value)
    }
}
