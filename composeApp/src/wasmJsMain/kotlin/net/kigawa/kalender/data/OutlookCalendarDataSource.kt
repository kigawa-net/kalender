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
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
data class OutlookCalendar(
    val id: String,
    val name: String?,
    val color: String?,
)

@Serializable
data class OutlookCalendarsResponse(
    val value: List<OutlookCalendar>? = emptyList(),
)

@Serializable
data class OutlookEvent(
    val id: String,
    val subject: String?,
    val start: OutlookEventDateTime,
    val end: OutlookEventDateTime,
    val isAllDay: Boolean? = false,
    val bodyPreview: String?,
    val location: OutlookLocation?,
)

@Serializable
data class OutlookEventDateTime(
    val dateTime: String,
    val timeZone: String,
)

@Serializable
data class OutlookLocation(
    val displayName: String?,
)

@Serializable
data class OutlookEventsResponse(
    val value: List<OutlookEvent>? = emptyList(),
)

class OutlookCalendarDataSource(private val accessToken: String, private val ownerEmail: String) : CalendarDataSource {

    private val client = HttpClient {
        install(JsonFeature) {
            serializer = KotlinxSerializer()
        }
        defaultRequest {
            header("Authorization", "Bearer $accessToken")
            header("Accept", "application/json")
        }
    }

    private var cachedCalendars: List<UserCalendar>? = null

    private val dateTimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    override suspend fun fetchCalendars(): List<UserCalendar> = withContext(Dispatchers.IO) {
        cachedCalendars?.let { return@withContext it }
        val response = client.get<OutlookCalendarsResponse>("https://graph.microsoft.com/v1.0/me/calendars")
        val result = response.value?.map { item ->
            UserCalendar(
                id = item.id.toLongId(),
                name = item.name ?: "",
                color = item.color?.toOutlookColor() ?: 0xFF0078D4.toInt(),
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
        val url = "https://graph.microsoft.com/v1.0/me/calendars/$calId/events"
        val response = client.post<OutlookEvent>(url) {
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
        val url = "https://graph.microsoft.com/v1.0/me/calendars/$calId/events/$eventId"
        client.patch<OutlookEvent>(url) {
            contentType(io.ktor.http.ContentType.Application.Json)
            setBody(buildEventJson(event))
        }
        event
    }

    suspend fun deleteEvent(calendarAccountName: String, remoteId: String) = withContext(Dispatchers.IO) {
        require(remoteId.isNotEmpty()) { "remoteId が空です" }
        val calId = URLEncoder.encode(calendarAccountName, "UTF-8")
        val eventId = URLEncoder.encode(remoteId, "UTF-8")
        val url = "https://graph.microsoft.com/v1.0/me/calendars/$calId/events/$eventId"
        client.delete(url)
    }

    private fun buildEventJson(event: CalendarEvent): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        map["subject"] = event.title
        map["isAllDay"] = event.allDay
        map["start"] = mapOf(
            "dateTime" to Instant.ofEpochMilli(event.startMs).atZone(ZoneOffset.UTC).format(dateTimeFmt),
            "timeZone" to "UTC"
        )
        map["end"] = mapOf(
            "dateTime" to Instant.ofEpochMilli(event.endMs).atZone(ZoneOffset.UTC).format(dateTimeFmt),
            "timeZone" to "UTC"
        )
        if (event.description.isNotEmpty()) {
            map["body"] = mapOf(
                "contentType" to "text",
                "content" to event.description
            )
        }
        if (event.location.isNotEmpty()) {
            map["location"] = mapOf(
                "displayName" to event.location
            )
        }
        return map
    }

    private suspend fun fetchCalendarEvents(calendar: UserCalendar, startMs: Long, endMs: Long): List<CalendarEvent> {
        val calId = URLEncoder.encode(calendar.accountName, "UTF-8")
        val start = Instant.ofEpochMilli(startMs).toString()
        val end = Instant.ofEpochMilli(endMs).toString()
        val url = "https://graph.microsoft.com/v1.0/me/calendars/$calId/calendarView" +
                "?startDateTime=$start&endDateTime=$end&\$select=id,subject,start,end,isAllDay,bodyPreview,location"

        val response = client.get<OutlookEventsResponse>(url) {
            header("Prefer", "outlook.timezone=\"UTC\"")
        }
        return response.value?.map { item ->
            val startObj = item.start
            val endObj = item.end
            val isAllDay = item.isAllDay == true
            val remoteId = item.id

            CalendarEvent(
                id = remoteId.toLongId(),
                calendarId = calendar.id,
                title = item.subject ?: "(タイトルなし)",
                startMs = OffsetDateTime.parse(startObj.dateTime + "Z").toInstant().toEpochMilli(),
                endMs = OffsetDateTime.parse(endObj.dateTime + "Z").toInstant().toEpochMilli(),
                allDay = isAllDay,
                color = calendar.color,
                timeZone = "UTC",
                description = item.bodyPreview ?: "",
                location = item.location?.displayName ?: "",
                remoteId = remoteId,
            )
        } ?: emptyList()
    }

    private fun String.toLongId(): Long = hashCode().toLong().and(0x7FFFFFFFL)

    private fun String.toOutlookColor(): Int {
        return when (this.lowercase()) {
            "lightblue" -> 0xFF99CCFF.toInt()
            "lightgreen" -> 0xFF99FF99.toInt()
            "lightorange" -> 0xFFFFCC99.toInt()
            "lightred" -> 0xFFFF9999.toInt()
            "lightyellow" -> 0xFFFFFFCC.toInt()
            else -> 0xFF0078D4.toInt() // Default Outlook Blue
        }
    }
}