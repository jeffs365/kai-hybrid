package com.inspiredandroid.kai.agent

import com.inspiredandroid.kai.agentdb.AgentDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** Separate database. Only host-generated IDs and fixed metadata codes enter this store. */
class AgentExecutionStore(private val database: AgentDatabase? = null, var retentionDays: Int = 7) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val _entries = MutableStateFlow<List<ExecutionEntry>>(emptyList())
    val entries: StateFlow<List<ExecutionEntry>> = _entries

    init {
        val loaded = database?.agent_executionQueries?.selectRecent()?.executeAsList().orEmpty()
            .mapNotNull { runCatching { json.decodeFromString<ExecutionEntry>(it.metadataJson) }.getOrNull() }
        _entries.value = loaded.map { entry ->
            if (entry.status == ExecutionStatus.RUNNING || entry.status == ExecutionStatus.WAITING_APPROVAL) {
                entry.copy(status = ExecutionStatus.INTERRUPTED, updatedAt = agentNow(), errorCode = "process_interrupted").also(::persist)
            } else {
                entry
            }
        }
        pruneLocked(retentionDays.coerceIn(1, 30))
    }
    private fun persist(entry: ExecutionEntry) {
        database?.agent_executionQueries?.upsert(entry.id, entry.runId, entry.updatedAt, json.encodeToString(ExecutionEntry.serializer(), entry))
    }
    suspend fun put(entry: ExecutionEntry) = mutex.withLock {
        persist(entry)
        _entries.value = (_entries.value.filterNot { it.id == entry.id } + entry).sortedByDescending { it.startedAt }
        pruneLocked(retentionDays.coerceIn(1, 30))
    }
    fun find(id: String): ExecutionEntry? = _entries.value.firstOrNull { it.id == id }
    suspend fun prune(days: Int) = mutex.withLock {
        retentionDays = days.coerceIn(1, 30)
        pruneLocked(retentionDays)
    }
    private fun pruneLocked(days: Int) {
        val cutoff = agentNow() - days * 86_400_000L
        val terminal = _entries.value.filter { it.status != ExecutionStatus.RUNNING && it.status != ExecutionStatus.WAITING_APPROVAL }
        val keep = terminal.filter { it.updatedAt >= cutoff }.take(500).map { it.id }.toSet()
        val remove = terminal.filter { it.id !in keep }.map { it.id }.toSet()
        remove.forEach { database?.agent_executionQueries?.deleteEntry(it) }
        _entries.value = _entries.value.filterNot { it.id in remove }
    }
    suspend fun delete(id: String) = mutex.withLock {
        val entry = find(id) ?: return@withLock
        if (entry.status == ExecutionStatus.RUNNING || entry.status == ExecutionStatus.WAITING_APPROVAL) return@withLock
        database?.agent_executionQueries?.deleteEntry(id)
        _entries.value = _entries.value.filterNot { it.id == id }
    }
    suspend fun clearCompleted() = mutex.withLock {
        val active = _entries.value.filter { it.status == ExecutionStatus.RUNNING || it.status == ExecutionStatus.WAITING_APPROVAL }
        _entries.value.filterNot { it in active }.forEach { database?.agent_executionQueries?.deleteEntry(it.id) }
        _entries.value = active
    }
}

expect fun createAgentExecutionStore(): AgentExecutionStore
