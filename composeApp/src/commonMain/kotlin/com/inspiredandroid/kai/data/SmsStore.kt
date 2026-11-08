package com.inspiredandroid.kai.data

import kotlinx.serialization.serializer

class SmsStore(private val appSettings: AppSettings) {

    private val syncState = SettingsJsonValue(
        read = appSettings::getSmsSyncStateJson,
        write = appSettings::setSmsSyncStateJson,
        serializer = serializer<SmsSyncState>(),
        label = "SmsStore.syncState",
        default = { SmsSyncState() },
    )
    private val pendingQueue = PendingQueue<SmsMessage, Long>(
        readJson = appSettings::getSmsPendingJson,
        writeJson = appSettings::setSmsPendingJson,
        serializer = serializer<SmsMessage>(),
        label = "SmsStore.pending",
        keyOf = { it.id },
    )

    fun getSyncState(): SmsSyncState = syncState.get()

    suspend fun updateSyncState(state: SmsSyncState) = syncState.set(state)

    fun getPending(): List<SmsMessage> = pendingQueue.get()

    fun cloudMessage(message: SmsMessage): SmsMessage? = com.inspiredandroid.kai.events.EventPrivacyPolicy.cloudSms(message, appSettings.getAgentPreferences())

    suspend fun addPending(messages: List<SmsMessage>) = pendingQueue.add(messages.mapNotNull(com.inspiredandroid.kai.events.EventPrivacyPolicy::sms))

    suspend fun removePending(messages: List<SmsMessage>) = pendingQueue.remove(messages)
}
