package com.inspiredandroid.kai.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

const val KOKORO_ENGINE_PACKAGE = "com.inspiredandroid.kai.kokoro"
const val LEGACY_KOKORO_ENGINE_PACKAGE = "com.k2fsa.sherpa.onnx.tts.engine"

/** Same signing key, explicit protocol and no network/microphone permission. */
fun trustedKokoroEngine(context: Context): Boolean = runCatching {
    val pm = context.packageManager
    val info = pm.getPackageInfo(KOKORO_ENGINE_PACKAGE, PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA)
    val version = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    pm.checkSignatures(context.packageName, KOKORO_ENGINE_PACKAGE) == PackageManager.SIGNATURE_MATCH &&
        version >= 20261109 && info.applicationInfo?.metaData?.getInt("kai.kokoro.protocol") == 2 &&
        info.requestedPermissions.orEmpty().none { it in setOf(Manifest.permission.INTERNET, Manifest.permission.RECORD_AUDIO) }
}.getOrDefault(false)
