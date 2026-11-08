package com.inspiredandroid.kai.agent

import com.inspiredandroid.kai.data.*
import com.russhwolf.settings.MapSettings
import kotlin.test.*

class AgentPreferencesTest {
    @Test fun newInstallIsLocalOnlyAndSafeModeIsOptIn() {
        val settings = AppSettings(MapSettings())
        assertEquals(AgentPreferences(), settings.getAgentPreferences())
        assertFalse(settings.getAgentPreferences().enabled)
        assertFalse(settings.getAgentPreferences().notificationCloud)
        assertFalse(settings.getAgentPreferences().smsCloud)
    }

    @Test fun explicitPreferencesRoundTripWithToolsAndNoExecutionHistory() {
        val settings = AppSettings(MapSettings())
        val prefs = AgentPreferences(true, true, setOf("com.example.chat"), true, setOf("friend"), 14)
        settings.setAgentPreferences(prefs)
        val json = settings.exportToJson(emptyList(), setOf(ImportSection.TOOLS))
        val restored = AppSettings(MapSettings())
        assertEquals(0, restored.importFromJson(json, emptyList(), setOf(ImportSection.TOOLS)))
        assertEquals(prefs, restored.getAgentPreferences())
        assertTrue(ImportSection.TOOLS in detectExportableSections(json))
        assertFalse("executionEntry" in json.toString())
    }

    @Test fun malformedConfigurationFailsClosedAndInputsAreBounded() {
        val backing = MapSettings()
        backing.putString("safe_agent_preferences", "broken")
        val settings = AppSettings(backing)
        assertEquals(AgentPreferences(), settings.getAgentPreferences())
        settings.setAgentPreferences(AgentPreferences(notificationSources = setOf("bad source", "com.valid.app"), retentionDays = 999))
        assertEquals(setOf("com.valid.app"), settings.getAgentPreferences().notificationSources)
        assertEquals(30, settings.getAgentPreferences().retentionDays)
    }
}
