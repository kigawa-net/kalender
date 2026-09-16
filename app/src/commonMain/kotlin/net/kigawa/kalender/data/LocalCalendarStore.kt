package net.kigawa.kalender.data

import kotlinx.coroutines.flow.Flow
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.UserCalendar

/**
 * ローカルキャッシュ(カレンダー/イベント/週フェッチ状態)への共通アクセスインターフェース。
 * Androidでは Room、Webではブラウザストレージで実装する。
 */
interface LocalCalendarStore {
    fun observeCalendars(): Flow<List<UserCalendar>>
    fun observeVisibleCalendars(): Flow<List<UserCalendar>>
    fun observeEvents(startMs: Long, endMs: Long): Flow<List<CalendarEvent>>
    fun observeEventById(id: Long): Flow<CalendarEvent?>

    suspend fun upsertCalendars(calendars: List<UserCalendar>)
    suspend fun upsertEventsForCalendars(events: List<CalendarEvent>, startMs: Long, endMs: Long, calendarIds: List<Long>)
    suspend fun upsertEvent(event: CalendarEvent)
    suspend fun deleteEventById(id: Long)
    suspend fun updateCalendarVisibility(id: Long, isVisible: Boolean)
    suspend fun deleteCalendarsByOwnerEmail(ownerEmail: String)

    /** 週開始msをキーにしたフェッチキャッシュの鮮度を確認する */
    suspend fun isWeekCacheFresh(weekStartMs: Long, ttlMs: Long): Boolean
    suspend fun markWeekFetched(weekStartMs: Long)
    suspend fun clearWeekCache()
}
