package com.inspiredandroid.kai.data

/** Platform delivery never calls an AI or network service. */
interface ReminderDelivery {
    /** Null means notifications and precise alarms are available; otherwise nothing is saved. */
    suspend fun prepare(): String?
    fun schedule(task: ScheduledTask): Boolean
    fun cancel(id: String)
    fun refresh()
}

expect fun createReminderDelivery(settings: AppSettings, tasks: TaskStore): ReminderDelivery

class UnsupportedReminderDelivery : ReminderDelivery {
    override suspend fun prepare(): String = "Direct reminders are available in Kai Hybrid on Android. Nothing was scheduled."
    override fun schedule(task: ScheduledTask): Boolean = false
    override fun cancel(id: String) {}
    override fun refresh() {}
}
