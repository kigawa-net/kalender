package net.kigawa.kalender.data.db

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.kigawa.kalender.data.LocalEventTemplateStore
import net.kigawa.kalender.model.EventTemplate
import net.kigawa.kalender.util.nowMs

private const val KEY_TEMPLATES = "kalender_event_templates"

/**
 * ブラウザ(localStorage)を使ったイベントテンプレート保存実装。
 */
class WebEventTemplateStore : LocalEventTemplateStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val _templates = MutableStateFlow(load())

    private fun load(): List<EventTemplate> = runCatching {
        localStorage.getItem(KEY_TEMPLATES)?.let { json.decodeFromString<List<EventTemplate>>(it) }
    }.getOrNull() ?: emptyList()

    private fun persist() {
        localStorage.setItem(KEY_TEMPLATES, json.encodeToString(_templates.value))
    }

    override fun observeAll(): Flow<List<EventTemplate>> = _templates

    override fun observeById(id: Long): Flow<EventTemplate?> =
        _templates.map { list -> list.find { it.id == id } }

    override suspend fun getAll(): List<EventTemplate> = _templates.value

    override suspend fun findById(id: Long): EventTemplate? = _templates.value.find { it.id == id }

    override suspend fun upsert(template: EventTemplate): Long {
        val current = _templates.value.toMutableList()
        val index = current.indexOfFirst { it.id == template.id }
        val withTimestamps = template.copy(
            createdAt = if (template.createdAt == 0L) nowMs() else template.createdAt,
            updatedAt = nowMs(),
        )
        val savedId = if (index >= 0) {
            current[index] = withTimestamps
            withTimestamps.id
        } else {
            val newId = if (template.id == 0L) (current.maxOfOrNull { it.id } ?: 0L) + 1L else template.id
            val newTemplate = withTimestamps.copy(id = newId)
            current.add(newTemplate)
            newId
        }
        _templates.value = current.sortedByDescending { it.updatedAt }
        persist()
        return savedId
    }

    override suspend fun delete(id: Long) {
        _templates.value = _templates.value.filter { it.id != id }
        persist()
    }

    override suspend fun deleteAll() {
        _templates.value = emptyList()
        persist()
    }
}
