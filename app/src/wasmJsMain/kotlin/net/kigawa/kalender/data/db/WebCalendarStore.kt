package net.kigawa.kalender.data.db

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.kigawa.kalender.data.LocalCalendarStore
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.UserCalendar
import net.kigawa.kalender.util.nowMs

private const val KEY_CALENDARS = "kalender_calendars"
private const val KEY_EVENTS = "kalender_events"
private const val KEY_CACHE_META = "kalender_cache_meta"

@Serializable
private data class CacheMetaRecord(val weekStartMs: Long, val lastFetchedMs: Long)

/**
 * ブラウザ(localStorage)を使ったローカルキャッシュ実装。RoomはwasmJsをサポートしないため、
 * Web版ではシンプルなJSONシリアライズ+localStorage永続化で代替する。
 */
class WebCalendarStore : LocalCalendarStore {
    private val json = Json { ignoreUnknownKeys = true }

    private val _calendars = MutableStateFlow(loadCalendars())
    private val _events = MutableStateFlow(loadEvents())
    private val cacheMeta: MutableMap<Long, Long> =
        loadCacheMeta().associate { it.weekStartMs to it.lastFetchedMs }.toMutableMap()

    private fun loadCalendars(): List<UserCalendar> = runCatching {
        localStorage.getItem(KEY_CALENDARS)?.let { json.decodeFromString<List<UserCalendar>>(it) }
    }.getOrNull() ?: emptyList()

    private fun loadEvents(): List<CalendarEvent> = runCatching {
        localStorage.getItem(KEY_EVENTS)?.let { json.decodeFromString<List<CalendarEvent>>(it) }
    }.getOrNull() ?: emptyList()

    private fun loadCacheMeta(): List<CacheMetaRecord> = runCatching {
        localStorage.getItem(KEY_CACHE_META)?.let { json.decodeFromString<List<CacheMetaRecord>>(it) }
    }.getOrNull() ?: emptyList()

    private fun persistCalendars() {
        localStorage.setItem(KEY_CALENDARS, json.encodeToString(_calendars.value))
    }

    private fun persistEvents() {
        localStorage.setItem(KEY_EVENTS, json.encodeToString(_events.value))
    }

    private fun persistCacheMeta() {
        localStorage.setItem(KEY_CACHE_META, json.encodeToString(cacheMeta.map { CacheMetaRecord(it.key, it.value) }))
    }

    override fun observeCalendars(): Flow<List<UserCalendar>> = _calendars

    override fun observeVisibleCalendars(): Flow<List<UserCalendar>> = _calendars.map { list -> list.filter { it.isVisible } }

    override fun observeEvents(startMs: Long, endMs: Long): Flow<List<CalendarEvent>> =
        _events.map { list -> list.filter { it.startMs < endMs && it.endMs > startMs } }

    override fun observeEventById(id: Long): Flow<CalendarEvent?> = _events.map { list -> list.find { it.id == id } }

    override suspend fun upsertCalendars(calendars: List<UserCalendar>) {
        val current = _calendars.value.associateBy { it.id }.toMutableMap()
        calendars.forEach { incoming ->
            val existing = current[incoming.id]
            current[incoming.id] = if (existing != null) incoming.copy(isVisible = existing.isVisible) else incoming
        }
        _calendars.value = current.values.toList()
        persistCalendars()
    }

    override suspend fun upsertEventsForCalendars(
        events: List<CalendarEvent>,
        startMs: Long,
        endMs: Long,
        calendarIds: List<Long>,
    ) {
        val remaining = _events.value.filterNot { e ->
            e.calendarId in calendarIds && e.startMs < endMs && e.endMs > startMs
        }
        _events.value = remaining + events
        persistEvents()
    }

    override suspend fun upsertEvent(event: CalendarEvent) {
        _events.value = _events.value.filterNot { it.id == event.id } + event
        persistEvents()
    }

    override suspend fun deleteEventById(id: Long) {
        _events.value = _events.value.filterNot { it.id == id }
        persistEvents()
    }

    override suspend fun updateCalendarVisibility(id: Long, isVisible: Boolean) {
        _calendars.value = _calendars.value.map { if (it.id == id) it.copy(isVisible = isVisible) else it }
        persistCalendars()
    }

    override suspend fun deleteCalendarsByOwnerEmail(ownerEmail: String) {
        val removedIds = _calendars.value.filter { it.ownerEmail == ownerEmail }.map { it.id }.toSet()
        _calendars.value = _calendars.value.filterNot { it.ownerEmail == ownerEmail }
        _events.value = _events.value.filterNot { it.calendarId in removedIds }
        persistCalendars()
        persistEvents()
    }

    override suspend fun isWeekCacheFresh(weekStartMs: Long, ttlMs: Long): Boolean {
        val last = cacheMeta[weekStartMs] ?: return false
        return (nowMs() - last) < ttlMs
    }

    override suspend fun markWeekFetched(weekStartMs: Long) {
        cacheMeta[weekStartMs] = nowMs()
        persistCacheMeta()
    }

    override suspend fun clearWeekCache() {
        cacheMeta.clear()
        persistCacheMeta()
    }
}
