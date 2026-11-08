package com.inspiredandroid.kai.agent

import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The same finite definitions drive wire schemas and strict local validation. */
object AgentToolRegistry {
    val names = setOf("phone_battery", "phone_status", "phone_flashlight")
    val tools: List<Tool> = names.map { name ->
        object : Tool {
            override val schema = ToolSchema(
                name,
                when (name) {
                    "phone_battery" -> "Read current Android battery status. Never guess a battery reading."
                    "phone_status" -> "Read current phone model, battery, storage, RAM and connection type."
                    else -> "Read flashlight status, or set on/off only when the current user explicitly authorized that exact change."
                },
                if (name == "phone_flashlight") {
                    mapOf(
                        "state" to ParameterSchema(
                            "string",
                            "Exact target: on, off, or status.",
                            true,
                            buildJsonObject {
                                put("type", "string")
                                put("enum", buildJsonArray { for (value in listOf("on", "off", "status")) add(JsonPrimitive(value)) })
                            },
                        ),
                    )
                } else {
                    emptyMap()
                },
            )
            override suspend fun execute(args: Map<String, Any>): Any = error("Host execution context required")
        }
    }
    fun decode(name: String, arguments: String): PhoneAction? {
        if (name !in names || arguments.length > 256 || '\\' in arguments) return null
        val obj = runCatching { Json.parseToJsonElement(arguments) as? JsonObject }.getOrNull() ?: return null
        // Reject duplicate JSON keys as well as extras; do not let the parser silently keep the last.
        val expectedKeys = if (name == "phone_flashlight") setOf("state") else emptySet()
        if (obj.keys != expectedKeys) return null
        if (name != "phone_flashlight") return if (name == "phone_battery") PhoneAction.BATTERY else PhoneAction.STATUS
        if (Regex("\"state\"\\s*:").findAll(arguments).count() != 1) return null
        val state = obj["state"] as? JsonPrimitive ?: return null
        if (!state.isString) return null
        return when (state.content) {
            "on" -> PhoneAction.LIGHT_ON
            "off" -> PhoneAction.LIGHT_OFF
            "status" -> PhoneAction.LIGHT_STATUS
            else -> null
        }
    }
}
