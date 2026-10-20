@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.inspiredandroid.kai.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal sealed interface ReminderCommand {
    data class Create(val message: String, val at: Instant, val cron: String? = null) : ReminderCommand
    data object ListReminders : ReminderCommand
    data class Cancel(val id: String) : ReminderCommand
    data class Invalid(val explanation: String = REMINDER_USAGE) : ReminderCommand
}

internal const val REMINDER_USAGE =
    "Nothing was scheduled. Try 'Remind me in 20 minutes to pay bills', " +
        "'Remind me tomorrow at 9am to call Alex', or 'Remind me every day at 9am to drink water'. " +
        "Use /reminders to list reminders, /cancel-reminder ID to cancel, or /cron 0 9 * * 1-5 | message."

internal const val LOCAL_REMINDER_GUIDANCE =
    "Reminders are managed by the app, not by you. The app handles explicit reminder requests before model inference. " +
        "Never claim to have scheduled, changed, or cancelled a reminder yourself. " +
        "For unsupported requests, show a supported format: 'Remind me in 20 minutes to pay bills', " +
        "'Remind me tomorrow at 9am to call Alex', or 'Remind me every day at 9am to drink water'. " +
        "A real reminder has an app confirmation with a scheduled date and reminder ID. " +
        "Use /reminders to inspect saved reminders. Old assistant promises without a saved task are not real reminders."

/** Native local tools exclude scheduling; ordinary generated prose cannot confirm an action. */
internal fun guardLocalReminderClaim(response: String): String {
    val promise = Regex(
        """\b(?:i['’]ll remind you|i will remind you|(?:i['’]ve |i have |successfully )?(?:set|scheduled|created) (?:a |the |your )?reminder|(?:your |the )?reminder (?:is |was |has been )?(?:set|scheduled))\b""",
        RegexOption.IGNORE_CASE,
    )
    return if (promise.containsMatchIn(response)) "I have not created a saved reminder for that request. $REMINDER_USAGE" else response
}

