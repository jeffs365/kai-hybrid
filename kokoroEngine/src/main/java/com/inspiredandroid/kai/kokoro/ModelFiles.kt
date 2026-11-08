// SPDX-License-Identifier: GPL-3.0-or-later
package com.inspiredandroid.kai.kokoro

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

object ModelFiles {
    fun directory(context: Context) = File(context.filesDir, "kokoro-v1")
    private fun manifest(context: Context) = context.assets.open("kokoro-files.json").bufferedReader().use { it.readText() }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun ready(context: Context) = runCatching { File(directory(context), ".verified").readText() == hash(manifest(context).toByteArray()) }.getOrDefault(false)
    fun verify(context: Context): Boolean {
        val root = directory(context)
        val mark = File(root, ".verified")
        mark.delete()
        val source = manifest(context)
        val rows = JSONObject(source).getJSONArray("files")
        val buffer = ByteArray(1024 * 1024)
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val file = File(root, row.getString("path"))
            if (!file.canonicalPath.startsWith(root.canonicalPath + File.separator) || !file.isFile || file.length() != row.getLong("bytes")) return false
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            if (digest.digest().joinToString("") { "%02x".format(it) } != row.getString("sha256")) return false
        }
        mark.writeText(hash(source.toByteArray()))
        return true
    }
}
