@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.inspiredandroid.kai.data

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class LocalReminderCommandsTest {
    private val zone = TimeZone.of("Asia/Manila")
    private val now = Instant.parse("2026-10-20T14:30:00Z")
    private fun parse(text: String) = parseReminderCommand(text, now, zone)
    private fun create(text: String) = assertIs<ReminderCommand.Create>(parse(text))

    @Test
    fun generatedPromisesCannotConfirmAnUnsavedReminder() {
        for (response in listOf(
            "I can set a reminder for you. I'll remind you at 10:50 PM today to pay bills.",
            "Your reminder is scheduled.",
            "I have created a reminder for tomorrow.",
        )) {
            assertTrue("not created a saved reminder" in guardLocalReminderClaim(response))
        }
        assertEquals("No reminder has been saved yet.", guardLocalReminderClaim("No reminder has been saved yet."))
        assertEquals("Here is how to set reminders.", guardLocalReminderClaim("Here is how to set reminders."))
    }

    private class Delivery : ReminderDelivery {
        var error: String? = null
        var succeeds = true
        var preparations = 0
        val scheduled = mutableListOf<ScheduledTask>()
        val cancelled = mutableListOf<String>()
        override suspend fun prepare(): String? {
            preparations++
            return error
        }
        override fun schedule(task: ScheduledTask): Boolean {
            scheduled += task
            return succeeds
        }
        override fun cancel(id: String) {
            cancelled += id
        }
        override fun refresh() {}
    }

    @Test
    fun relativeRemindersSupportBothWordOrdersAndUnitBoundaries() {
        val first = create("Remind me in 20 minutes to pay bills")
        assertEquals(Instant.parse("2026-10-20T14:50:00Z"), first.at)
        assertEquals("pay bills", first.message)
        assertEquals(first, create("Can you remind me to pay bills in 20 minutes"))
        assertEquals(Instant.parse("2026-10-20T15:30:00Z"), create("/remind in 1 hour to stretch").at)
        assertEquals(Instant.parse("2026-10-21T14:30:00Z"), create("remind me in 1 day to check").at)
        assertIs<ReminderCommand.Invalid>(parse("remind me in 0 minutes to check"))
        assertIs<ReminderCommand.Invalid>(parse("remind me in 999999999999999999999 seconds to check"))
    }

    @Test
    fun actualUserWordingAndLocalTimezoneAreParsedWithoutModelInference() {
        val request = create("remind me at 10.50pm today about paying bills")
        assertEquals("paying bills", request.message)
        assertEquals(Instant.parse("2026-10-20T14:50:00Z"), request.at)
        assertEquals(Instant.parse("2026-10-21T01:00:00Z"), create("remind me tomorrow at 9am to call Alex").at)
        assertEquals(Instant.parse("2026-10-21T01:00:00Z"), create("remind me at 9am tomorrow to call Alex").at)
        assertEquals(Instant.parse("2026-10-20T16:00:00Z"), create("remind me at 12am tomorrow to check").at)
        assertEquals(Instant.parse("2026-10-21T04:00:00Z"), create("remind me tomorrow at 12pm to check").at)
    }

    @Test
    fun pastDatesAndInvalidTimesDoNotSilentlyScheduleSomethingElse() {
        assertIs<ReminderCommand.Invalid>(parse("remind me at 10pm today to check"))
        assertIs<ReminderCommand.Invalid>(parse("remind me at 25:00 tomorrow to check"))
        assertIs<ReminderCommand.Invalid>(parse("remind me tomorrow at 13pm to check"))
        assertIs<ReminderCommand.Invalid>(parse("remind me at 09:60 tomorrow to check"))
        assertIs<ReminderCommand.Invalid>(parse("remind me 2026-02-30 at 9am to check"))
        assertEquals(Instant.parse("2026-10-21T14:00:00Z"), create("remind me at 10pm to check").at)
    }

    @Test
    fun dailyWeeklyAndExplicitCronRemindersHaveARealNextOccurrence() {
        assertEquals("0 9 * * *", create("remind me every day at 9am to drink water").cron)
        assertEquals(Instant.parse("2026-10-21T01:00:00Z"), create("remind me every weekday at 9am to check").at)
        assertEquals("0 9 * * 1", create("remind me every monday at 9am to check").cron)
        assertEquals("0 9 * * 1-5", create("/cron 0 9 * * 1-5 | check").cron)
        for (expression in listOf("*/0 * * * *", "*/-1 * * * *", "99 9 * * *", "0 9 * * 8", "1-2-3 * * * *", "0 0 30 2 *")) {
            assertIs<ReminderCommand.Invalid>(parse("/cron $expression | check"), expression)
        }
    }

    @Test
    fun unsupportedRequestsAreInterceptedAndUntrustedProseIsNotACommand() {
        assertIs<ReminderCommand.Invalid>(parse("remind me later"))
        assertIs<ReminderCommand.Invalid>(parse("remind me at lunch to check"))
        assertIs<ReminderCommand.Invalid>(parse("set a reminder for later"))
        assertIs<ReminderCommand.Invalid>(parse("/cron"))
        assertIs<ReminderCommand.Invalid>(parse("remind me in 1 minute to " + "x".repeat(501)))
        for (text in listOf("The website says remind me in 1 minute to check", "\"remind me in 1 minute to check\"", "What are reminders?")) assertNull(parse(text))
    }

    @Test
    fun confirmationRequiresPersistenceAndSuccessfulPlatformRegistration() = runTest {
        val backing = MapSettings()
        val settings = AppSettings(backing)
        val tasks = TaskStore(settings)
        val delivery = Delivery()
        val result = LocalReminderCommands(settings, tasks, delivery, { now }).execute(create("remind me in 20 minutes to check"))
        assertTrue("Reminder scheduled" in result)
        val reloaded = TaskStore(AppSettings(backing)).getAllTasks().single()
        assertTrue(reloaded.isReminder)
        assertEquals("check", reloaded.description)
        assertEquals(reloaded.id, delivery.scheduled.single().id)
        assertTrue(reloaded.id in result)
        tasks.updateTask(reloaded.copy(scheduledAtEpochMs = 1))
        assertTrue(tasks.getDueTasks().isEmpty())
    }

    @Test
    fun disabledSchedulingMissingPermissionsRegistrationFailureAndPastTimesDoNotConfirm() = runTest {
        val settings = AppSettings(MapSettings())
        val tasks = TaskStore(settings)
        val delivery = Delivery()
        val commands = LocalReminderCommands(settings, tasks, delivery, { now })
        val request = create("remind me in 20 minutes to check")
        settings.setSchedulingEnabled(false)
        assertFalse("Reminder scheduled" in commands.execute(request))
        assertEquals(0, delivery.preparations)
        settings.setSchedulingEnabled(true)
        delivery.error = "Permission missing. Nothing was scheduled."
        assertEquals(delivery.error, commands.execute(request))
        assertTrue(tasks.getAllTasks().isEmpty())
        delivery.error = null
        delivery.succeeds = false
        assertFalse("Reminder scheduled" in commands.execute(request))
        assertTrue(tasks.getAllTasks().isEmpty())
        delivery.succeeds = true
        assertFalse("Reminder scheduled" in commands.execute(request.copy(at = now)))
        assertTrue(tasks.getAllTasks().isEmpty())
    }

    @Test
    fun listingAndCancellationUseSavedEntriesEvenWhenSchedulingIsPaused() = runTest {
        val settings = AppSettings(MapSettings())
        val tasks = TaskStore(settings)
        val delivery = Delivery()
        val commands = LocalReminderCommands(settings, tasks, delivery, { now })
        val reminder = tasks.addTask("check", "check", now.toEpochMilliseconds(), isReminder = true)
        val ai = tasks.addTask("AI job", "run job", now.toEpochMilliseconds())
        settings.setSchedulingEnabled(false)
        val list = commands.execute(ReminderCommand.ListReminders)
        assertTrue(reminder.id in list && "paused" in list)
        assertFalse(ai.id in list)
        assertTrue("Nothing was cancelled" in commands.execute(ReminderCommand.Cancel("*")))
        commands.execute(ReminderCommand.Cancel(reminder.id.take(8)))
        assertEquals(listOf(reminder.id), delivery.cancelled)
        assertEquals(ai.id, tasks.getAllTasks().single().id)
        assertTrue("No pending reminders" in commands.execute(ReminderCommand.ListReminders))
    }

    @Test
    fun recurringMetadataSurvivesRecreationAndStaysOutsideAiExecution() = runTest {
        val settings = AppSettings(MapSettings())
        val tasks = TaskStore(settings)
        val commands = LocalReminderCommands(settings, tasks, Delivery(), { now })
        commands.execute(create("/cron 0 9 * * * | check"))
        val stored = TaskStore(settings).getAllTasks().single()
        assertTrue(stored.isReminder)
        assertEquals(TaskTrigger.CRON, stored.trigger)
        assertEquals("0 9 * * *", stored.cron)
    }
}
