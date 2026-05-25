package net.kigawa.kalender.data

import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.UserCalendar
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

class GoogleCalendarDataSource(private val accessToken: String) : CalendarDataSource {

    private val eventColors = mapOf(
        "1" to 0xFFD50000.toInt(), "2" to 0xFFE67C73.toInt(),
        "3" to 0xFFF6BF26.toInt(), "4" to 0xFF33B679.toInt(),
        "5" to 0xFF0B8043.toInt(), "6" to 0xFF039BE5.toInt(),
        "7" to 0xFF3F51B5.toInt(), "8" to 0xFF7986CB.toInt(),
        "9" to 0xFF8E24AA.toInt(), "10" to 0xFF616161.toInt(),
        "11" to 0xFF795548.toInt(),
    )

    private var cachedCalendars: List<UserCalendar>? = null

    private fun get(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.connect()
            if (conn.responseCode !in 200..299) {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() }
                throw Exception("Google Calendar API Error ${conn.responseCode}: $error")
            }
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun fetchCalendars(): List<UserCalendar> = withContext(Dispatchers.IO) {
        cachedCalendars?.let { return@withContext it }
        val items = get("https://www.googleapis.com/calendar/v3/users/me/calendarList")
            .optJSONArray("items") ?: return@withContext emptyList()
        val result = (0 until items.length()).mapNotNull { i ->
            val item = items.getJSONObject(i)
            if (!item.optBoolean("selected", true)) return@mapNotNull null
            UserCalendar(
                id = item.getString("id").toLongId(),
                name = item.optString("summary", ""),
                color = item.optString("backgroundColor").toArgbOrNull() ?: 0xFF808080.toInt(),
                accountName = item.getString("id"),
            )
        }
        cachedCalendars = result
        result
    }

    override suspend fun fetchEvents(startMs: Long, endMs: Long): List<CalendarEvent> = withContext(Dispatchers.IO) {
        fetchCalendars().flatMap { calendar ->
            fetchCalendarEvents(calendar, startMs, endMs)
        }
    }

    private fun fetchCalendarEvents(calendar: UserCalendar, startMs: Long, endMs: Long): List<CalendarEvent> {
        val calId = URLEncoder.encode(calendar.accountName, "UTF-8")
        val timeMin = URLEncoder.encode(Instant.ofEpochMilli(startMs).toString(), "UTF-8")
        val timeMax = URLEncoder.encode(Instant.ofEpochMilli(endMs).toString(), "UTF-8")
        val url = "https://www.googleapis.com/calendar/v3/calendars/$calId/events" +
            "?timeMin=$timeMin&timeMax=$timeMax&singleEvents=true&orderBy=startTime&maxResults=250"
        val items = get(url).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).map { i ->
            val item = items.getJSONObject(i)
            val startObj = item.optJSONObject("start")
            val endObj = item.optJSONObject("end")
            val (startEpoch, allDay) = parseDateTime(startObj)
            val (endEpoch, _) = parseDateTime(endObj)
            val colorId = item.optString("colorId")
            CalendarEvent(
                id = item.getString("id").toLongId(),
                calendarId = calendar.id,
                title = item.optString("summary", ""),
                startMs = startEpoch,
                endMs = endEpoch,
                allDay = allDay,
                color = eventColors[colorId] ?: calendar.color,
                timeZone = startObj?.optString("timeZone") ?: "",
                description = item.optString("description", ""),
                location = item.optString("location", ""),
            )
        }
    }

    private fun parseDateTime(obj: JSONObject?): Pair<Long, Boolean> {
        obj ?: return 0L to false
        val dt = obj.optString("dateTime")
        val d = obj.optString("date")
        return when {
            dt.isNotEmpty() -> OffsetDateTime.parse(dt).toInstant().toEpochMilli() to false
            d.isNotEmpty() -> LocalDate.parse(d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() to true
            else -> 0L to false
        }
    }

    private fun String.toLongId(): Long = hashCode().toLong().and(0x7FFFFFFFL)

    private fun String.toArgbOrNull(): Int? = takeIf { isNotEmpty() }?.let {
        try { Color.parseColor(it) } catch (_: Exception) { null }
    }
}
