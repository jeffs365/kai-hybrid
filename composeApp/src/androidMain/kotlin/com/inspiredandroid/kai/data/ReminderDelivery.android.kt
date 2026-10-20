@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.inspiredandroid.kai.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.inspiredandroid.kai.permissionQualifier
import com.inspiredandroid.kai.shared.R
import com.inspiredandroid.kai.tools.AppPermission
import com.inspiredandroid.kai.tools.PermissionController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.java.KoinJavaComponent.get
import kotlin.time.Clock

actual fun createReminderDelivery(settings: AppSettings, tasks: TaskStore): ReminderDelivery = AndroidReminderDelivery(get(Context::class.java), settings, tasks)

private const val REMINDER_ACTION = "com.inspiredandroid.kai.REMINDER"
private const val REMINDER_CHANNEL = "kai_reminders"
private const val REMINDER_ID = 9003

class AndroidReminderDelivery(
    private val context: Context,
    private val settings: AppSettings,
    private val tasks: TaskStore,
) : ReminderDelivery {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    // Only identifiers, never reminder contents or credentials.
    private val registry = context.getSharedPreferences("reminder_alarm_ids", Context.MODE_PRIVATE)
    private val deliveryMutex = Mutex()

    override suspend fun prepare(): String? {
        val controller = get<PermissionController>(PermissionController::class.java, permissionQualifier(AppPermission.POST_NOTIFICATIONS))
        if (!controller.requestPermission()) return "Nothing was scheduled. Allow Kai Hybrid notifications in Android settings, then resend the reminder."
        ensureChannel()
        if (!canNotify()) return "Nothing was scheduled. Enable the Reminders notification channel for Kai Hybrid in Android settings."
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
            try {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: RuntimeException) {
                // The message below still gives the manual path if no settings activity exists.
            }
            return "Nothing was scheduled. Enable 'Allow setting alarms and reminders' for Kai Hybrid in Android Settings → Apps → Special access → Alarms & reminders, then return and resend your request. This permission lets your reminder fire while the app is closed."
        }
        return null
    }

    private fun canNotify(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        notifications.getNotificationChannel(REMINDER_CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    private fun pendingIntent(id: String, due: Long = 0): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(REMINDER_ACTION)
            .setData(Uri.parse("kai-reminder://" + context.packageName + "/" + id))
            .putExtra("due", due)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @Synchronized
    override fun schedule(task: ScheduledTask): Boolean {
        if (!task.isReminder || task.status != TaskStatus.PENDING || !settings.isSchedulingEnabled()) return false
        ensureChannel()
        if (!canNotify() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms())) return false
        return try {
            alarms.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                maxOf(task.scheduledAtEpochMs, System.currentTimeMillis() + 1_000),
                pendingIntent(task.id, task.scheduledAtEpochMs),
            )
            val ids = registry.getStringSet("ids", emptySet()).orEmpty().toMutableSet()
            ids.add(task.id)
            registry.edit().putStringSet("ids", ids).apply()
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    @Synchronized
    override fun cancel(id: String) {
        val pending = pendingIntent(id)
        alarms.cancel(pending)
        pending.cancel()
        notifications.cancel(id, REMINDER_ID)
        val ids = registry.getStringSet("ids", emptySet()).orEmpty().toMutableSet()
        ids.remove(id)
        registry.edit().putStringSet("ids", ids).apply()
    }

    @Synchronized
    override fun refresh() {
        val pending = tasks.getAllTasks().filter { it.isReminder && it.status == TaskStatus.PENDING }
        val wanted = if (settings.isSchedulingEnabled()) pending.map { it.id }.toSet() else emptySet()
        for (id in registry.getStringSet("ids", emptySet()).orEmpty().toSet() - wanted) cancel(id)
        if (settings.isSchedulingEnabled()) pending.forEach { schedule(it) }
    }

    /** Clock/zone changes recompute recurring local-time schedules; one-off instants stay fixed. */
    suspend fun restore(rebaseCron: Boolean) {
        if (rebaseCron) {
            for (task in tasks.getAllTasks().filter { it.isReminder && it.status == TaskStatus.PENDING && it.cron != null }) {
                val next = runCatching { CronExpression(task.cron!!).nextAfter(Clock.System.now()) }.getOrNull()
                if (next != null) tasks.updateTask(task.copy(scheduledAtEpochMs = next.toEpochMilliseconds()))
            }
        }
        refresh()
    }

    suspend fun deliver(id: String, due: Long) = deliveryMutex.withLock {
        val task = tasks.getAllTasks().firstOrNull { it.id == id && it.isReminder && it.status == TaskStatus.PENDING }
            ?: return@withLock
        // Stale broadcasts cannot deliver a cancelled or already advanced occurrence.
        if (!settings.isSchedulingEnabled() || task.scheduledAtEpochMs != due) return@withLock
        if (System.currentTimeMillis() + 500 < due) {
            schedule(task)
            return@withLock
        }
        ensureChannel()
        if (!canNotify()) {
            tasks.updateTask(task.copy(lastResult = "Reminder not delivered: notifications are disabled. Re-enable them and open Kai to retry."))
            return@withLock
        }
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.setData(Uri.parse("kai-reminder-open://" + context.packageName + "/" + id))
        val open = launch?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Kai reminder")
            .setContentText(task.description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(task.description))
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setWhen(task.scheduledAtEpochMs)
            .setAutoCancel(true)
            .build()
        try {
            notifications.notify(id, REMINDER_ID, notification)
        } catch (_: RuntimeException) {
            tasks.updateTask(task.copy(lastResult = "Android could not post this reminder. Check notification settings and reopen Kai."))
            return@withLock
        }
        val now = Clock.System.now()
        val next = task.cron?.let { runCatching { CronExpression(it).nextAfter(now) }.getOrNull() }
        val updated = task.copy(
            status = if (next == null) TaskStatus.COMPLETED else TaskStatus.PENDING,
            scheduledAtEpochMs = next?.toEpochMilliseconds() ?: task.scheduledAtEpochMs,
            lastResult = "Reminder notification posted at $now",
            recentExecutions = (listOf(TaskExecutionLogEntry(now.toEpochMilliseconds(), true, "Reminder notification posted")) + task.recentExecutions).take(10),
        )
        tasks.updateTask(updated)
        if (next != null) schedule(updated)
        // No model initialization or network request occurs in this receiver.
    }

    private fun ensureChannel() {
        if (notifications.getNotificationChannel(REMINDER_CHANNEL) == null) {
            notifications.createNotificationChannel(
                NotificationChannel(REMINDER_CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Reminders requested in Kai Hybrid"
                    enableVibration(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                },
            )
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val supported = setOf(REMINDER_ACTION, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
        if (action !in supported) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val delivery = get<ReminderDelivery>(ReminderDelivery::class.java) as AndroidReminderDelivery
                if (action == REMINDER_ACTION) {
                    val id = intent.data?.lastPathSegment ?: return@launch
                    delivery.deliver(id, intent.getLongExtra("due", -1))
                } else {
                    delivery.restore(action == Intent.ACTION_TIME_CHANGED || action == Intent.ACTION_TIMEZONE_CHANGED)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                println("Kai reminders: delivery/restore failed; inspect saved task status.")
            } finally {
                pending.finish()
            }
        }
    }
}
