package com.inspiredandroid.kai.data

import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.serializer
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Persistence for notifications captured by [com.inspiredandroid.kai.notifications.KaiNotificationListenerService].
 *
 * Two collections:
 * - **Pending queue** — capped FIFO that fills as the listener fires and gets snapshotted
 *   into the heartbeat prompt, then drained. Mirrors [SmsStore].
 * - **Store** — broader rolling history backing [com.inspiredandroid.kai.notifications.NotificationReader],
 *   bounded by per-app cap and age cap.
 *
 * Per-app gating is handled by the system Notification Access "Apps" picker — if the
 * user unchecks an app there, `onNotificationPosted` is never called for that package
 * in the first place, so this store never sees it.
 */
@OptIn(ExperimentalTime::class)
class NotificationStore(private val appSettings: AppSettings) {

    private val store = SettingsJsonList(
        read = appSettings::getNotificationsStoreJson,
        write = appSettings::setNotificationsStoreJson,
        itemSerializer = serializer<NotificationRecord>(),
        label = "NotificationStore",
    )
    private val syncState = SettingsJsonValue(
        read = appSettings::getNotificationsSyncStateJson,
        write = appSettings::setNotificationsSyncStateJson,
        serializer = serializer<NotificationSyncState>(),
        label = "NotificationStore.syncState",
        default = { NotificationSyncState() },
    )
    private val pendingQueue = PendingQueue<NotificationRecord, String>(
        readJson = appSettings::getNotificationsPendingJson,
        writeJson = appSettings::setNotificationsPendingJson,
        serializer = serializer<NotificationRecord>(),
        label = "NotificationStore.pending",
        keyOf = { it.id },
    )

    fun getPending(): List<NotificationRecord> = pendingQueue.get().filter {
        it.postedAtEpochMs >= Clock.System.now().toEpochMilliseconds() - MAX_AGE_MS && com.inspiredandroid.kai.events.EventPrivacyPolicy.notification(it) != null
    }

    suspend fun addPending(record: NotificationRecord) {
        val safe = com.inspiredandroid.kai.events.EventPrivacyPolicy.notification(record) ?: return
        if (pendingQueue.get().any { com.inspiredandroid.kai.events.EventPrivacyPolicy.sameNotification(it, safe) }) return
        pendingQueue.add(listOf(safe))
    }

    private val cloudAliases = kotlinx.coroutines.flow.MutableStateFlow<Map<String, String>>(emptyMap())
    fun cloudRecord(record: NotificationRecord): NotificationRecord? {
        val safe = com.inspiredandroid.kai.events.EventPrivacyPolicy.cloudNotification(record, appSettings.getAgentPreferences()) ?: return null
        val key = record.packageName + "\u0000" + record.id
        val alias = cloudAliases.updateAndGet { current ->
            if (key in current) current else current.entries.toList().takeLast(4999).associate { it.toPair() } + (key to newCloudAlias())
        }.getValue(key)
        return safe.copy(id = alias)
    }

    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    private fun newCloudAlias(): String = "event-" + kotlin.uuid.Uuid.random().toString()

    suspend fun removePending(records: List<NotificationRecord>) = pendingQueue.remove(records)

    suspend fun clearPending() = pendingQueue.clear()

    fun getStore(): List<NotificationRecord> = store.get()

    suspend fun addRecord(record: NotificationRecord) {
        val safe = com.inspiredandroid.kai.events.EventPrivacyPolicy.notification(record) ?: return
        store.update { current ->
            val same = current.firstOrNull { it.id == safe.id && it.packageName == safe.packageName }
            if (same == safe) {
                current
            } else {
                prune(current.filterNot { it.id == safe.id && it.packageName == safe.packageName } + safe)
            }
        }
    }

    private val captureMutex = kotlinx.coroutines.sync.Mutex()
    suspend fun capture(record: NotificationRecord) {
        captureMutex.lock()
        try {
            val safe = com.inspiredandroid.kai.events.EventPrivacyPolicy.notification(record) ?: return
            if (store.get().any { it.id == safe.id && it.packageName == safe.packageName && it.postedAtEpochMs > safe.postedAtEpochMs }) return
            if (store.get().any { com.inspiredandroid.kai.events.EventPrivacyPolicy.sameNotification(it, safe) && kotlin.math.abs(it.postedAtEpochMs - safe.postedAtEpochMs) < 300_000 }) return
            addRecord(safe)
            addPending(safe)
        } finally {
            captureMutex.unlock()
        }
    }

    /** Drops records older than 24h or beyond the per-package cap. Called after each heartbeat. */
    suspend fun sweep() {
        store.update { prune(it) }
    }

    /** The retention bounds: newest [MAX_PER_PACKAGE] per package, nothing older than [MAX_AGE_MS]. */
    private fun prune(records: List<NotificationRecord>): List<NotificationRecord> {
        val ageCutoff = Clock.System.now().toEpochMilliseconds() - MAX_AGE_MS
        return records
            .filter { it.postedAtEpochMs >= ageCutoff }
            .groupBy { it.packageName }
            .flatMap { (_, msgs) -> msgs.sortedByDescending { it.postedAtEpochMs }.take(MAX_PER_PACKAGE) }
            .sortedByDescending { it.postedAtEpochMs }
    }

    fun getSyncState(): NotificationSyncState = syncState.get()

    suspend fun updateSyncState(state: NotificationSyncState) = syncState.set(state)

    companion object {
        private const val MAX_PER_PACKAGE = 50
        private const val MAX_AGE_MS = 24L * 60L * 60L * 1000L
    }
}
