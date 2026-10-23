@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.network.tools.ToolInfo
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToLong
import kotlin.time.Clock

internal const val PHONE_BATTERY = "phone_battery"
internal const val PHONE_STATUS = "phone_status"
internal const val PHONE_FLASHLIGHT = "phone_flashlight"
internal val phoneToolDefinitions = listOf(
    ToolInfo(PHONE_BATTERY, "Phone battery", "Read live battery charge, charging state, temperature, and battery saver."),
    ToolInfo(PHONE_STATUS, "Phone status", "Read device, battery, internal storage, available RAM, and connection type. No IP address or Wi-Fi name."),
    ToolInfo(PHONE_FLASHLIGHT, "Phone flashlight", "Turn the flashlight on or off, or read its actual state. Requires Android camera permission."),
)

internal sealed interface PhoneCommand {
    data object Battery : PhoneCommand
    data object Status : PhoneCommand
    data class Flashlight(val enabled: Boolean?) : PhoneCommand
    data object InvalidFlashlight : PhoneCommand
}

internal fun parsePhoneCommand(question: String?): PhoneCommand? {
    val input = question?.trim()?.lowercase()?.replace("’", "'")?.trimEnd('?', '.', '!') ?: return null
    var text = input.replace(Regex("""\s+"""), " ").replace("batery", "battery").replace("flash light", "flashlight")
    text = text.replace(Regex("""^(?:hey\s+kai[, ]+|kai[, ]+)"""), "")
    text = text.replace(Regex("""^(?:please\s+)?(?:(?:can|could|would|will)\s+you\s+)?(?:please\s+)?"""), "").trim()
    text = text.removeSuffix(" please").trim()
    text = text.replace(Regex("""^turnon """), "turn on ").replace(Regex("""^turnoff """), "turn off ")
    if (text in setOf("/battery", "/phone battery", "battery", "battery status", "battery level", "check battery", "check my battery", "check phone battery", "check my phone battery", "check the battery", "am i charging", "is my phone charging", "is the phone charging")) return PhoneCommand.Battery
    if (Regex("""^(?:what(?:'s|s| is)|how much is|check|show|tell me)\s+(?:my |the |your |this |my phone's |the phone's )?(?:phone )?battery(?: (?:level|percentage|percent|status|charge))?$""").matches(text) ||
        Regex("""^how much battery(?: (?:do i have|is left|have i got|is remaining))(?: left)?$""").matches(text)
    ) {
        return PhoneCommand.Battery
    }
    if (text in setOf("/phone", "/phone status", "phone status", "check phone status", "check my phone status", "show phone status", "device status", "check device status", "how much storage is left", "how much free storage do i have", "how much ram is available", "what is my connection", "am i on wifi", "am i on wi-fi")) return PhoneCommand.Status
    if (text in setOf("/flashlight status", "flashlight status", "torch status", "is my flashlight on", "is the flashlight on", "is my torch on", "is the torch on", "check flashlight", "check the flashlight", "check flashlight status")) return PhoneCommand.Flashlight(null)
    val direct = Regex("""^/?(?:flashlight|torch) (on|off)$""").matchEntire(text)
    val before = Regex("""^(?:turn|switch) (on|off) (?:the |my |phone )?(?:flashlight|torch)$""").matchEntire(text)
    val after = Regex("""^(?:turn|switch) (?:the |my |phone )?(?:flashlight|torch) (on|off)$""").matchEntire(text)
    val match = direct ?: before ?: after
    if (match != null) return PhoneCommand.Flashlight(match.groupValues[1] == "on")
    if (Regex("""^(?:enable|activate) (?:the |my )?(?:flashlight|torch)$""").matches(text)) return PhoneCommand.Flashlight(true)
    if (Regex("""^(?:disable|deactivate) (?:the |my )?(?:flashlight|torch)$""").matches(text)) return PhoneCommand.Flashlight(false)
    // Unsupported durations, negation and compound instructions never become a partial action.
    if ((
            Regex("""^(?:/flashlight|/torch|turn |switch |enable |disable |activate |deactivate |toggle |do not turn |don't turn )""").containsMatchIn(text) &&
                Regex("""\b(?:flashlight|torch)\b""").containsMatchIn(text)
            ) ||
        text in setOf("flashlight", "torch", "/flashlight", "/torch")
    ) {
        return PhoneCommand.InvalidFlashlight
    }
    return null
}

