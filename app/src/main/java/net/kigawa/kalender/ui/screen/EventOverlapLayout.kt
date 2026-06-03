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
    }

    // Union-Find でオーバーラップの連結成分を求める
    val parent = IntArray(timed.size) { it }
    fun find(x: Int): Int {
        if (parent[x] != x) parent[x] = find(parent[x])
        return parent[x]
    }
    for (i in timed.indices) {
        for (j in i + 1 until timed.size) {
            if (timed[i].start < timed[j].end && timed[j].start < timed[i].end) {
                parent[find(i)] = find(j)
            }
        }
    }

    // 連結成分ごとにカラムを割り当て、totalColumns を統一する
    val result = mutableListOf<EventColumnLayout>()
    timed.indices.groupBy { find(it) }.values.forEach { indices ->
        val group = indices.map { timed[it] }.sortedBy { it.start }

        val columnEnds = mutableListOf<Int>()
        val colAssigned = mutableListOf<Int>()
        for (t in group) {
            val col = columnEnds.indexOfFirst { it <= t.start }
            if (col == -1) {
                colAssigned.add(columnEnds.size)
                columnEnds.add(t.end)
            } else {
                colAssigned.add(col)
                columnEnds[col] = t.end
            }
        }

        val totalCols = columnEnds.size
        group.zip(colAssigned).forEach { (t, col) ->
            result.add(EventColumnLayout(t.event, col, totalCols, t.start, t.end - t.start))
        }
    }

    return result
}
