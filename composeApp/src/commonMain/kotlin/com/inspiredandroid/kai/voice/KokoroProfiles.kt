package com.inspiredandroid.kai.voice

object KokoroProfiles {
    const val DEFAULT = "kai-kokoro-bm_george"
    val voices = linkedMapOf(DEFAULT to "George · British English", "kai-kokoro-bm_daniel" to "Daniel · British English", "kai-kokoro-bm_lewis" to "Lewis · British English")

    // Protocol 2 publishes a unique private-use locale tag for each voice. The
    // multiplatform TTS library exposes display labels rather than native IDs.
    fun languageTag(value: String): String? = when (value) {
        DEFAULT -> "en-GB-x-kai-george"
        "kai-kokoro-bm_daniel" -> "en-GB-x-kai-daniel"
        "kai-kokoro-bm_lewis" -> "en-GB-x-kai-lewis"
        else -> null
    }
    fun validVoice(value: String) = value.takeIf { it in voices } ?: DEFAULT
}
