package net.kigawa.kalender.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.UserCalendar
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class OutlookCalendarDataSource(private val accessToken: String) : CalendarDataSource {

    private var cachedCalendars: List<UserCalendar>? = null

    private val dateTimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    private fun get(url: String, extraHeaders: Map<String, String> = emptyMap()): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Accept", "application/json")
            extraHeaders.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.connect()
            if (conn.responseCode !in 200..299) {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() }
                throw Exception("Graph API Error ${conn.responseCode}: $error")
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
            conn.setRequestProperty("Accept", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() }
                throw Exception("Graph API Error ${conn.responseCode}: $error")
            }
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun patch(url: String, body: JSONObject): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "PATCH"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() }
                throw Exception("Graph API Error ${conn.responseCode}: $error")
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
            conn.setRequestProperty("Accept", "application/json")
            conn.connect()
            if (conn.responseCode !in 200..299 && conn.responseCode != 204) {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() }
                throw Exception("Graph API Error ${conn.responseCode}: $error")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun buildEventJson(event: CalendarEvent): JSONObject {
        return JSONObject().apply {
            put("subject", event.title)
            put("isAllDay", event.allDay)
            put("start", JSONObject().apply {
                put("dateTime", Instant.ofEpochMilli(event.startMs).atZone(ZoneOffset.UTC).format(dateTimeFmt))
                put("timeZone", "UTC")
            })
            put("end", JSONObject().apply {
                put("dateTime", Instant.ofEpochMilli(event.endMs).atZone(ZoneOffset.UTC).format(dateTimeFmt))
                put("timeZone", "UTC")
            })
            if (event.description.isNotEmpty()) {
                put("body", JSONObject().apply {
                    put("contentType", "text")
                    put("content", event.description)
                })
            }
            if (event.location.isNotEmpty()) {
                put("location", JSONObject().apply {
                    put("displayName", event.location)
                })
            }
        }
    }

    suspend fun createEvent(calendarAccountName: String, event: CalendarEvent): CalendarEvent = withContext(Dispatchers.IO) {
        val calId = URLEncoder.encode(calendarAccountName, "UTF-8")
        val url = "https://graph.microsoft.com/v1.0/me/calendars/$calId/events"
        val response = post(url, buildEventJson(event))
        val remoteId = response.getString("id")
        val calendarId = cachedCalendars?.find { it.accountName == calendarAccountName }?.id ?: event.calendarId
        event.copy(id = remoteId.toLongId(), remoteId = remoteId, calendarId = calendarId)
    }

    suspend fun updateEvent(calendarAccountName: String, event: CalendarEvent): CalendarEvent = withContext(Dispatchers.IO) {
        require(event.remoteId.isNotEmpty()) { "remoteId が空です" }
        val calId = URLEncoder.encode(calendarAccountName, "UTF-8")
        val eventId = URLEncoder.encode(event.remoteId, "UTF-8")
        val url = "https://graph.microsoft.com/v1.0/me/calendars/$calId/events/$eventId"
        patch(url, buildEventJson(event))
        event
    }

    suspend fun deleteEvent(calendarAccountName: String, remoteId: String) = withContext(Dispatchers.IO) {
        require(remoteId.isNotEmpty()) { "remoteId が空です" }
        val calId = URLEncoder.encode(calendarAccountName, "UTF-8")
        val eventId = URLEncoder.encode(remoteId, "UTF-8")
        val url = "https://graph.microsoft.com/v1.0/me/calendars/$calId/events/$eventId"
        httpDelete(url)
    }

    override suspend fun fetchCalendars(): List<UserCalendar> = withContext(Dispatchers.IO) {
        cachedCalendars?.let { return@withContext it }
        val items = get("https://graph.microsoft.com/v1.0/me/calendars")
            .optJSONArray("value") ?: return@withContext emptyList()
        val result = (0 until items.length()).map { i ->
            val item = items.getJSONObject(i)
            UserCalendar(
                id = item.getString("id").toLongId(),
                name = item.optString("name", ""),
                color = item.optString("color").toOutlookColor(),
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
        val start = Instant.ofEpochMilli(startMs).toString()
        val end = Instant.ofEpochMilli(endMs).toString()
        val url = "https://graph.microsoft.com/v1.0/me/calendars/$calId/calendarView" +
                "?startDateTime=$start&endDateTime=$end&\$select=id,subject,start,end,isAllDay,bodyPreview,location"

        val items = get(url, mapOf("Prefer" to "outlook.timezone=\"UTC\""))
            .optJSONArray("value") ?: return emptyList()
        return (0 until items.length()).map { i ->
            val item = items.getJSONObject(i)
            val startObj = item.getJSONObject("start")
            val endObj = item.getJSONObject("end")
            val isAllDay = item.optBoolean("isAllDay", false)
            val remoteId = item.getString("id")

            CalendarEvent(
                id = remoteId.toLongId(),
                calendarId = calendar.id,
                title = item.optString("subject", "(タイトルなし)"),
                startMs = OffsetDateTime.parse(startObj.getString("dateTime") + "Z").toInstant().toEpochMilli(),
                endMs = OffsetDateTime.parse(endObj.getString("dateTime") + "Z").toInstant().toEpochMilli(),
                allDay = isAllDay,
                color = calendar.color,
                timeZone = "UTC",
                description = item.optString("bodyPreview", ""),
                location = item.optJSONObject("location")?.optString("displayName", "") ?: "",
                remoteId = remoteId,
            )
        }
    }

    private fun String.toLongId(): Long = hashCode().toLong().and(0x7FFFFFFFL)

    private fun String.toOutlookColor(): Int {
        return when (this.lowercase()) {
            "lightblue" -> 0xFF99CCFF.toInt()
            "lightgreen" -> 0xFF99FF99.toInt()
            "lightorange" -> 0xFFFFCC99.toInt()
            "lightred" -> 0xFFFF9999.toInt()
            "lightyellow" -> 0xFFFFCC.toInt()
            else -> 0xFF0078D4.toInt() // Default Outlook Blue
        }
    }
}
