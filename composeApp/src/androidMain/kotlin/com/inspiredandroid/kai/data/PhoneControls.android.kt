package com.inspiredandroid.kai.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import com.inspiredandroid.kai.permissionQualifier
import com.inspiredandroid.kai.tools.AppPermission
import com.inspiredandroid.kai.tools.PermissionController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.java.KoinJavaComponent.get

actual fun createPhoneControls(): PhoneControls = AndroidPhoneControls(get(Context::class.java))

private class AndroidPhoneControls(private val context: Context) : PhoneControls {
    override val supported: Boolean = true
    private val flashlightMutex = Mutex()

    override suspend fun battery(): BatterySnapshot? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val percent = batteryPercent(intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1))
        val state = when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> "Unknown"
        }
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val source = when {
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "AC charger"
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "Wireless charger"
            plugged != 0 -> "External power"
            else -> null
        }
        val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE).takeIf { it in -500..1_500 }
        return BatterySnapshot(percent, state, source, temperature, context.getSystemService(PowerManager::class.java).isPowerSaveMode)
    }

    override suspend fun status(): PhoneSnapshot {
        val storage = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
        val memory = runCatching {
            ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        }.getOrNull()
        return PhoneSnapshot(
            model = Build.MANUFACTURER + " " + Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            battery = battery(),
            storageAvailableBytes = storage?.availableBytes,
            storageTotalBytes = storage?.totalBytes,
            memoryAvailableBytes = memory?.availMem,
            memoryTotalBytes = memory?.totalMem,
            connection = connection(),
        )
    }

    private fun connection(): String = try {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork
        val caps = network?.let { manager.getNetworkCapabilities(it) }
        when {
            caps == null -> "No active network"

            else -> {
                val type = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
                    else -> "Other network"
                }
                type + if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) " · internet validated by Android" else " · internet not validated by Android"
            }
        }
    } catch (_: SecurityException) {
        "Unavailable"
    }

    override suspend fun flashlight(enabled: Boolean?): FlashlightResult = flashlightMutex.withLock {
        val permission = get<PermissionController>(PermissionController::class.java, permissionQualifier(AppPermission.CAMERA))
        if (!permission.requestPermission()) return@withLock FlashlightResult(FlashlightState.UNAVAILABLE, "Camera permission is needed to control the flashlight. Allow it for Kai Hybrid in Android Settings → Apps → Kai Hybrid → Permissions, then try again.")
        val manager = context.getSystemService(CameraManager::class.java)
        var registered = false
        val states = Channel<FlashlightState>(Channel.CONFLATED)
        var cameraId: String? = null
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(id: String, active: Boolean) {
                if (id == cameraId) states.trySend(if (active) FlashlightState.ON else FlashlightState.OFF)
            }
            override fun onTorchModeUnavailable(id: String) {
                if (id == cameraId) states.trySend(FlashlightState.UNAVAILABLE)
            }
        }
        try {
            cameraId = manager.cameraIdList.firstOrNull {
                val characteristics = manager.getCameraCharacteristics(it)
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            val id = cameraId ?: return@withLock FlashlightResult(FlashlightState.UNAVAILABLE, "No rear-camera flashlight is available on this device.")
            manager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
            registered = true
            val initial = withTimeoutOrNull(3_000) { states.receive() } ?: return@withLock FlashlightResult(FlashlightState.UNKNOWN)
            if (enabled == null || initial == FlashlightState.UNAVAILABLE) return@withLock FlashlightResult(initial)
            val desired = if (enabled) FlashlightState.ON else FlashlightState.OFF
            if (initial == desired) return@withLock FlashlightResult(initial)
            manager.setTorchMode(id, enabled)
            val confirmed = withTimeoutOrNull(3_000) {
                var state = states.receive()
                while (state != desired && state != FlashlightState.UNAVAILABLE) state = states.receive()
                state
            } ?: FlashlightState.UNKNOWN
            FlashlightResult(confirmed)
        } catch (e: CancellationException) {
            throw e
        } catch (_: CameraAccessException) {
            FlashlightResult(FlashlightState.UNAVAILABLE, "Android could not access the flashlight. Close any app using the camera, then try again.")
        } catch (_: SecurityException) {
            FlashlightResult(FlashlightState.UNAVAILABLE, "Android denied flashlight access. Check Kai Hybrid's camera permission.")
        } catch (_: RuntimeException) {
            FlashlightResult(FlashlightState.UNKNOWN, "The flashlight operation could not be verified. Check the phone's Quick Settings.")
        } finally {
            if (registered) runCatching { manager.unregisterTorchCallback(callback) }
            states.close()
        }
    }
}
