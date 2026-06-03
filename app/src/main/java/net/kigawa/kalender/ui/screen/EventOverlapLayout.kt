package net.kigawa.kalender.ui.screen

import net.kigawa.kalender.model.CalendarEvent
import java.time.Instant
import java.time.ZoneId

internal data class EventColumnLayout(
    val event: CalendarEvent,
    val columnIndex: Int,
    val totalColumns: Int,
    val startMinutes: Int,
    val durationMinutes: Int,
)

internal fun layoutDayEvents(events: List<CalendarEvent>, zoneId: ZoneId): List<EventColumnLayout> {
    if (events.isEmpty()) return emptyList()

    data class Timed(val event: CalendarEvent, val start: Int, val end: Int)

    val timed = events.map { event ->
        val startZdt = Instant.ofEpochMilli(event.startMs).atZone(zoneId)
        val start = startZdt.hour * 60 + startZdt.minute
        val duration = maxOf(30, ((event.endMs - event.startMs) / 60_000L).toInt())
        Timed(event, start, start + duration)
    }.sortedBy { it.start }

    // 貪欲法でカラムを割り当てる（各カラムの末尾時刻を追跡）
    val columnEnds = mutableListOf<Int>()
    val assigned = mutableListOf<Pair<Timed, Int>>()
    for (t in timed) {
        val col = columnEnds.indexOfFirst { it <= t.start }
        if (col == -1) {
            assigned.add(t to columnEnds.size)
            columnEnds.add(t.end)
        } else {
            assigned.add(t to col)
            columnEnds[col] = t.end
        }
    }

    // 各予定の totalColumns = 同時間帯の予定が使う最大カラム番号 + 1
    return assigned.map { (t, col) ->
        val maxCol = assigned
            .filter { (other, _) -> other.start < t.end && other.end > t.start }
            .maxOf { (_, c) -> c }
        EventColumnLayout(t.event, col, maxCol + 1, t.start, t.end - t.start)
    }
}
