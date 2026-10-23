package com.inspiredandroid.kai.data

data class BatterySnapshot(
    val percent: Int?,
    val state: String,
    val powerSource: String?,
    val temperatureTenthsC: Int?,
    val batterySaver: Boolean,
)

data class PhoneSnapshot(
    val model: String,
    val androidVersion: String,
    val battery: BatterySnapshot?,
    val storageAvailableBytes: Long?,
    val storageTotalBytes: Long?,
    val memoryAvailableBytes: Long?,
    val memoryTotalBytes: Long?,
    val connection: String,
)

enum class FlashlightState { ON, OFF, UNAVAILABLE, UNKNOWN }

data class FlashlightResult(val state: FlashlightState, val message: String? = null)

/** Direct Android operations; no model-generated action arguments or cloud requests. */
interface PhoneControls {
    val supported: Boolean
    suspend fun battery(): BatterySnapshot?
    suspend fun status(): PhoneSnapshot?

    /** Null reads state. Success requires a camera-service callback for the requested state. */
    suspend fun flashlight(enabled: Boolean?): FlashlightResult
}

expect fun createPhoneControls(): PhoneControls

class UnsupportedPhoneControls : PhoneControls {
    override val supported: Boolean = false
    override suspend fun battery(): BatterySnapshot? = null
    override suspend fun status(): PhoneSnapshot? = null
    override suspend fun flashlight(enabled: Boolean?): FlashlightResult = FlashlightResult(FlashlightState.UNAVAILABLE, "Phone controls are available on Android.")
}

internal fun batteryPercent(level: Int, scale: Int): Int? = if (scale <= 0 || level !in 0..scale) null else (level.toLong() * 100 / scale).toInt()
