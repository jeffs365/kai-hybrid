package com.inspiredandroid.kai.agent

import com.inspiredandroid.kai.network.OpenAICompatibleEmptyResponseException
import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatResponseDto
import com.inspiredandroid.kai.ui.chat.History

fun interface AgentModelClient {
    suspend fun chat(history: List<History>): AgentModelTurn
}

/** Keep reasoning available for protocol pairing without presenting it as speech or final text. */
fun deepSeekAgentTurn(response: OpenAICompatibleChatResponseDto): AgentModelTurn {
    val message = response.choices.firstOrNull()?.message ?: throw OpenAICompatibleEmptyResponseException()
    require(message.role == "assistant") { "Unsupported message role" }
    require(message.toolCalls.orEmpty().all { it.type == "function" }) { "Unsupported tool response" }
    return AgentModelTurn(
        message.content.orEmpty(),
        message.toolCalls.orEmpty().map { AgentCall(it.id, it.function.name, it.function.arguments) },
        message.reasoningContent,
    )
}
