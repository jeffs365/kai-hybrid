@file:OptIn(kotlin.time.ExperimentalTime::class, kotlin.uuid.ExperimentalUuidApi::class)

package com.inspiredandroid.kai.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Serializable
data class AgentPreferences(
    val enabled: Boolean = false,
    val notificationCloud: Boolean = false,
    val notificationSources: Set<String> = emptySet(),
    val smsCloud: Boolean = false,
    val smsSources: Set<String> = emptySet(),
    val retentionDays: Int = 7,
)

enum class AgentOrigin { DIRECT_USER, FOREGROUND_USER, BACKGROUND }

@Serializable
enum class ExecutionStatus { RUNNING, WAITING_APPROVAL, SUCCEEDED, FAILED, DENIED, CANCELLED, TIMED_OUT, INTERRUPTED, EXPIRED }

@Serializable
data class ExecutionEntry(
    val id: String,
    val runId: String,
    val kind: String,
    val tool: String,
    val origin: String,
    val status: ExecutionStatus,
    val startedAt: Long,
    val updatedAt: Long = startedAt,
    val durationMs: Long = 0,
    val errorCode: String? = null,
)

@Serializable
data class AgentToolResult(val success: Boolean, val content: String, val code: String = "observed") {
    fun json(): String = Json.encodeToString(serializer(), this)
}

data class AgentCall(val id: String, val name: String, val arguments: String)
data class AgentModelTurn(val text: String, val calls: List<AgentCall> = emptyList(), val reasoning: String? = null)

data class AgentActivity(val runId: String, val label: String, val step: Int = 0)
internal fun agentId(): String = Uuid.random().toString()
internal fun agentNow(): Long = Clock.System.now().toEpochMilliseconds()

class AgentScope internal constructor(
    val runId: String,
    val origin: AgentOrigin,
    internal val flashlightTarget: Boolean?,
) {
    // Provider call IDs/arguments and observed results live only for this run, never in the journal.
    internal val completed = mutableMapOf<String, Pair<PhoneAction, AgentToolResult>>()
    internal val observed = mutableListOf<AgentToolResult>()
    internal var failureCode: String? = null
}

enum class PhoneAction(val tool: String) {
    BATTERY("phone_battery"),
    STATUS("phone_status"),
    LIGHT_ON("phone_flashlight"),
    LIGHT_OFF("phone_flashlight"),
    LIGHT_STATUS("phone_flashlight"),
    ;

    val writes: Boolean get() = this == LIGHT_ON || this == LIGHT_OFF
}
