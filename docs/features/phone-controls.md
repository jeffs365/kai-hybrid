# Phone controls

**Last verified:** 2026-10-23

Kai Hybrid on Android can read live phone status and control the rear flashlight. Direct text requests are handled by the app before model inference, Brave search, or DeepSeek consultation. They work while Local Model is selected and also when a cloud provider is selected, without making a cloud request for the command.

## Battery and phone status

Ask **What's my battery level?**, **Is my phone charging?**, or **/battery** for battery percentage, charging state, external power source, temperature when available, and battery-saver state.

Ask **Phone status** or **/phone status** for the device model, Android version, battery snapshot, available/total internal data storage, available/total RAM, and current connection type. Storage and RAM use GiB. Connection reports Wi-Fi, mobile data, VPN, or another transport and Android's internet-validation state; it does not collect Wi-Fi names, IP addresses, location, identifiers, app lists, or file contents.

Each request reads Android again and includes a timestamp and timezone. Unavailable readings are marked unavailable rather than guessed. These values are a snapshot, not continuous monitoring or a battery-life prediction. Results are saved in normal chat history; subsequent chat with a selected cloud provider follows that provider's ordinary history behavior.

## Flashlight

Supported requests include **Turn on the flashlight**, **Turn off the flashlight**, **Switch my torch off**, **/flashlight on**, **/flashlight off**, and **/flashlight status**.

Android asks for camera permission because the flash unit belongs to the camera hardware. This feature never opens a camera stream, captures a photo, or records video. Kai chooses a rear camera with a flash unit and waits for the camera service to report the actual torch state before confirming on/off. A state query does not change the light.

Missing permission, a busy camera, an unavailable flash unit, and missing confirmation produce a truthful explanation rather than a success claim. Camera callbacks are released after the operation. Android or another app may turn the light off, and it turns off if the process that enabled it exits. The app does not promise an independent flashlight service that survives every process exit.

Only explicit, immediate user commands change the light. Quoted instructions, web excerpts, photos, background task prompts, delayed requests, ambiguous toggles, and compound requests cannot trigger this path. Send one text-only command at a time. Existing reminders continue to post notifications; they do not execute flashlight commands embedded in a reminder message.

## Settings

Settings → Tools has independent switches for **Phone battery**, **Phone status**, and **Phone flashlight**. A disabled switch prevents the matching backend call. Android camera permission is requested only for flashlight operations; battery and basic status need no runtime camera permission. Network status uses Android's normal network-state permission.

These are app-managed controls. They are not arbitrary Android automation, root access, accessibility control, or model-generated shell commands. Other platforms retain their existing behavior.

## Key files

| File | Purpose |
|---|---|
| PhoneControls.kt in common data | Platform contract and structured device readings |
| PhoneCommands.kt in common data | Whole-message command parsing, tool gates, and measured responses |
| PhoneControls.android.kt in Android data | Battery, memory, storage, network, and camera-service torch integration |
| PermissionController.android.kt in Android tools | Android runtime camera permission |
| RemoteDataRepository.kt in common data | Direct command interception before inference and routing |

References: [Android battery monitoring](https://developer.android.com/training/monitoring-device-state/battery-monitoring), [Android flashlight control and state callbacks](https://developer.android.com/reference/android/hardware/camera2/CameraManager#setTorchMode(java.lang.String,%20boolean)).
