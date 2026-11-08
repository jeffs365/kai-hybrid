package com.inspiredandroid.kai.voice

object KokoroProfiles {
    const val DEFAULT = "kai-kokoro-bm_george"
    val voices = linkedMapOf(DEFAULT to "George · British English", "kai-kokoro-bm_daniel" to "Daniel · British English", "kai-kokoro-bm_lewis" to "Lewis · British English")
    fun validVoice(value: String) = value.takeIf { it in voices } ?: DEFAULT
}
