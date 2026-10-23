package com.inspiredandroid.kai.data

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhoneCommandsTest {
    @Test
    fun ordinaryGeneratedTextCannotInventPhoneReadingsOrConfirmHardwareActions() {
        for (response in listOf("I've turned on the flashlight.", "I will turn your torch off.", "Your flashlight is now on.", "Your battery is currently at 74%.", "Battery: 80%")) {
            assertTrue("not verified" in guardLocalPhoneClaims(response), response)
        }
    }

    @Test
    fun phoneGuidanceAndGeneralBatteryAdviceRemainUsable() {
        for (response in listOf("Ask /battery for a fresh reading.", "A flashlight uses the camera's LED.", "Charging speed varies with temperature.", "I cannot verify the flashlight state.")) {
            assertEquals(response, guardLocalPhoneClaims(response))
        }
    }
    private class Controls : PhoneControls {
        override var supported = true
        var reads = 0
        var statusReads = 0
        val requests = mutableListOf<Boolean?>()
        var reading: BatterySnapshot? = BatterySnapshot(47, "Charging", "USB", 321, false)
        var flash = FlashlightResult(FlashlightState.OFF)
        var cancel = false
        override suspend fun battery(): BatterySnapshot? {
            reads++
            return reading
        }
        override suspend fun status(): PhoneSnapshot {
            statusReads++
            return PhoneSnapshot("Test phone", "17", reading, 2_147_483_648, 4_294_967_296, 1_073_741_824, 12_884_901_888, "Wi-Fi")
        }
        override suspend fun flashlight(enabled: Boolean?): FlashlightResult {
            requests += enabled
            if (cancel) throw CancellationException("stop")
            return flash
        }
    }

    @Test
    fun batteryQueriesReadCurrentPhoneStateWithoutNeedingInference() {
        for (text in listOf("What's my battery level?", "whats my battery percentage", "check phone batery", "Can you check my battery please?", "/battery", "Is my phone charging?", "How much battery do I have left?", "check my phone battery")) {
            assertEquals(PhoneCommand.Battery, parsePhoneCommand(text), text)
        }
        assertEquals(PhoneCommand.Status, parsePhoneCommand("phone status"))
        assertEquals(PhoneCommand.Status, parsePhoneCommand("am I on Wi-Fi?"))
    }

    @Test
    fun flashlightCommandsSpecifyAnExplicitImmediateTargetState() {
        for (text in listOf("turn on flashlight", "turn the flashlight on", "Could you turn on the flashlight?", "switch on my torch", "Please turnon the flash light", "/flashlight on")) {
            assertEquals(PhoneCommand.Flashlight(true), parsePhoneCommand(text), text)
        }
        for (text in listOf("turn off the flashlight", "switch my torch off", "disable the flashlight", "/flashlight off")) {
            assertEquals(PhoneCommand.Flashlight(false), parsePhoneCommand(text), text)
        }
        assertEquals(PhoneCommand.Flashlight(null), parsePhoneCommand("is the flashlight on?"))
    }

    @Test
    fun quotedFutureNegatedAndCompoundRequestsCannotTriggerAnUnintendedAction() {
        for (text in listOf("turn on flashlight in 5 minutes", "turn on flashlight and send a message", "don't turn on the flashlight", "do not turn the torch on", "toggle the flashlight")) {
            assertEquals(PhoneCommand.InvalidFlashlight, parsePhoneCommand(text), text)
        }
        for (text in listOf("\"turn on the flashlight\"", "The website says turn on the flashlight", "remind me in 1 minute to turn on the flashlight", "Explain how a flashlight works", "remember that my battery is low")) {
            assertNull(parsePhoneCommand(text), text)
        }
    }

    @Test
    fun missingOrInvalidBatteryValuesAreNeverInvented() {
        assertEquals(50, batteryPercent(25, 50))
        assertEquals(100, batteryPercent(100, 100))
        assertEquals(0, batteryPercent(0, 100))
        for ((level, scale) in listOf(-1 to 100, 101 to 100, 1 to 0, 1 to -1)) assertNull(batteryPercent(level, scale))
    }

    @Test
    fun eachBatteryRequestGetsANewReadingAndPreservesActualChargingDetails() = runTest {
        val controls = Controls()
        val commands = PhoneCommands(AppSettings(MapSettings()), controls)
        val first = commands.execute(PhoneCommand.Battery)
        assertTrue("47%" in first && "Charging" in first && "USB" in first && "32.1" in first)
        controls.reading = controls.reading!!.copy(percent = 46, state = "Discharging", powerSource = null)
        val second = commands.execute(PhoneCommand.Battery)
        assertTrue("46%" in second && "Discharging" in second)
        assertFalse("USB" in second)
        assertEquals(2, controls.reads)
    }

    @Test
    fun toolSwitchesDisableBackendCallsIndependently() = runTest {
        val settings = AppSettings(MapSettings())
        val controls = Controls()
        val commands = PhoneCommands(settings, controls)
        for ((id, command) in listOf(PHONE_BATTERY to PhoneCommand.Battery, PHONE_STATUS to PhoneCommand.Status, PHONE_FLASHLIGHT to PhoneCommand.Flashlight(true))) {
            settings.setToolEnabled(id, false)
            assertTrue("disabled" in commands.execute(command))
        }
        assertEquals(0, controls.reads)
        assertEquals(0, controls.statusReads)
        assertTrue(controls.requests.isEmpty())
        settings.setToolEnabled(PHONE_BATTERY, true)
        assertTrue("47%" in commands.execute(PhoneCommand.Battery))
        assertTrue(controls.requests.isEmpty())
    }

    @Test
    fun unavailableOrUnconfirmedFlashlightStateCannotBecomeASuccess() = runTest {
        val controls = Controls()
        val commands = PhoneCommands(AppSettings(MapSettings()), controls)
        for (state in listOf(FlashlightState.UNKNOWN, FlashlightState.UNAVAILABLE)) {
            controls.flash = FlashlightResult(state)
            assertFalse("Flashlight is on." in commands.execute(PhoneCommand.Flashlight(true)))
        }
        controls.flash = FlashlightResult(FlashlightState.UNAVAILABLE, "Camera permission denied.")
        assertEquals("Camera permission denied.", commands.execute(PhoneCommand.Flashlight(true)))
        controls.flash = FlashlightResult(FlashlightState.ON)
        assertEquals("Flashlight is on.", commands.execute(PhoneCommand.Flashlight(true)))
        controls.flash = FlashlightResult(FlashlightState.OFF)
        assertEquals("Flashlight is off.", commands.execute(PhoneCommand.Flashlight(false)))
        commands.execute(PhoneCommand.Flashlight(null))
        assertEquals(null, controls.requests.last())
    }

    @Test
    fun invalidRequestsAndUnsupportedPlatformsCannotWriteHardwareState() = runTest {
        val controls = Controls()
        val commands = PhoneCommands(AppSettings(MapSettings()), controls)
        assertTrue("unchanged" in commands.execute(PhoneCommand.InvalidFlashlight))
        assertTrue(controls.requests.isEmpty())
        controls.supported = false
        assertTrue("Android" in commands.execute(PhoneCommand.Flashlight(true)))
        assertTrue(controls.requests.isEmpty())
    }

    @Test
    fun statusReportsMeasuredStorageRamAndConnectionWithExplicitUnknownValues() = runTest {
        val controls = Controls()
        val commands = PhoneCommands(AppSettings(MapSettings()), controls)
        val result = commands.execute(PhoneCommand.Status)
        assertTrue("2.0 GiB available of 4.0 GiB" in result)
        assertTrue("1.0 GiB available of 12.0 GiB" in result)
        assertTrue("Wi-Fi" in result)
        controls.reading = controls.reading!!.copy(percent = null)
        assertTrue("Battery: Unavailable" in commands.execute(PhoneCommand.Battery))
        controls.reading = null
        assertTrue("No battery level was guessed" in commands.execute(PhoneCommand.Battery))
    }

    @Test
    fun cancellationPropagatesWithoutAFalseSuccess() = runTest {
        val controls = Controls().apply { cancel = true }
        assertFailsWith<CancellationException> { PhoneCommands(AppSettings(MapSettings()), controls).execute(PhoneCommand.Flashlight(true)) }
    }
}
