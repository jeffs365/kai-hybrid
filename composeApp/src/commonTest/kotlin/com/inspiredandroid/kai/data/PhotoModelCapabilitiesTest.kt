package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.ui.chat.History
import com.inspiredandroid.kai.ui.chat.toGroqMessageDto
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.JsonArray
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotoModelCapabilitiesTest {
    @Test
    fun `verified DeepSeek vision aliases accept photos while text families remain gated`() {
        assertTrue(modelSupportsImages("deepseek-flash"))
        assertTrue(modelSupportsImages("provider/deepseek-v4-flash-vision-exp"))
        assertFalse(modelSupportsImages("deepseek-chat"))
        assertFalse(modelSupportsImages("deepseek-reasoner"))
        assertFalse(modelSupportsImages("deepseek-v4-pro"))
    }

    @Test
    fun `image parts survive the vision wire format and are withheld for text models`() {
        val message = History(role = History.Role.USER, content = "Describe this photo", attachments = persistentListOf(Attachment("aGVsbG8=", "image/jpeg")))
        val vision = message.toGroqMessageDto(supportsImages = modelSupportsImages("deepseek-flash"))
        assertTrue(vision.content is JsonArray)
        assertTrue(vision.content.toString().contains("data:image/jpeg;base64,aGVsbG8="))
        val text = message.toGroqMessageDto(supportsImages = modelSupportsImages("deepseek-chat"))
        assertFalse(text.content.toString().contains("data:image"))
    }
}
