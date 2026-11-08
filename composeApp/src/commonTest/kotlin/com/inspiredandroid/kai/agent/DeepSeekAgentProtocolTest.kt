package com.inspiredandroid.kai.agent

import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatResponseDto
import kotlinx.serialization.json.Json
import kotlin.test.*

class DeepSeekAgentProtocolTest {
    private val wire = """{"choices":[{"message":{"role":"assistant","content":null,"reasoning_content":"private internal reasoning","tool_calls":[{"id":"call-a","type":"function","function":{"name":"phone_battery","arguments":"{}"}}]}}]}"""
    private fun response(text: String) = Json.decodeFromString<OpenAICompatibleChatResponseDto>(text)

    @Test fun actualWireFixtureProducesTypedCallAndRetainsReasoningOnlyForPairing() {
        val turn = deepSeekAgentTurn(response(wire))
        assertEquals("", turn.text)
        assertEquals("private internal reasoning", turn.reasoning)
        assertEquals(AgentCall("call-a", "phone_battery", "{}"), turn.calls.single())
    }

    @Test fun invalidRolesAndToolTypesAreRejected() {
        assertFailsWith<IllegalArgumentException> { deepSeekAgentTurn(response(wire.replace("assistant", "user"))) }
        assertFailsWith<IllegalArgumentException> { deepSeekAgentTurn(response(wire.replace("\"type\":\"function\"", "\"type\":\"other\""))) }
    }

    @Test fun reasoningOnlyResponseDoesNotBecomeUserFacingText() {
        val turn = deepSeekAgentTurn(response("""{"choices":[{"message":{"role":"assistant","reasoning_content":"private internal reasoning"}}]}"""))
        assertEquals("", turn.text)
        assertTrue(turn.calls.isEmpty())
    }
}
