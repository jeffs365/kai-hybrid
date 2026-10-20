package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeepSeekConsultationToolTest {
    @Test
    fun `cloud access is opt in and disabled configuration makes no request`() = runTest {
        val settings = AppSettings(MapSettings())
        assertFalse(settings.isDeepSeekConsultationEnabled())
        settings.setDeepSeekConsultationEnabled(true)
        assertTrue(settings.isDeepSeekConsultationEnabled())
        var calls = 0
        for ((enabled, configured) in listOf(false to true, true to false)) {
            val tool = DeepSeekConsultationTool(enabled, configured) { _, _ ->
                calls++
                "answer"
            }
            assertTrue(tool.execute(mapOf("question" to "hello")).toString().contains("success=false"))
            assertFalse(tool.attempted)
        }
        assertEquals(0, calls)
    }

    @Test
    fun `concurrent attempts share one call budget and a fresh turn gets its own budget`() = runTest {
        var calls = 0
        fun turn() = DeepSeekConsultationTool(true, true) { _, _ ->
            calls++
            delay(10)
            "advice"
        }
        val first = turn()
        (1..8).map { async { first.execute(mapOf("question" to "help")) } }.awaitAll()
        assertEquals(1, calls)
        assertEquals("advice", first.answer)
        turn().execute(mapOf("question" to "next"))
        assertEquals(2, calls)
    }

    @Test
    fun `only supplied question and context are forwarded and oversized input is rejected`() = runTest {
        val captured = mutableListOf<Pair<String, String>>()
        val tool = DeepSeekConsultationTool(true, true) { q, c ->
            captured += q to c
            "answer"
        }
        tool.execute(mapOf("question" to " explain ", "context" to " code ", "apiKey" to "do-not-forward"))
        assertEquals(listOf("explain" to "code"), captured)
        val oversized = DeepSeekConsultationTool(true, true) { _, _ -> error("must not send") }
        oversized.execute(mapOf("question" to "x".repeat(6000), "context" to "x"))
        assertFalse(oversized.attempted)
        val empty = DeepSeekConsultationTool(true, true) { _, _ -> error("must not send") }
        empty.execute(mapOf("question" to " "))
        assertFalse(empty.attempted)
    }

    @Test
    fun `network failure cannot leak exception details or cause a retry`() = runTest {
        var calls = 0
        val tool = DeepSeekConsultationTool(true, true) { _, _ ->
            calls++
            error("secret-key-request-details")
        }
        val first = tool.execute(mapOf("question" to "help")).toString()
        assertFalse(first.contains("secret-key"))
        tool.execute(mapOf("question" to "retry"))
        assertEquals(1, calls)
        assertNull(tool.answer)
        assertTrue(tool.label("local answer").contains("DeepSeek unavailable"))
    }

    @Test
    fun `timeout is bounded and cancellation propagates`() = runTest {
        val timeout = DeepSeekConsultationTool(true, true) { _, _ -> awaitCancellation() }
        assertTrue(timeout.execute(mapOf("question" to "help")).toString().contains("timed out"))
        val cancelled = DeepSeekConsultationTool(true, true) { _, _ -> throw CancellationException("stop") }
        assertFailsWith<CancellationException> { cancelled.execute(mapOf("question" to "help")) }
    }

    @Test
    fun `response size and cloud attribution are bounded`() = runTest {
        val tool = DeepSeekConsultationTool(true, true) { _, _ -> "x".repeat(9000) }
        val result = tool.execute(mapOf("question" to "help")).toString()
        assertEquals(6000, tool.answer?.length)
        assertTrue(result.contains("truncated=true"))
        assertTrue(tool.label("answer").startsWith("**Cloud-assisted"))
        val unused = DeepSeekConsultationTool(true, true) { _, _ -> "answer" }
        assertEquals("local", unused.label("local"))
    }

    @Test
    fun `explicit command is deliberate and case insensitive`() {
        assertEquals("explain this", DeepSeekConsultationTool.explicitQuestion("/DeepSeek explain this"))
        assertEquals("", DeepSeekConsultationTool.explicitQuestion("/deepseek"))
        assertNull(DeepSeekConsultationTool.explicitQuestion("What is DeepSeek?"))
        assertNull(DeepSeekConsultationTool.explicitQuestion("/deepseeker hello"))
    }

    @Test
    fun `text router accepts only an exact cloud decision`() {
        assertTrue(DeepSeekConsultationTool.requestsCloud(" {\"route\":\"deepseek\"} \n"))
        assertTrue(DeepSeekConsultationTool.requestsCloud("```json\n{\"route\":\"deepseek\"}\n```"))
        listOf(
            "{\"route\":\"local\"}",
            "consult_deepseek: help",
            "Here is the decision: {\"route\":\"deepseek\"}",
            "{\"route\":\"deepseek\",\"question\":\"invented\"}",
            "{\"route\":true}",
            "{\"route\":null}",
            "[\"deepseek\"]",
            "{malformed",
        ).forEach { assertFalse(DeepSeekConsultationTool.requestsCloud(it), it) }
    }

    @Test
    fun `consultation wire payload has token cap thinking disabled and no tools`() {
        val request = OpenAICompatibleChatRequestDto(
            messages = listOf(OpenAICompatibleChatRequestDto.Message("user", JsonPrimitive("question"))),
            model = "deepseek-flash",
            maxTokens = DeepSeekConsultationTool.MAX_OUTPUT_TOKENS,
            thinking = OpenAICompatibleChatRequestDto.Thinking("disabled"),
        )
        val json = Json.encodeToString(request)
        assertTrue(json.contains("\"max_tokens\":2048"))
        assertTrue(json.contains("\"thinking\":{\"type\":\"disabled\"}"))
        assertFalse(json.contains("\"tools\""))
        assertFalse(Json.encodeToString(OpenAICompatibleChatRequestDto(emptyList())).contains("max_tokens"))
    }
}
