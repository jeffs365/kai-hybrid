package com.inspiredandroid.kai.kokoro

import org.junit.Assert.*
import org.junit.Test

class SpeechPolicyTest {
    @Test fun sentenceChunksPreserveWordsAndStayBounded() {
        val text = "First sentence. " + "More words to read. ".repeat(100)
        val chunks = SpeechPolicy.chunks(text)!!
        assertTrue(chunks.all { it.length <= 220 })
        assertEquals(text.trim(), chunks.joinToString(" "))
    }

    @Test fun longWordsDoNotSplitUnicodeSurrogatePairs() {
        val text = "a".repeat(219) + "😀" + "b".repeat(300)
        val chunks = SpeechPolicy.chunks(text)!!
        assertEquals(text, chunks.joinToString(""))
        assertFalse(chunks.any { it.last().isHighSurrogate() || it.first().isLowSurrogate() })
    }

    @Test fun oversizeAndNulInputAreRefused() {
        assertNull(SpeechPolicy.chunks("x".repeat(4_001)))
        assertNull(SpeechPolicy.chunks("secret\u0000text"))
        assertTrue(SpeechPolicy.chunks(" ")!!.isEmpty())
    }

    @Test fun stopAndNewRequestInvalidateOldAudio() {
        var time = 0L
        val gate = SynthesisGate { time }
        val old = gate.begin()
        assertTrue(gate.active(old))
        gate.stop()
        assertFalse(gate.active(old))
        val next = gate.begin()
        assertFalse(gate.active(old))
        assertTrue(gate.active(next))
        time = 90_000
        assertFalse(gate.active(next))
    }

    @Test fun voiceIdsAreFiniteAndGeorgeIsDefault() {
        assertEquals(26, SpeechPolicy.speakers[SpeechPolicy.DEFAULT_VOICE])
        assertNull(SpeechPolicy.speakers["arbitrary-model-voice"])
    }
}
