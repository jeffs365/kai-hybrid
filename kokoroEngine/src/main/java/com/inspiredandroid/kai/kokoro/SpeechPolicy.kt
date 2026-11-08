// SPDX-License-Identifier: GPL-3.0-or-later
package com.inspiredandroid.kai.kokoro

import java.util.concurrent.atomic.AtomicLong

object SpeechPolicy {
    val speakers = mapOf("kai-kokoro-bm_george" to 26, "kai-kokoro-bm_daniel" to 24, "kai-kokoro-bm_lewis" to 27)
    const val DEFAULT_VOICE = "kai-kokoro-bm_george"
    fun languageTag(name: String): String = when (name) {
        DEFAULT_VOICE -> "en-GB-x-kai-george"
        "kai-kokoro-bm_daniel" -> "en-GB-x-kai-daniel"
        "kai-kokoro-bm_lewis" -> "en-GB-x-kai-lewis"
        else -> error("unsupported_voice")
    }
    fun chunks(input: String, limit: Int = 220): List<String>? {
        if (input.length > 4_000 || input.any { it == '\u0000' }) return null
        var rest = input.trim().replace(Regex("\\s+"), " ")
        val out = mutableListOf<String>()
        while (rest.isNotEmpty()) {
            if (out.size >= 32) return null
            if (rest.length <= limit) {
                out += rest
                break
            }
            val prefix = rest.take(limit)
            var split = Regex("[.!?;:] ").findAll(prefix).lastOrNull()?.range?.last?.plus(1)
                ?: prefix.lastIndexOf(' ').takeIf { it > 0 }
                ?: limit
            if (split > 0 && rest[split - 1].isHighSurrogate()) split--
            out += rest.take(split).trim()
            rest = rest.drop(split).trimStart()
        }
        return out
    }
}

class SynthesisGate(private val now: () -> Long) {
    data class Ticket(val id: Long, val deadline: Long)
    private val sequence = AtomicLong()
    fun begin() = Ticket(sequence.incrementAndGet(), now() + 90_000)
    fun active(ticket: Ticket) = sequence.get() == ticket.id && now() < ticket.deadline
    fun stop() {
        sequence.incrementAndGet()
    }
}
