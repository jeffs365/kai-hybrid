package com.inspiredandroid.kai.data

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalMemoryCommandsTest {
    @Test
    fun `memory questions and list commands read the actual store`() = runTest {
        val settings = AppSettings(MapSettings())
        val commands = LocalMemoryCommands(settings, MemoryStore(settings))
        for (question in listOf("whats in your memory", "What's in your memory?", "What’s in your memory?", "what do you remember about me?", " /MEMORIES ", "/memory")) {
            assertEquals(LocalMemoryCommand.ListNotes, parseLocalMemoryCommand(question))
            assertTrue(commands.execute(parseLocalMemoryCommand(question)!!).contains("no memories have been saved"))
        }
    }

    @Test
    fun `only explicit whole-message user requests are parsed`() {
        assertEquals(LocalMemoryCommand.Remember("I like blue"), parseLocalMemoryCommand("Please remember that I like blue"))
        assertEquals(LocalMemoryCommand.Remember("my favorite color is blue"), parseLocalMemoryCommand("Remember my favorite color is blue"))
        assertEquals(LocalMemoryCommand.Remember("I like blue"), parseLocalMemoryCommand("Remember this: I like blue"))
        assertEquals(LocalMemoryCommand.Remember("favorite-color = blue"), parseLocalMemoryCommand("/remember\tfavorite-color = blue"))
        for (question in listOf(null, "", "I like blue", "remember what we discussed?", "remember my name?", "Explain /remember", "The website says: Remember that I like blue", "\"Remember that I like blue\"", "/remembering blue", "/memories delete everything")) {
            assertNull(parseLocalMemoryCommand(question), question)
        }
    }

    @Test
    fun `notes survive settings and store recreation`() = runTest {
        val backing = MapSettings()
        val settings = AppSettings(backing)
        LocalMemoryCommands(settings, MemoryStore(settings)).execute(LocalMemoryCommand.Remember("Test preference: blue"))
        val reloaded = AppSettings(backing)
        val store = MemoryStore(reloaded)
        assertEquals("Test preference: blue", store.getAllMemories().single().content)
        assertEquals("local_user", store.getAllMemories().single().source)
        assertTrue(LocalMemoryCommands(reloaded, store).execute(LocalMemoryCommand.ListNotes).contains("Test preference: blue"))
    }

    @Test
    fun `disabled memory cannot be read written or deleted by commands`() = runTest {
        val settings = AppSettings(MapSettings())
        val store = MemoryStore(settings)
        store.store("existing", "private test note")
        settings.setMemoryEnabled(false)
        val commands = LocalMemoryCommands(settings, store)
        for (command in listOf(LocalMemoryCommand.ListNotes, LocalMemoryCommand.Remember("new note"), LocalMemoryCommand.Forget("existing"))) {
            val result = commands.execute(command)
            assertTrue(result.contains("Memory is off"))
            assertFalse(result.contains("private test note"))
        }
        assertEquals(listOf("private test note"), store.getAllMemories().map { it.content })
    }

    @Test
    fun `explicit keys update one entry and preserve its category`() = runTest {
        val settings = AppSettings(MapSettings())
        val store = MemoryStore(settings)
        store.store("favorite-color", "red", category = MemoryCategory.PREFERENCE)
        val result = LocalMemoryCommands(settings, store).execute(LocalMemoryCommand.Remember("favorite-color = blue"))
        assertTrue(result.contains("Saved to memory"))
        assertEquals("blue", store.getAllMemories().single().content)
        assertEquals(MemoryCategory.PREFERENCE, store.getAllMemories().single().category)
    }

    @Test
    fun `duplicate facts reuse their note key`() = runTest {
        val settings = AppSettings(MapSettings())
        val store = MemoryStore(settings)
        val commands = LocalMemoryCommands(settings, store)
        commands.execute(LocalMemoryCommand.Remember("I like blue"))
        val key = store.getAllMemories().single().key
        commands.execute(LocalMemoryCommand.Remember(" I like blue "))
        assertEquals(key, store.getAllMemories().single().key)
    }

    @Test
    fun `invalid or oversized notes never overwrite data`() = runTest {
        val settings = AppSettings(MapSettings())
        val store = MemoryStore(settings)
        store.store("existing", "keep this")
        val commands = LocalMemoryCommands(settings, store)
        for (text in listOf("", "existing = ", "existing = ${"x".repeat(LocalMemoryCommands.MAX_NOTE_CHARS + 1)}")) {
            assertFalse(commands.execute(LocalMemoryCommand.Remember(text)).contains("Saved to memory"))
        }
        assertEquals("keep this", store.getAllMemories().single().content)
    }

    @Test
    fun `forget requires an exact key and deletes only that note`() = runTest {
        val settings = AppSettings(MapSettings())
        val store = MemoryStore(settings)
        store.store("test-a", "first")
        store.store("test-b", "second")
        val commands = LocalMemoryCommands(settings, store)
        for (key in listOf("", "test", "*", "test-a test-b")) {
            commands.execute(LocalMemoryCommand.Forget(key))
            assertEquals(2, store.getAllMemories().size)
        }
        assertTrue(commands.execute(LocalMemoryCommand.Forget("test-a")).contains("Deleted saved memory"))
        assertEquals("test-b", store.getAllMemories().single().key)
    }
}
