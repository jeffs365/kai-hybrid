@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.inspiredandroid.kai.agent

import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.data.FlashlightState
import com.inspiredandroid.kai.data.PhoneCommand
import com.inspiredandroid.kai.data.PhoneControls
import com.inspiredandroid.kai.data.PhoneResponses
import com.inspiredandroid.kai.data.guardLocalPhoneClaims
import com.inspiredandroid.kai.data.guardLocalReminderClaim
import com.inspiredandroid.kai.data.parsePhoneCommand
import com.inspiredandroid.kai.ui.chat.History
import com.inspiredandroid.kai.ui.chat.ToolCallInfo
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** A single host-owned coordinator shared by direct commands and structured agent calls. */
class AgentRuntime(val settings: AppSettings, private val controls: PhoneControls, val store: AgentExecutionStore) {
    val preferences get() = settings.agentPreferences
    val supported get() = controls.supported
    val approvals = ApprovalGate(store)
    init {
        store.retentionDays = settings.getAgentPreferences().retentionDays
    }
    private val runLane = Mutex()
    private val callLane = Mutex()
    private val _active = MutableStateFlow<AgentActivity?>(null)
    val active: StateFlow<AgentActivity?> = _active

    @kotlin.concurrent.Volatile
    private var runningJob: Job? = null
    private var currentScope: AgentScope? = null

