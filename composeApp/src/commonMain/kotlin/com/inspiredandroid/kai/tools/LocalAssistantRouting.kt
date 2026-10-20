package com.inspiredandroid.kai.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class LocalRoute(val route: String, val query: String? = null)

object LocalAssistantRouting {
    fun guidance(search: Boolean, deepSeek: Boolean): String = buildString {
        append("Classify the next user message. Do not answer it. Return only one JSON object. Choose {\"route\":\"local\"} for ordinary conversation, arithmetic, rewriting, phone actions, or questions needing private history or credentials. ")
        if (search) append("For current events, latest information, prices, weather, or an explicit request to search or look something up, choose {\"route\":\"search\",\"query\":\"short standalone search terms\"}. Search takes priority over expert advice when fresh information is needed. Query must exclude credentials and unrelated personal information, with at most 600 characters and 75 words. ")
        if (deepSeek) append("If the user explicitly asks for DeepSeek, or asks for difficult multi-step reasoning or complex coding that does not need fresh web information, choose {\"route\":\"deepseek\"}. ")
        append("Use only the routes described above. No explanations. No history or memories will be uploaded.")
    }

    fun parse(response: String): LocalRoute = runCatching {
        val trimmed = response.trim()
        val text = if (trimmed.startsWith("```json\n") && trimmed.endsWith("\n```")) trimmed.removePrefix("```json\n").removeSuffix("\n```") else trimmed
        val obj = Json.parseToJsonElement(text) as? JsonObject ?: return@runCatching LocalRoute("local")
        fun string(key: String): String? = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        when {
            obj.keys == setOf("route") && string("route") in listOf("local", "deepseek") -> LocalRoute(string("route")!!)
            obj.keys == setOf("route", "query") && string("route") == "search" && string("query")?.let(BraveSearchTool::validQuery) == true -> LocalRoute("search", string("query"))
            else -> LocalRoute("local")
        }
    }.getOrDefault(LocalRoute("local"))
}