internal const val PHONE_CONTROL_GUIDANCE =
    "Phone controls are managed by the app. Direct requests such as 'What is my battery level?', " +
        "'Phone status', 'Turn on the flashlight', and 'Turn off the flashlight' are handled before inference. " +
        "Never invent current device readings or claim a phone action completed yourself. " +
        "For a fresh reading or an action, direct the user to these explicit commands. " +
        "Only an app result can confirm flashlight state. You cannot schedule flashlight actions or control arbitrary phone settings."

internal fun guardLocalPhoneClaims(response: String): String {
    val action = Regex("""\bi(?:['’]ve| have| just|['’]ll| will)?\s+(?:turned|turn|switched|switch|set|enabled|disabled)\b[^\n.!?]{0,60}\b(?:flashlight|torch)\b""", RegexOption.IGNORE_CASE)
    val flashState = Regex("""^\s*(?:(?:your|the|my) )?(?:flashlight|torch) is (?:now |currently )?(?:on|off)[.!]?\s*$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    val batteryReading = Regex("""^\s*(?:your |my |the phone's )?battery(?: level| percentage| charge)?(?: is(?: currently| now)?(?: at)?|:)\s*\d+(?:\.\d+)?\s*%""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    return if (action.containsMatchIn(response) || flashState.containsMatchIn(response) || batteryReading.containsMatchIn(response)) {
        "A fresh phone reading or action needs an app-managed request. Use /battery, /phone status, /flashlight on, /flashlight off, or /flashlight status. I have not verified a new device reading or action for this response."
    } else {
        response
    }
}

internal class PhoneCommands(private val settings: AppSettings, private val controls: PhoneControls) {
    suspend fun execute(command: PhoneCommand): String {
        if (!controls.supported) return "Phone controls are available in Kai Hybrid on Android."
        val id = when (command) {
            PhoneCommand.Battery -> PHONE_BATTERY
            PhoneCommand.Status -> PHONE_STATUS
            else -> PHONE_FLASHLIGHT
        }
        if (!settings.isToolEnabled(id)) return "This phone control is disabled. Enable " + phoneToolDefinitions.first { it.id == id }.name + " in Settings → Tools."
        return when (command) {
            PhoneCommand.InvalidFlashlight -> "Flashlight unchanged. Use 'Turn on the flashlight', 'Turn off the flashlight', or /flashlight status. Send one immediate action at a time."

            PhoneCommand.Battery -> controls.battery()?.let { "**Phone battery**\n\n" + renderBattery(it) + timestamp() }
                ?: "Android could not provide a battery reading. No battery level was guessed."

            PhoneCommand.Status -> controls.status()?.let { status ->
                buildString {
                    append("**Phone status**\n\n" + status.model + " · Android " + status.androidVersion)
                    status.battery?.let { append("\n\n" + renderBattery(it)) }
                    append("\n\nInternal data storage: " + size(status.storageAvailableBytes) + " available of " + size(status.storageTotalBytes))
                    append("\nRAM: " + size(status.memoryAvailableBytes) + " available of " + size(status.memoryTotalBytes))
                    append("\nConnection: " + status.connection)
                    append(timestamp())
                }
            } ?: "Android could not provide phone status."

            is PhoneCommand.Flashlight -> {
                val result = controls.flashlight(command.enabled)
                result.message ?: when (result.state) {
                    FlashlightState.ON -> "Flashlight is on."
                    FlashlightState.OFF -> "Flashlight is off."
                    FlashlightState.UNAVAILABLE -> "The flashlight is unavailable. The camera or light may be busy."
                    FlashlightState.UNKNOWN -> "Android did not confirm the flashlight state. Check the phone's Quick Settings."
                }
            }
        }
    }

    private fun renderBattery(battery: BatterySnapshot): String = buildString {
        append("Battery: " + (battery.percent?.takeIf { it in 0..100 }?.let { "$it%" } ?: "Unavailable"))
        append("\nState: " + battery.state)
        battery.powerSource?.let { append("\nPower source: " + it) }
        battery.temperatureTenthsC?.let { append("\nTemperature: " + it / 10.0 + " °C") }
        append("\nBattery saver: " + if (battery.batterySaver) "On" else "Off")
    }

    private fun timestamp(): String = "\n\nRead from Android at " + Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) + " (" + TimeZone.currentSystemDefault().id + ")."
    private fun size(bytes: Long?): String = if (bytes == null || bytes < 0) "unavailable" else ((bytes / 1_073_741_824.0 * 10).roundToLong() / 10.0).toString() + " GiB"
}