/** Whole current-user messages only; no model-generated date or action arguments. */
internal fun parseReminderCommand(
    question: String?,
    now: Instant = Clock.System.now(),
    zone: TimeZone = TimeZone.currentSystemDefault(),
): ReminderCommand? {
    val text = question?.trim() ?: return null
    val lower = text.lowercase().trimEnd('?', '.', '!')
    if (lower in setOf("/reminders", "list reminders", "list my reminders", "show my reminders", "what reminders do i have")) {
        return ReminderCommand.ListReminders
    }
    val cancel = Regex("""^(?:/cancel-reminder|cancel reminder)\s*(.*)$""", RegexOption.IGNORE_CASE).matchEntire(text)
    if (cancel != null) return ReminderCommand.Cancel(cancel.groupValues[1].trim())
    val cron = Regex("""^/cron(?:\s+(.*))?$""", RegexOption.IGNORE_CASE).matchEntire(text)
    if (cron != null) {
        val parts = cron.groupValues[1].split('|', limit = 2)
        if (parts.size != 2 || parts[0].length > 200) return ReminderCommand.Invalid()
        return recurringReminder(parts[1].trim(), parts[0].trim(), now, zone)
    }
    val prefix = Regex("""^(?:please\s+)?(?:(?:can|could|would|will)\s+you\s+)?(?:please\s+)?(?:remind\s+me|set\s+a\s+reminder|/remind)(?:\s+|$)(.*)$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .matchEntire(text) ?: return null
    val body = prefix.groupValues[1].trim()
    if (body.length > 2_000 || body.isBlank()) return ReminderCommand.Invalid()

    // Relative duration, either before or after the message.
    val relativeFirst = Regex("""^in\s+(\d+)\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?|days?)\s+(?:to\s+|about\s+)?(.+)$""", RegexOption.IGNORE_CASE).matchEntire(body)
    val relativeLast = Regex("""^(?:to\s+|about\s+)?(.+?)\s+in\s+(\d+)\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?|days?)$""", RegexOption.IGNORE_CASE).matchEntire(body)
    if (relativeFirst != null || relativeLast != null) {
        val match = relativeFirst ?: relativeLast!!
        val amount = match.groupValues[if (relativeFirst != null) 1 else 2].toLongOrNull()
            ?: return ReminderCommand.Invalid()
        val unit = match.groupValues[if (relativeFirst != null) 2 else 3].lowercase()
        val multiplier = when {
            unit.startsWith("s") -> 1L
            unit.startsWith("m") -> 60L
            unit.startsWith("h") -> 3_600L
            else -> 86_400L
        }
        if (amount <= 0 || amount > 31_536_000L / multiplier) return ReminderCommand.Invalid("Nothing was scheduled. Use a delay from 1 second to 365 days.")
        val message = match.groupValues[if (relativeFirst != null) 3 else 1].trim()
        return validatedReminder(message, now + (amount * multiplier).seconds)
    }

    val time = """(\d{1,2}(?:[:.]\d{2})?\s*(?:am|pm)?)"""
    val recurring = Regex("""^every\s+(day|weekday|weekdays|weekend|weekends|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\s+at\s+$time\s+(?:to\s+|about\s+)?(.+)$""", RegexOption.IGNORE_CASE).matchEntire(body)
    if (recurring != null) {
        val clock = parseReminderTime(recurring.groupValues[2]) ?: return ReminderCommand.Invalid()
        val day = recurring.groupValues[1].lowercase()
        val days = when (day) {
            "day" -> "*"
            "weekday", "weekdays" -> "1-5"
            "weekend", "weekends" -> "0,6"
            else -> listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday").indexOf(day).toString()
        }
        return recurringReminder(recurring.groupValues[3].trim(), clock.minute.toString() + " " + clock.hour + " * * " + days, now, zone)
    }

    // Accept both "at 10.50pm today about ..." and "tomorrow at 9am to ...".
    val timeFirst = Regex("""^at\s+$time(?:\s+(today|tomorrow|\d{4}-\d{2}-\d{2}))?\s+(?:to\s+|about\s+)?(.+)$""", RegexOption.IGNORE_CASE).matchEntire(body)
    val dayFirst = Regex("""^(today|tomorrow|\d{4}-\d{2}-\d{2})\s+at\s+$time\s+(?:to\s+|about\s+)?(.+)$""", RegexOption.IGNORE_CASE).matchEntire(body)
    if (timeFirst != null || dayFirst != null) {
        val match = timeFirst ?: dayFirst!!
        val clock = parseReminderTime(match.groupValues[if (timeFirst != null) 1 else 2]) ?: return ReminderCommand.Invalid()
        val day = match.groupValues[if (timeFirst != null) 2 else 1].lowercase()
        val today = now.toLocalDateTime(zone).date
        val date = try {
            when (day) {
                "", "today" -> today
                "tomorrow" -> today.plus(1, DateTimeUnit.DAY)
                else -> kotlinx.datetime.LocalDate.parse(day)
            }
        } catch (_: IllegalArgumentException) {
            return ReminderCommand.Invalid("Nothing was scheduled. That date is invalid.")
        }
        var at = try {
            LocalDateTime(date, clock).toInstant(zone)
        } catch (_: IllegalArgumentException) {
            return ReminderCommand.Invalid()
        }
        if (at <= now && day.isEmpty()) at = LocalDateTime(today.plus(1, DateTimeUnit.DAY), clock).toInstant(zone)
        if (at <= now) return ReminderCommand.Invalid("Nothing was scheduled. That time has already passed. Please give a future date and time.")
        return validatedReminder(match.groupValues[3].trim(), at)
    }
    return ReminderCommand.Invalid()
}

private fun parseReminderTime(text: String): LocalTime? {
    val match = Regex("""^(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm)?$""", RegexOption.IGNORE_CASE).matchEntire(text.trim()) ?: return null
    var hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].ifEmpty { "0" }.toInt()
    val suffix = match.groupValues[3].lowercase()
    if (minute !in 0..59 || (suffix.isEmpty() && hour !in 0..23) || (suffix.isNotEmpty() && hour !in 1..12)) return null
    if (suffix.isNotEmpty()) hour = hour % 12 + if (suffix == "pm") 12 else 0
    return LocalTime(hour, minute)
}

private fun validatedReminder(message: String, at: Instant, cron: String? = null): ReminderCommand = if (message.isBlank() || message.length > 500) {
    ReminderCommand.Invalid("Nothing was scheduled. Include a reminder message of 1–500 characters.")
} else {
    ReminderCommand.Create(message, at, cron)
}

private fun recurringReminder(message: String, expression: String, now: Instant, zone: TimeZone): ReminderCommand {
    val at = try {
        CronExpression(expression).nextAfter(now, zone)
    } catch (_: IllegalArgumentException) {
        null
    } ?: return ReminderCommand.Invalid("Nothing was scheduled. Use a valid five-field cron expression with a future occurrence.")
    return validatedReminder(message, at, expression)
}

internal class LocalReminderCommands(
    private val settings: AppSettings,
    private val tasks: TaskStore,
    private val delivery: ReminderDelivery,
    private val now: () -> Instant = { Clock.System.now() },
) {
    suspend fun execute(command: ReminderCommand): String {
        if (command is ReminderCommand.Invalid) return command.explanation
        if (command is ReminderCommand.Cancel) {
            val matching = tasks.getAllTasks().filter { it.isReminder && command.id.length >= 8 && it.id.startsWith(command.id) }
            if (matching.size != 1) return "Nothing was cancelled. Use /cancel-reminder followed by the exact ID (or unique first 8 characters) shown by /reminders."
            val task = matching.single()
            delivery.cancel(task.id)
            tasks.removeTask(task.id)
            return "Cancelled reminder: " + task.description
        }
        if (command == ReminderCommand.ListReminders) {
            val pending = tasks.getAllTasks().filter { it.isReminder && it.status == TaskStatus.PENDING }.sortedBy { it.scheduledAtEpochMs }
            if (pending.isEmpty()) return "No pending reminders are saved. Old chat confirmations without a saved reminder ID were not scheduled."
            return buildString {
                append("**Saved reminders**")
                if (!settings.isSchedulingEnabled()) append(" — paused; enable Scheduled Tasks in Settings → Agent")
                for (task in pending.take(100)) {
                    append("\n\n- " + task.description + "\n  Next: " + task.scheduledAt.toLocalDateTime(TimeZone.currentSystemDefault()))
                    task.cron?.let { append("\n  Cron: `" + it + "`") }
                    append("\n  ID: " + task.id)
                }
                append("\n\nTimezone: " + TimeZone.currentSystemDefault().id)
            }
        }
        if (!settings.isSchedulingEnabled()) return "Nothing was scheduled. Enable Scheduled Tasks in Settings → Agent, then resend the reminder."
        val create = command as ReminderCommand.Create
        delivery.prepare()?.let { return it }
        // Recheck after permission dialogs; the requested instant may have elapsed.
        if (create.at <= now()) return "Nothing was scheduled. That time has now passed. Please resend with a future time."
        val task = tasks.addTask(create.message, create.message, create.at.toEpochMilliseconds(), cron = create.cron, isReminder = true)
        if (!delivery.schedule(task)) {
            delivery.cancel(task.id)
            tasks.removeTask(task.id)
            return "The reminder could not be registered with Android. Nothing was scheduled. Check notifications and Alarms & reminders permission, then try again."
        }
        return buildString {
            append("**Reminder scheduled**\n\n" + task.description)
            append("\nWhen: " + task.scheduledAt.toLocalDateTime(TimeZone.currentSystemDefault()) + " (" + TimeZone.currentSystemDefault().id + ")")
            task.cron?.let { append("\nRepeats: `" + it + "`") }
            append("\nID: " + task.id)
            append("\n\nAndroid will post a notification. Use /reminders to view it or /cancel-reminder " + task.id.take(8) + " to cancel.")
        }
    }
}
