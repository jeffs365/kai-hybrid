package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.data.AppSettings
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BraveSearchToolTest {
    private val oneResult = """{"web":{"results":[{"title":"Example","url":"https://example.com/","description":"Source excerpt"}]}}"""

    @Test
    fun `missing key disabled tool and invalid queries cannot reach network`() = runTest {
        val settings = AppSettings(MapSettings())
        assertTrue(settings.getBraveSearchApiKey().isBlank())
        assertFalse(settings.isBraveSearchAutomatic())
        var calls = 0
        val request: suspend (String, String) -> BraveHttpResponse = { _, _ ->
            calls++
            BraveHttpResponse(200, oneResult)
        }
        for ((enabled, key, query) in listOf(Triple(false, "key", "query"), Triple(true, "", "query"), Triple(true, "key", ""), Triple(true, "key", "x".repeat(601)), Triple(true, "key", List(76) { "word" }.joinToString(" ")))) {
            val tool = BraveSearchTool(enabled, key, request)
            assertTrue(tool.execute(mapOf("query" to query)).toString().contains("success=false"))
        }
        assertEquals(0, calls)
    }

    @Test
    fun `one turn reserves one network attempt across concurrent calls`() = runTest {
        var calls = 0
        val tool = BraveSearchTool(true, "private-key") { query, key ->
            assertEquals("query", query)
            assertEquals("private-key", key)
            calls++
            delay(10)
            BraveHttpResponse(200, oneResult)
        }
        (1..8).map { async { tool.execute(mapOf("query" to "query", "context" to "unrelated private history")) } }.awaitAll()
        assertEquals(1, calls)
        assertEquals(1, tool.results.size)
        assertFalse(tool.context().contains("private-key"))
        assertFalse(tool.context().contains("unrelated"))
    }

    @Test
    fun `search results are limited deduplicated and safe links remain available`() = runTest {
        val entries = listOf(
            """{"title":"Unsafe","url":"javascript:alert(1)","description":"bad"}""",
            """{"title":"Credentials","url":"https://user:pass@example.com","description":"bad"}""",
        ) + (1..12).map { """{"title":"<b>Title [$it]</b>","url":"https://example.com/$it","description":"${"x".repeat(1000)}"}""" } + listOf("""{"title":"Duplicate","url":"https://example.com/1"}""")
        val tool = BraveSearchTool(true, "key") { _, _ -> BraveHttpResponse(200, "{\"web\":{\"results\":[${entries.joinToString(",")}]}}") }
        tool.execute(mapOf("query" to "query"))
        assertEquals(5, tool.results.size)
        assertTrue(tool.results.all { it.description.length <= 700 && !it.title.contains("<b>") })
        assertTrue(tool.context().length < 5000)
        assertTrue(tool.context().contains("Source: https://example.com/1"))
        assertTrue(tool.sourceLinks().contains("https://example.com/1"))
        assertFalse(tool.sourceLinks().contains("javascript:"))
        assertFalse(tool.sourceLinks().contains("user:pass"))
    }

    @Test
    fun `provider errors and exceptions are redacted and never retried`() = runTest {
        for (status in listOf(401, 403, 429, 500)) {
            var calls = 0
            val tool = BraveSearchTool(true, "secret-key") { _, _ ->
                calls++
                BraveHttpResponse(status, "secret-key")
            }
            val result = tool.execute(mapOf("query" to "query")).toString()
            assertFalse(result.contains("secret-key"))
            tool.execute(mapOf("query" to "retry"))
            assertEquals(1, calls)
        }
        val exception = BraveSearchTool(true, "secret-key") { _, _ -> error("secret-key request details") }
        assertFalse(exception.execute(mapOf("query" to "query")).toString().contains("secret-key"))
    }

    @Test
    fun `timeout is bounded and user cancellation propagates`() = runTest {
        val timeout = BraveSearchTool(true, "key") { _, _ -> awaitCancellation() }
        assertTrue(timeout.execute(mapOf("query" to "query")).toString().contains("timed out"))
        val cancelled = BraveSearchTool(true, "key") { _, _ -> throw CancellationException("stop") }
        assertFailsWith<CancellationException> { cancelled.execute(mapOf("query" to "query")) }
    }

    @Test
    fun `empty malformed and oversized responses cannot become fabricated evidence`() = runTest {
        val empty = BraveSearchTool(true, "key") { _, _ -> BraveHttpResponse(200, "{}") }
        assertTrue(empty.execute(mapOf("query" to "query")).toString().contains("success=true"))
        assertTrue(empty.results.isEmpty())
        for (body in listOf("bad-json", "x".repeat(512_001))) {
            val tool = BraveSearchTool(true, "key") { _, _ -> BraveHttpResponse(200, body) }
            assertTrue(tool.execute(mapOf("query" to "query")).toString().contains("success=false"))
            assertTrue(tool.results.isEmpty())
        }
    }

    @Test
    fun `only deliberate slash search commands bypass routing`() {
        assertEquals("current weather", BraveSearchTool.explicitQuery("/SEARCH current weather"))
        assertEquals("", BraveSearchTool.explicitQuery("/search"))
        assertEquals(null, BraveSearchTool.explicitQuery("please search"))
        assertEquals(null, BraveSearchTool.explicitQuery("/searching"))
    }

    @Test
    fun `automatic routing accepts exact decisions and bounded queries only`() {
        assertEquals(LocalRoute("search", "latest news"), LocalAssistantRouting.parse("```json\n{\"route\":\"search\",\"query\":\"latest news\"}\n```"))
        assertEquals(LocalRoute("deepseek"), LocalAssistantRouting.parse("{\"route\":\"deepseek\"}"))
        for (text in listOf("search for news", "{\"route\":\"search\"}", "{\"route\":\"search\",\"query\":\"\"}", "{\"route\":\"search\",\"query\":\"news\",\"key\":\"invented\"}", "{\"route\":\"search\",\"query\":true}", "{\"route\":\"search\",\"query\":\"${"x".repeat(601)}\"}")) {
            assertEquals(LocalRoute("local"), LocalAssistantRouting.parse(text), text)
        }
        assertFalse(LocalAssistantRouting.guidance(search = true, deepSeek = false).contains("\"route\":\"deepseek\""))
    }
}