    fun stop() {
        runningJob?.cancel()
        approvals.cancel()
    }
    suspend fun updatePreferences(value: AgentPreferences) {
        if (preferences.value.enabled && !value.enabled) stop()
        settings.setAgentPreferences(value)
        store.prune(settings.getAgentPreferences().retentionDays)
    }
    private suspend fun run(question: String, origin: AgentOrigin, deadlineMs: Long, block: suspend (AgentScope) -> String): String {
        if (!runLane.tryLock()) return "Kai is already handling a phone task. Stop it or wait for it to finish."
        val runId = agentId()
        val command = parsePhoneCommand(question) as? PhoneCommand.Flashlight
        val scope = AgentScope(runId, origin, command?.enabled)
        var terminal = ExecutionStatus.FAILED
        var code: String? = "operation_failed"
        val start = agentNow()
        try {
            store.put(ExecutionEntry(runId, runId, "run", "phone_task", origin.name, ExecutionStatus.RUNNING, start))
            currentScope = scope
            _active.value = AgentActivity(runId, "Phone task")
            val result = withTimeout(deadlineMs) {
                runningJob = currentCoroutineContext()[Job]
                block(scope)
            }
            code = scope.failureCode ?: scope.observed.firstOrNull { !it.success }?.code
            terminal = if (code == null) ExecutionStatus.SUCCEEDED else ExecutionStatus.FAILED
            return result
        } catch (e: TimeoutCancellationException) {
            terminal = ExecutionStatus.CANCELLED
            code = "parent_cancelled"
            currentCoroutineContext().ensureActive()
            terminal = ExecutionStatus.TIMED_OUT
            code = "run_deadline"
            return "The phone task reached its time limit and stopped. An action already completed cannot be undone by Stop; request a fresh status if needed."
        } catch (e: CancellationException) {
            terminal = ExecutionStatus.CANCELLED
            code = "user_cancelled"
            throw e
        } catch (_: Exception) {
            return "The phone task could not finish. No automatic retry or provider fallback was made. Check Activity history in Settings → Agent."
        } finally {
            withContext(NonCancellable) {
                try {
                    store.find(runId)?.let { store.put(it.copy(status = terminal, updatedAt = agentNow(), durationMs = agentNow() - start, errorCode = code)) }
                } finally {
                    currentScope = null
                    runningJob = null
                    _active.value = null
                    runLane.unlock()
                }
            }
        }
    }
    internal suspend fun execute(scope: AgentScope, call: AgentCall): AgentToolResult = callLane.withLock {
        fun denied(code: String) = AgentToolResult(false, "Phone action not performed ($code).", code)
        if (scope !== currentScope || store.find(scope.runId)?.status != ExecutionStatus.RUNNING) return@withLock denied("inactive_run")
        val action = AgentToolRegistry.decode(call.name, call.arguments)
        val key = agentId()
        val start = agentNow()
        val entry = ExecutionEntry(key, scope.runId, "call", call.name.takeIf { it in AgentToolRegistry.names } ?: "unsupported_tool", scope.origin.name, ExecutionStatus.RUNNING, start)
        var terminal = ExecutionStatus.DENIED
        var result = denied("invalid_arguments_or_tool")
        try {
            store.put(entry)
            if (action == null || call.id.isBlank() || call.id.length > 256) return@withLock result
            if (scope.origin == AgentOrigin.FOREGROUND_USER && !settings.getAgentPreferences().enabled) {
                result = denied("safe_mode_disabled")
                return@withLock result
            }
            scope.completed[call.id]?.let { (previous, observed) ->
                result = if (previous == action) observed else denied("call_id_reused")
                terminal = if (result.success) ExecutionStatus.SUCCEEDED else ExecutionStatus.DENIED
                return@withLock result
            }
            val denial = ToolPolicy.denial(action, scope, controls.supported, settings.isToolEnabled(action.tool), controls.canMutate())
            result = when {
                denial != null -> denied(denial)

                else -> {
                    terminal = ExecutionStatus.FAILED
                    withTimeout(30_000) {
                        currentCoroutineContext().ensureActive()
                        when (action) {
                            PhoneAction.BATTERY -> controls.battery()?.let { AgentToolResult(true, PhoneResponses.battery(it)) }
                                ?: AgentToolResult(false, "Android could not provide a battery reading.", "reading_unavailable")

                            PhoneAction.STATUS -> controls.status()?.let { AgentToolResult(true, PhoneResponses.status(it)) }
                                ?: AgentToolResult(false, "Android could not provide phone status.", "reading_unavailable")

                            else -> {
                                val target = when (action) {
                                    PhoneAction.LIGHT_ON -> true
                                    PhoneAction.LIGHT_OFF -> false
                                    else -> null
                                }
                                val value = controls.flashlight(target)
                                val verified = value.state in setOf(FlashlightState.ON, FlashlightState.OFF) &&
                                    (target == null || (value.state == FlashlightState.ON) == target) && value.message == null
                                AgentToolResult(verified, PhoneResponses.flashlight(value) + if (verified) PhoneResponses.timestamp() else "", if (verified) "observed" else "state_unconfirmed")
                            }
                        }
                    }
                }
            }
            terminal = if (result.success) {
                ExecutionStatus.SUCCEEDED
            } else if (terminal == ExecutionStatus.DENIED) {
                terminal
            } else {
                ExecutionStatus.FAILED
            }
            scope.completed[call.id] = action to result
            return@withLock result
        } catch (e: TimeoutCancellationException) {
            terminal = ExecutionStatus.CANCELLED
            result = denied("cancelled_state_unknown")
            currentCoroutineContext().ensureActive()
            terminal = ExecutionStatus.TIMED_OUT
            result = AgentToolResult(false, "Phone operation timed out; its final state was not confirmed.", "tool_deadline")
            action?.let { scope.completed[call.id] = it to result }
            return@withLock result
        } catch (e: CancellationException) {
            terminal = ExecutionStatus.CANCELLED
            result = denied("cancelled_state_unknown")
            throw e
        } catch (_: Exception) {
            terminal = ExecutionStatus.FAILED
            result = denied("backend_error_state_unknown")
            action?.let { scope.completed[call.id] = it to result }
            return@withLock result
        } finally {
            scope.observed += result
            if (!result.success) scope.failureCode = result.code
            withContext(NonCancellable) { store.put(entry.copy(status = terminal, updatedAt = agentNow(), durationMs = agentNow() - start, errorCode = result.code.takeUnless { result.success })) }
        }
    }
    internal suspend fun direct(command: PhoneCommand): String {
        val action = when (command) {
            PhoneCommand.Battery -> PhoneAction.BATTERY

            PhoneCommand.Status -> PhoneAction.STATUS

            is PhoneCommand.Flashlight -> when (command.enabled) {
                true -> PhoneAction.LIGHT_ON
                false -> PhoneAction.LIGHT_OFF
                null -> PhoneAction.LIGHT_STATUS
            }

            else -> return "Flashlight unchanged. Send one immediate command."
        }
        val question = when (action) {
            PhoneAction.LIGHT_ON -> "/flashlight on"
            PhoneAction.LIGHT_OFF -> "/flashlight off"
            else -> ""
        }
        return run(question, AgentOrigin.DIRECT_USER, 90_000) { scope -> execute(scope, AgentCall("direct", action.tool, arguments(action))).content }
    }
    suspend fun chat(question: String, history: List<History>, model: AgentModelClient, deadlineMs: Long = 90_000, maxSteps: Int = 8): String {
        if (!settings.getAgentPreferences().enabled) return "Safe agent mode is off. Enable it in Settings → Agent."
        return run(question, AgentOrigin.FOREGROUND_USER, deadlineMs) { scope ->
            val messages = history.toMutableList()
            val signatures = mutableMapOf<List<Pair<String, String>>, Int>()
            repeat(maxSteps.coerceIn(1, 8)) { step ->
                currentCoroutineContext().ensureActive()
                if (!settings.getAgentPreferences().enabled) throw CancellationException("Agent disabled")
                _active.value = AgentActivity(scope.runId, "Phone task", step + 1)
                val reply = model.chat(messages.toList())
                if (reply.calls.isEmpty()) {
                    if (reply.text.contains("<tool_call>", ignoreCase = true)) {
                        scope.failureCode = "inline_tool_text_rejected"
                        return@run "The model returned unsupported inline tool text. No action was performed."
                    }
                    if (scope.observed.isEmpty() && parsePhoneCommand(question) != null) {
                        scope.failureCode = "requested_tool_not_called"
                        return@run "The model did not call a phone tool for this request. No phone action was performed and no current state was verified. Use /battery, /phone status, or /flashlight on, off or status for a direct command."
                    }
                    return@run if (scope.observed.isNotEmpty()) {
                        "**DeepSeek agent**\n\n" + scope.observed.distinct().joinToString("\n\n") { it.content }
                    } else {
                        guardLocalReminderClaim(guardLocalPhoneClaims(reply.text.ifBlank { "No phone action was performed." }))
                    }
                }
                if (reply.calls.size > 8 || reply.calls.map { it.id }.toSet().size != reply.calls.size) {
                    scope.failureCode = "invalid_batch"
                    return@run "The model returned an invalid tool batch. No action in that batch was performed."
                }
                val signature = reply.calls.map { it.name to it.arguments }
                signatures[signature] = (signatures[signature] ?: 0) + 1
                if (signatures.getValue(signature) >= 3) {
                    scope.failureCode = "repeating_calls"
                    return@run "The agent repeated the same request and was stopped. Ask for a fresh status if needed."
                }
                messages += History(
                    role = History.Role.ASSISTANT,
                    content = reply.text,
                    reasoningContent = reply.reasoning,
                    toolCalls = reply.calls.map { ToolCallInfo(it.id, it.name, it.arguments) }.toImmutableList(),
                )
                for (call in reply.calls) {
                    val result = execute(scope, call)
                    messages += History(role = History.Role.TOOL, content = result.json(), toolCallId = call.id, toolName = call.name)
                }
            }
            scope.failureCode = "step_limit"
            "The agent reached its step limit and stopped. Check Activity history for observed results."
        }
    }
    private fun arguments(action: PhoneAction): String = when (action) {
        PhoneAction.BATTERY, PhoneAction.STATUS -> "{}"

        else -> "{\"state\":\"${when (action) {
            PhoneAction.LIGHT_ON -> "on"
            PhoneAction.LIGHT_OFF -> "off"
            else -> "status"
        }}\"}"
    }
}
