package net.kigawa.kalender.data

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.UserCalendar
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
data class GoogleCalendarListItem(
    val id: String,
    val summary: String?,
    val backgroundColor: String?,
    val selected: Boolean = true,
)

@Serializable
data class GoogleCalendarListResponse(
    val items: List<GoogleCalendarListItem>? = emptyList(),
)

@Serializable
data class GoogleEvent(
    val id: String,
    val summary: String?,
    val description: String?,
    val location: String?,
    val start: GoogleEventDateTime,
    val end: GoogleEventDateTime,
    val colorId: String?,
    val isAllDay: Boolean? = false,
)

@Serializable
data class GoogleEventDateTime(
    val dateTime: String?,
    val date: String?,
    val timeZone: String?,
)

@Serializable
data class GoogleEventsResponse(
    val items: List<GoogleEvent>? = emptyList(),
)

class GoogleCalendarDataSource(private val accessToken: String, private val ownerEmail: String) : CalendarDataSource {

    private val client = HttpClient {
        install(JsonFeature) {
            serializer = KotlinxSerializer()
        }
        defaultRequest {
            header("Authorization", "Bearer $accessToken")
            header("Accept", "application/json")
        }
    }

    private val eventColors = mapOf(
        "1" to 0xFFD50000.toInt(), "2" to 0xFFE67C73.toInt(),
        "3" to 0xFFF6BF26.toInt(), "4" to 0xFF33B679.toInt(),
        "5" to 0xFF0B8043.toInt(), "6" to 0xFF039BE5.toInt(),
        "7" to 0xFF3F51B5.toInt(), "8" to 0xFF7986CB.toInt(),
        "9" to 0xFF8E24AA.toInt(), "10" to 0xFF616161.toInt(),
        "11" to 0xFF795548.toInt(),
    )

    private var cachedCalendars: List<UserCalendar>? = null

    override suspend fun fetchCalendars(): List<UserCalendar> = withContext(Dispatchers.IO) {
        cachedCalendars?.let { return@withContext it }
        val response = client.get<GoogleCalendarListResponse>("https://www.googleapis.com/calendar/v3/users/me/calendarList")
        val result = response.items?.mapNotNull { item ->
            if (!item.selected) return@mapNotNull null
            UserCalendar(
                id = item.id.toLongId(),
                name = item.summary ?: "",
                color = item.backgroundColor?.toArgbOrNull() ?: 0xFF808080.toInt(),
                accountName = item.id,
                ownerEmail = ownerEmail,
            )
        } ?: emptyList()
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
        val response = client.post<GoogleEvent>(url) {
            contentType(io.ktor.http.ContentType.Application.Json)
            setBody(buildEventJson(event))
        }
        val remoteId = response.id
        val calendarId = cachedCalendars?.find { it.accountName == calendarAccountName }?.id ?: event.calendarId
        event.copy(id = remoteId.toLongId(), remoteId = remoteId, calendarId = calendarId)
    }

    suspend fun updateEvent(calendarAccountName: String, event: CalendarEvent): CalendarEvent = withContext(Dispatchers.IO) {
        require(event.remoteId.isNotEmpty()) { "remoteId が空です" }
        val calId = URLEncoder.encode(calendarAccountName, "UTF-8")
        val eventId = URLEncoder.encode(event.remoteId, "UTF-8")
        val url = "https://www.googleapis.com/calendar/v3/calendars/$calId/events/$eventId"
        client.put<GoogleEvent>(url) {
            contentType(io.ktor.http.ContentType.Application.Json)
            setBody(buildEventJson(event))
        }
        event
    }

    suspend fun deleteEvent(calendarAccountName: String, remoteId: String) = withContext(Dispatchers.IO) {
        require(remoteId.isNotEmpty()) { "remoteId が空です" }
        val calId = URLEncoder.encode(calendarAccountName, "UTF-8")
        val eventId = URLEncoder.encode(remoteId, "UTF-8")
        val url = "https://www.googleapis.com/calendar/v3/calendars/$calId/events/$eventId"
        client.delete(url)
    }

    private fun buildEventJson(event: CalendarEvent): Map<String, Any> {
        val tz = event.timeZone.ifEmpty { ZoneId.systemDefault().id }
        val map = mutableMapOf<String, Any>()
        map["summary"] = event.title
        if (event.description.isNotEmpty()) map["description"] = event.description
        if (event.location.isNotEmpty()) map["location"] = event.location
        if (event.allDay) {
            map["start"] = mapOf("date" to formatDate(event.startMs))
            map["end"] = mapOf("date" to formatDate(event.endMs))
        } else {
            map["start"] = mapOf(
                "dateTime" to formatDateTime(event.startMs, tz),
                "timeZone" to tz
            )
            map["end"] = mapOf(
                "dateTime" to formatDateTime(event.endMs, tz),
                "timeZone" to tz
            )
        }
        return map
    }

    private fun formatDateTime(ms: Long, timeZone: String): String {
        val zone = runCatching { ZoneId.of(timeZone) }.getOrDefault(ZoneId.systemDefault())
        return Instant.ofEpochMilli(ms).atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    private fun formatDate(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    private suspend fun fetchCalendarEvents(calendar: UserCalendar, startMs: Long, endMs: Long): List<CalendarEvent> {
        val calId = URLEncoder.encode(calendar.accountName, "UTF-8")
        val timeMin = URLEncoder.encode(Instant.ofEpochMilli(startMs).toString(), "UTF-8")
        val timeMax = URLEncoder.encode(Instant.ofEpochMilli(endMs).toString(), "UTF-8")
        val url = "https://www.googleapis.com/calendar/v3/calendars/$calId/events" +
            "?timeMin=$timeMin&timeMax=$timeMax&singleEvents=true&orderBy=startTime&maxResults=250"
        val response = client.get<GoogleEventsResponse>(url)
        return response.items?.map { item ->
            val startObj = item.start
            val endObj = item.end
            val (startEpoch, allDay) = parseDateTime(startObj)
            val (endEpoch, _) = parseDateTime(endObj)
            val colorId = item.colorId
            val remoteId = item.id
            CalendarEvent(
                id = remoteId.toLongId(),
                calendarId = calendar.id,
                title = item.summary ?: "",
                startMs = startEpoch,
                endMs = endEpoch,
                allDay = allDay || item.isAllDay == true,
                color = eventColors[colorId] ?: calendar.color,
                timeZone = startObj.timeZone ?: "",
                description = item.description ?: "",
                location = item.location ?: "",
                remoteId = remoteId,
            )
        } ?: emptyList()
    }

    private fun parseDateTime(obj: GoogleEventDateTime?): Pair<Long, Boolean> {
        obj ?: return 0L to false
        val dt = obj.dateTime
        val d = obj.date
        return when {
            (dt != null && dt.isNotEmpty()) -> OffsetDateTime.parse(dt).toInstant().toEpochMilli() to false
            (d != null && d.isNotEmpty()) -> LocalDate.parse(d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() to true
            else -> 0L to false
        }
    }

    private fun String.toLongId(): Long = hashCode().toLong().and(0x7FFFFFFFL)

    private fun String.toArgbOrNull(): Int? = takeIf { isNotEmpty() }?.let {
        try { 
            val color = it
            if (color.startsWith("#")) {
                val hex = color.substring(1)
                if (hex.length == 6) Integer.parseInt("FF$hex", 16).toInt() else Integer.parseInt(hex, 16).toInt()
            } else {
                0xFF808080.toInt()
            }
        } catch (_: Exception) { null }
    }
}