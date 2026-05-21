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

class OutlookCalendarDataSource(private val accessToken: String) : CalendarDataSource {

    private var cachedCalendars: List<UserCalendar>? = null

    private fun get(url: String, extraHeaders: Map<String, String> = emptyMap()): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Accept", "application/json")
            extraHeaders.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.connect()
            if (conn.responseCode !in 200..299) {
                val error = conn.errorStream?.bufferedReader()?.readText()
                throw Exception("Graph API Error ${conn.responseCode}: $error")
            }
            return JSONObject(conn.inputStream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun fetchCalendars(): List<UserCalendar> = withContext(Dispatchers.IO) {
        cachedCalendars?.let { return@withContext it }
        try {
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
        } catch (e: Exception) {
            emptyList()
        }
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

        return try {
            val items = get(url, mapOf("Prefer" to "outlook.timezone=\"UTC\""))
                .optJSONArray("value") ?: return emptyList()
            (0 until items.length()).map { i ->
                val item = items.getJSONObject(i)
                val startObj = item.getJSONObject("start")
                val endObj = item.getJSONObject("end")
                val isAllDay = item.optBoolean("isAllDay", false)

                CalendarEvent(
                    id = item.getString("id").toLongId(),
                    calendarId = calendar.id,
                    title = item.optString("subject", "(タイトルなし)"),
                    startMs = OffsetDateTime.parse(startObj.getString("dateTime") + "Z").toInstant().toEpochMilli(),
                    endMs = OffsetDateTime.parse(endObj.getString("dateTime") + "Z").toInstant().toEpochMilli(),
                    allDay = isAllDay,
                    color = calendar.color,
                    timeZone = "UTC",
                    description = item.optString("bodyPreview", ""),
                    location = item.optJSONObject("location")?.optString("displayName", "") ?: "",
                )
            }
        } catch (e: Exception) {
            emptyList()
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
