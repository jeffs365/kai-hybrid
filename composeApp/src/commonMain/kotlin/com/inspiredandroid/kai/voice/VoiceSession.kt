package com.inspiredandroid.kai.voice

/** Main-thread session gate. A cancelled or completed recognizer cannot deliver a late transcript. */
class VoiceSession {
    private var generation = 0L
    private var scope = ""
    private var accepting = false
    fun begin(context: String): Long {
        generation++
        scope = context
        accepting = true
        return generation
    }
    fun cancel() {
        generation++
        accepting = false
    }
    fun isActive(token: Long, context: String) = accepting && generation == token && scope == context
    fun complete(token: Long, context: String, text: String): String? {
        if (!isActive(token, context)) return null
        accepting = false
        return text.trim().takeIf { it.isNotBlank() && it.length <= 12_000 && it.none { c -> c.isISOControl() && c !in "\n\t" } }
    }
}
