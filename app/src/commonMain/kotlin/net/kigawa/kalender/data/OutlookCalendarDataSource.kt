package net.kigawa.kalender.data

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.UserCalendar
import net.kigawa.kalender.util.formatLocalDateTimeNoOffset
import net.kigawa.kalender.util.parseIsoInstantMs

class OutlookCalendarDataSource(
    private val accessToken: String,
    private val ownerEmail: String,
    private val httpClient: HttpClient,
) : CalendarDataSource {

    private var cachedCalendars: List<UserCalendar>? = null

    private suspend fun get(url: String, extraHeaders: Map<String, String> = emptyMap()): JsonObject {
        val response = httpClient.get(url) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header(HttpHeaders.Accept, "application/json")
            extraHeaders.forEach { (k, v) -> header(k, v) }
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw Exception("Graph API Error ${response.status.value}: $text")
        }
        return Json.parseToJsonElement(text).jsonObject
    }

    private suspend fun post(url: String, body: JsonObject): JsonObject {
        val response = httpClient.post(url) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header(HttpHeaders.Accept, "application/json")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw Exception("Graph API Error ${response.status.value}: $text")
        }
        return Json.parseToJsonElement(text).jsonObject
    }

    private suspend fun patch(url: String, body: JsonObject): JsonObject {
        val response = httpClient.patch(url) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header(HttpHeaders.Accept, "application/json")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw Exception("Graph API Error ${response.status.value}: $text")
        }
        return Json.parseToJsonElement(text).jsonObject
    }

    private suspend fun httpDelete(url: String) {
        val response = httpClient.delete(url) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header(HttpHeaders.Accept, "application/json")
        }
        if (!response.status.isSuccess() && response.status.value != 204) {
            throw Exception("Graph API Error ${response.status.value}: ${response.bodyAsText()}")
        }
    }

    private fun buildEventJson(event: CalendarEvent): JsonObject {
        return buildJsonObject {
            put("subject", event.title)
            put("isAllDay", event.allDay)
            put("start", buildJsonObject {
                put("dateTime", formatLocalDateTimeNoOffset(event.startMs, TimeZone.UTC))
                put("timeZone", "UTC")
            })
            put("end", buildJsonObject {
                put("dateTime", formatLocalDateTimeNoOffset(event.endMs, TimeZone.UTC))
                put("timeZone", "UTC")
            })
            if (event.description.isNotEmpty()) {
                put("body", buildJsonObject {
                    put("contentType", "text")
                    put("content", event.description)
                })
            }
            if (event.location.isNotEmpty()) {
                put("location", buildJsonObject { put("displayName", event.location) })
            }
        }
    }

    suspend fun createEvent(calendarAccountName: String, event: CalendarEvent): CalendarEvent {
        val calId = calendarAccountName.encode()
        val url = "https://graph.microsoft.com/v1.0/me/calendars/$calId/events"
        val response = post(url, buildEventJson(event))
        val remoteId = response["id"]!!.jsonPrimitive.content
        val calendarId = cachedCalendars?.find { it.accountName == calendarAccountName }?.id ?: event.calendarId
        return event.copy(id = remoteId.toLongId(), remoteId = remoteId, calendarId = calendarId)
    }

    suspend fun updateEvent(calendarAccountName: String, event: CalendarEvent): CalendarEvent {
        require(event.remoteId.isNotEmpty()) { "remoteId が空です" }
        val calId = calendarAccountName.encode()
        val eventId = event.remoteId.encode()
        val url = "https://graph.microsoft.com/v1.0/me/calendars/$calId/events/$eventId"
        patch(url, buildEventJson(event))
        return event
    }

    suspend fun deleteEvent(calendarAccountName: String, remoteId: String) {
        require(remoteId.isNotEmpty()) { "remoteId が空です" }
        val calId = calendarAccountName.encode()
        val eventId = remoteId.encode()
        val url = "https://graph.microsoft.com/v1.0/me/calendars/$calId/events/$eventId"
        httpDelete(url)
    }

    override suspend fun fetchCalendars(): List<UserCalendar> {
        cachedCalendars?.let { return it }
        val items = get("https://graph.microsoft.com/v1.0/me/calendars")["value"]?.jsonArray
            ?: return emptyList()
        val result = items.map { itemEl ->
            val item = itemEl.jsonObject
            val id = item["id"]!!.jsonPrimitive.content
            UserCalendar(
                id = id.toLongId(),
                name = item["name"]?.jsonPrimitive?.contentOrNull() ?: "",
                color = (item["color"]?.jsonPrimitive?.contentOrNull() ?: "").toOutlookColor(),
                accountName = id,
                ownerEmail = ownerEmail,
            )
        }
        cachedCalendars = result
        return result
    }

    override suspend fun fetchEvents(startMs: Long, endMs: Long): List<CalendarEvent> =
        fetchCalendars().flatMap { calendar -> fetchCalendarEvents(calendar, startMs, endMs) }

    private suspend fun fetchCalendarEvents(calendar: UserCalendar, startMs: Long, endMs: Long): List<CalendarEvent> {
        val calId = calendar.accountName.encode()
        val start = formatLocalDateTimeNoOffset(startMs, TimeZone.UTC) + "Z"
        val end = formatLocalDateTimeNoOffset(endMs, TimeZone.UTC) + "Z"
        val url = "https://graph.microsoft.com/v1.0/me/calendars/$calId/calendarView" +
            "?startDateTime=$start&endDateTime=$end&\$select=id,subject,start,end,isAllDay,bodyPreview,location"

        val items = get(url, mapOf("Prefer" to "outlook.timezone=\"UTC\""))["value"]?.jsonArray
            ?: return emptyList()
        return items.map { itemEl ->
            val item = itemEl.jsonObject
            val startObj = item["start"]!!.jsonObject
            val endObj = item["end"]!!.jsonObject
            val isAllDay = item["isAllDay"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val remoteId = item["id"]!!.jsonPrimitive.content

            CalendarEvent(
                id = remoteId.toLongId(),
                calendarId = calendar.id,
                title = item["subject"]?.jsonPrimitive?.contentOrNull() ?: "(タイトルなし)",
                startMs = parseIsoInstantMs(startObj["dateTime"]!!.jsonPrimitive.content + "Z"),
                endMs = parseIsoInstantMs(endObj["dateTime"]!!.jsonPrimitive.content + "Z"),
                allDay = isAllDay,
                color = calendar.color,
                timeZone = "UTC",
                description = item["bodyPreview"]?.jsonPrimitive?.contentOrNull() ?: "",
                location = item["location"]?.jsonObject?.get("displayName")?.jsonPrimitive?.contentOrNull() ?: "",
                remoteId = remoteId,
            )
        }
    }

    private fun String.toLongId(): Long = hashCode().toLong().and(0x7FFFFFFFL)

    private fun String.toOutlookColor(): Int = when (lowercase()) {
        "lightblue" -> 0xFF99CCFF.toInt()
        "lightgreen" -> 0xFF99FF99.toInt()
        "lightorange" -> 0xFFFFCC99.toInt()
        "lightred" -> 0xFFFF9999.toInt()
        "lightyellow" -> 0xFFFFFFCC.toInt()
        else -> 0xFF0078D4.toInt() // Default Outlook Blue
    }

    private fun String.encode(): String = encodeURLParameter()
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content
