package net.kigawa.kalender.data.db

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import net.kigawa.kalender.data.LocalEventTemplateStore
import net.kigawa.kalender.model.EventTemplate
import net.kigawa.kalender.model.RecurrenceRule

class RoomEventTemplateStore(
    private val dao: EventTemplateDao,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : LocalEventTemplateStore {

    companion object {
        fun fromContext(context: Context): RoomEventTemplateStore {
            val db = KalenderDatabase.getInstance(context)
            return RoomEventTemplateStore(db.eventTemplateDao())
        }
    }

    override fun observeAll(): Flow<List<EventTemplate>> =
        dao.observeAll().map { list -> list.map { it.toModel() } }

    override fun observeById(id: Long): Flow<EventTemplate?> =
        dao.observeById(id).map { it?.toModel() }

    override suspend fun getAll(): List<EventTemplate> =
        dao.getAll().map { it.toModel() }

    override suspend fun findById(id: Long): EventTemplate? =
        dao.findById(id)?.toModel()

    override suspend fun upsert(template: EventTemplate): Long =
        dao.upsert(template.toEntity())

    override suspend fun delete(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }

    private fun EventTemplateEntity.toModel() =
        EventTemplate(
            id = id,
            name = name,
            title = title,
            description = description,
            location = location,
            durationMinutes = durationMinutes,
            allDay = allDay,
            recurrence = recurrenceJson?.let {
                runCatching { json.decodeFromString(RecurrenceRule.serializer(), it) }.getOrNull()
            } ?: recurrenceRule?.let {
                runCatching { RecurrenceRule.fromRRule(it) }.getOrNull()
            },
            preferredCalendarId = preferredCalendarId,
            color = color,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun EventTemplate.toEntity() =
        EventTemplateEntity(
            id = id,
            name = name,
            title = title,
            description = description,
            location = location,
            durationMinutes = durationMinutes,
            allDay = allDay,
            recurrenceRule = recurrenceRule,
            recurrenceJson = recurrence?.let { rule ->
                json.encodeToString(RecurrenceRule.serializer(), rule)
            },
            preferredCalendarId = preferredCalendarId,
            color = color,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
