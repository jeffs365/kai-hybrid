package com.inspiredandroid.kai.voice

import kotlin.test.*

class VoiceSessionTest {
    @Test fun finalTranscriptDeliveredOnceAndNeverAutomaticallySubmitted() {
        val gate = VoiceSession()
        val id = gate.begin("chat-a:deepseek")
        assertEquals("Turn on the flashlight", gate.complete(id, "chat-a:deepseek", "  Turn on the flashlight  "))
        assertNull(gate.complete(id, "chat-a:deepseek", "duplicate"))
    }

    @Test fun cancellationRejectsLateResultsAndNextSessionStillWorks() {
        val gate = VoiceSession()
        val old = gate.begin("a")
        gate.cancel()
        assertNull(gate.complete(old, "a", "late result"))
        val next = gate.begin("a")
        assertEquals("fresh", gate.complete(next, "a", "fresh"))
    }

    @Test fun providerOrConversationChangeCannotReceiveAnOldTranscript() {
        val gate = VoiceSession()
        val old = gate.begin("a:local")
        assertNull(gate.complete(old, "b:deepseek", "wrong context"))
        val next = gate.begin("b:deepseek")
        assertNull(gate.complete(old, "a:local", "stale"))
        assertEquals("new", gate.complete(next, "b:deepseek", "new"))
    }

    @Test fun offlineLanguageReadinessUsesTheReportedInstalledSupportedAndPendingLists() {
        assertEquals(OfflineLanguageStatus.INSTALLED, offlineLanguageStatus("en-US", listOf("en_us"), emptyList(), emptyList()))
        assertEquals(OfflineLanguageStatus.DOWNLOADABLE, offlineLanguageStatus("en-GB", listOf("en-US"), listOf("en-GB"), emptyList()))
        assertEquals(OfflineLanguageStatus.DOWNLOADABLE, offlineLanguageStatus("fil-PH", listOf("en-US"), emptyList(), listOf("fil-PH")))
    }

    @Test fun unsupportedLocalesDoNotBorrowAnotherRegionsModelOrSuggestAFalseDownload() {
        assertEquals(OfflineLanguageStatus.UNSUPPORTED, offlineLanguageStatus("en-PH", listOf("en-US"), listOf("en-GB"), emptyList()))
        assertEquals(OfflineLanguageStatus.UNSUPPORTED, offlineLanguageStatus("fil-PH", listOf("en-US"), listOf("en-GB"), emptyList()))
    }

    @Test fun kokoroVoiceAndSpeedAreBoundedAndPersistWithoutArbitraryVoiceNames() {
        val backing = com.russhwolf.settings.MapSettings()
        val settings = com.inspiredandroid.kai.data.AppSettings(backing)
        assertEquals(KokoroProfiles.DEFAULT, settings.getKokoroVoice())
        assertEquals(0.95f, settings.getKokoroSpeed())
        settings.setKokoroVoice("kai-kokoro-bm_daniel")
        settings.setKokoroSpeed(1.05f)
        val restored = com.inspiredandroid.kai.data.AppSettings(backing)
        assertEquals("kai-kokoro-bm_daniel", restored.getKokoroVoice())
        assertEquals(1.05f, restored.getKokoroSpeed())
        assertFailsWith<IllegalArgumentException> { settings.setKokoroVoice("arbitrary-engine-voice") }
        assertFailsWith<IllegalArgumentException> { settings.setKokoroSpeed(Float.NaN) }
        assertFailsWith<IllegalArgumentException> { settings.setKokoroSpeed(5f) }
    }

    @Test fun engineChoiceIsDeviceLocalAndRestrictedToKnownEngines() {
        val backing = com.russhwolf.settings.MapSettings()
        val settings = com.inspiredandroid.kai.data.AppSettings(backing)
        assertEquals("system", settings.getLocalVoiceEngine())
        settings.setLocalVoiceEngine("kokoro")
        assertEquals("kokoro", com.inspiredandroid.kai.data.AppSettings(backing).getLocalVoiceEngine())
        assertFailsWith<IllegalArgumentException> { settings.setLocalVoiceEngine("arbitrary.package") }
    }

    @Test fun emptyOversizeAndControlCharacterResultsAreRejected() {
        val gate = VoiceSession()
        for (bad in listOf("   ", "x".repeat(12_001), "bad\u0000text")) {
            assertNull(gate.complete(gate.begin("a"), "a", bad))
        }
    }
}
