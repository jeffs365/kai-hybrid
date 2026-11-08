package com.inspiredandroid.kai.data

import kotlinx.serialization.KSerializer

/**
 * Capped FIFO queue persisted as JSON via [SettingsJsonList]. Generic over the item type [T]
 * and a stable key type [K] used to identify items for removal. Shared by `EmailStore`,
 * `SmsStore`, and `NotificationStore` to enforce a uniform pending-buffer discipline.
 */
class PendingQueue<T, K>(
    readJson: () -> String,
    writeJson: (String) -> Unit,
    serializer: KSerializer<T>,
    label: String,
    private val keyOf: (T) -> K,
    private val maxSize: Int = 100,
) {
    private val persisted = SettingsJsonList(
        read = readJson,
        write = writeJson,
        itemSerializer = serializer,
        label = label,
    )

    fun get(): List<T> = persisted.get()

    suspend fun add(items: List<T>) {
        if (items.isEmpty()) return
        persisted.update { current ->
            val keys = items.map(keyOf).toSet()
            (current.filterNot { keyOf(it) in keys } + items.distinctBy(keyOf)).takeLast(maxSize)
        }
    }

    suspend fun remove(items: List<T>) {
        if (items.isEmpty()) return
        persisted.update { current -> current.filterNot { item -> items.any { keyOf(it) == keyOf(item) && it == item } } }
    }

    suspend fun clear() {
        persisted.update { emptyList() }
    }
}
