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
import java.time.format.DateTimeFormatter

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

    private fun post(url: String, body: JSONObject): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() }
                throw Exception("Google Calendar API Error ${conn.responseCode}: $error")
            }
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun put(url: String, body: JSONObject): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() }
                throw Exception("Google Calendar API Error ${conn.responseCode}: $error")
            }
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun httpDelete(url: String) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "DELETE"
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.connect()
            if (conn.responseCode !in 200..299 && conn.responseCode != 204) {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() }
                throw Exception("Google Calendar API Error ${conn.responseCode}: $error")
            }
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

    suspend fun createEvent(calendarAccountName: String, event: CalendarEvent): CalendarEvent = withContext(Dispatchers.IO) {
        val calId = URLEncoder.encode(calendarAccountName, "UTF-8")
        val url = "https://www.googleapis.com/calendar/v3/calendars/$calId/events"
        val response = post(url, buildEventJson(event))
        val remoteId = response.getString("id")
        val calendarId = cachedCalendars?.find { it.accountName == calendarAccountName }?.id ?: event.calendarId
        event.copy(id = remoteId.toLongId(), remoteId = remoteId, calendarId = calendarId)
    }

    suspend fun updateEvent(calendarAccountName: String, event: CalendarEvent): CalendarEvent = withContext(Dispatchers.IO) {
        require(event.remoteId.isNotEmpty()) { "remoteId が空です" }
        val calId = URLEncoder.encode(calendarAccountName, "UTF-8")
        val eventId = URLEncoder.encode(event.remoteId, "UTF-8")
        val url = "https://www.googleapis.com/calendar/v3/calendars/$calId/events/$eventId"
        put(url, buildEventJson(event))
        event
    }

    suspend fun deleteEvent(calendarAccountName: String, remoteId: String) = withContext(Dispatchers.IO) {
        require(remoteId.isNotEmpty()) { "remoteId が空です" }
        val calId = URLEncoder.encode(calendarAccountName, "UTF-8")
        val eventId = URLEncoder.encode(remoteId, "UTF-8")
        val url = "https://www.googleapis.com/calendar/v3/calendars/$calId/events/$eventId"
        httpDelete(url)
    }

    private fun buildEventJson(event: CalendarEvent): JSONObject {
        val tz = event.timeZone.ifEmpty { ZoneId.systemDefault().id }
        return JSONObject().apply {
            put("summary", event.title)
            if (event.description.isNotEmpty()) put("description", event.description)
            if (event.location.isNotEmpty()) put("location", event.location)
            if (event.allDay) {
                put("start", JSONObject().put("date", formatDate(event.startMs)))
                put("end", JSONObject().put("date", formatDate(event.endMs)))
            } else {
                put("start", JSONObject().apply {
                    put("dateTime", formatDateTime(event.startMs, tz))
                    put("timeZone", tz)
                })
                put("end", JSONObject().apply {
                    put("dateTime", formatDateTime(event.endMs, tz))
                    put("timeZone", tz)
                })
            }
        }
    }

    private fun formatDateTime(ms: Long, timeZone: String): String {
        val zone = runCatching { ZoneId.of(timeZone) }.getOrDefault(ZoneId.systemDefault())
        return Instant.ofEpochMilli(ms).atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    private fun formatDate(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()

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
            val remoteId = item.getString("id")
            CalendarEvent(
                id = remoteId.toLongId(),
                calendarId = calendar.id,
                title = item.optString("summary", ""),
                startMs = startEpoch,
                endMs = endEpoch,
                allDay = allDay,
                color = eventColors[colorId] ?: calendar.color,
                timeZone = startObj?.optString("timeZone") ?: "",
                description = item.optString("description", ""),
                location = item.optString("location", ""),
                remoteId = remoteId,
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
