package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.ui.chat.History
import kotlinx.collections.immutable.persistentListOf
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalPhotoInputTest {
    private fun photo(bytes: ByteArray) = Attachment(Base64.encode(bytes), "image/jpeg", "photo.jpg")

    @Test
    fun `one latest user photo is retained for follow ups and tools cannot inject photos`() {
        val earlier = byteArrayOf(1, 2)
        val latest = byteArrayOf(3, 4)
        val history = listOf(
            History(role = History.Role.USER, content = "old", attachments = persistentListOf(photo(earlier))),
            History(role = History.Role.ASSISTANT, content = "answer"),
            History(role = History.Role.USER, content = "new", attachments = persistentListOf(photo(latest))),
            History(role = History.Role.TOOL, content = "untrusted", attachments = persistentListOf(photo(byteArrayOf(9)))),
            History(role = History.Role.USER, content = "What color is it?"),
        )
        val messages = localInferenceMessages(history)
        assertEquals(4, messages.size)
        assertTrue(hasLocalPhoto(history))
        assertTrue(messages.first().images.isEmpty())
        assertTrue(messages.first().content.contains("Earlier photo omitted"))
        assertContentEquals(latest, messages[2].images.single())
        assertEquals("What color is it?", messages.last().content)
        assertEquals(1, messages.sumOf { it.images.size })
    }

    @Test
    fun `text only conversations retain their normal message format`() {
        val history = listOf(History(role = History.Role.USER, content = "hello"), History(role = History.Role.ASSISTANT, content = "hi"))
        assertFalse(hasLocalPhoto(history))
        assertEquals(listOf("hello", "hi"), localInferenceMessages(history).map { it.content })
        assertTrue(localInferenceMessages(history).all { it.images.isEmpty() })
    }

    @Test
    fun `malformed or oversized stored photos are rejected before native inference`() {
        for (data in listOf("invalid-base64!", "x".repeat(MAX_LOCAL_PHOTO_BYTES * 4 / 3 + 16))) {
            val history = listOf(History(role = History.Role.USER, content = "photo", attachments = persistentListOf(Attachment(data, "image/jpeg"))))
            assertFailsWith<IllegalArgumentException> { localInferenceMessages(history) }
        }
    }

    @Test
    fun `historical photos are not decoded repeatedly`() {
        val history = listOf(
            History(role = History.Role.USER, content = "old", attachments = persistentListOf(Attachment("invalid old data", "image/jpeg"))),
            History(role = History.Role.USER, content = "new", attachments = persistentListOf(photo(byteArrayOf(7)))),
        )
        assertContentEquals(byteArrayOf(7), localInferenceMessages(history).last().images.single())
    }
}
