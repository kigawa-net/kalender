package net.kigawa.kalender.data

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.UserCalendar
import net.kigawa.kalender.util.formatIsoDate
import net.kigawa.kalender.util.formatIsoOffsetDateTime
import net.kigawa.kalender.util.parseHexColorOrNull
import net.kigawa.kalender.util.parseIsoDateStartMs
import net.kigawa.kalender.util.parseIsoInstantMs
import net.kigawa.kalender.util.systemZone
import net.kigawa.kalender.util.timeZoneOrNull

class GoogleCalendarDataSource(
    private val accessToken: String,
    private val ownerEmail: String,
    private val httpClient: HttpClient,
) : CalendarDataSource {

    private val eventColors = mapOf(
        "1" to 0xFFD50000.toInt(), "2" to 0xFFE67C73.toInt(),
        "3" to 0xFFF6BF26.toInt(), "4" to 0xFF33B679.toInt(),
        "5" to 0xFF0B8043.toInt(), "6" to 0xFF039BE5.toInt(),
        "7" to 0xFF3F51B5.toInt(), "8" to 0xFF7986CB.toInt(),
        "9" to 0xFF8E24AA.toInt(), "10" to 0xFF616161.toInt(),
        "11" to 0xFF795548.toInt(),
    )

    private var cachedCalendars: List<UserCalendar>? = null

    private suspend fun get(url: String): JsonObject {
        val response = httpClient.get(url) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw Exception("Google Calendar API Error ${response.status.value}: $text")
        }
        return Json.parseToJsonElement(text).jsonObject
    }

    private suspend fun post(url: String, body: JsonObject): JsonObject {
        val response = httpClient.post(url) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw Exception("Google Calendar API Error ${response.status.value}: $text")
        }
        return Json.parseToJsonElement(text).jsonObject
    }

    private suspend fun put(url: String, body: JsonObject): JsonObject {
        val response = httpClient.put(url) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw Exception("Google Calendar API Error ${response.status.value}: $text")
        }
        return Json.parseToJsonElement(text).jsonObject
    }

    private suspend fun httpDelete(url: String) {
        val response = httpClient.delete(url) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (!response.status.isSuccess() && response.status.value != 204) {
            throw Exception("Google Calendar API Error ${response.status.value}: ${response.bodyAsText()}")
        }
    }

    override suspend fun fetchCalendars(): List<UserCalendar> {
        cachedCalendars?.let { return it }
        val items = get("https://www.googleapis.com/calendar/v3/users/me/calendarList")["items"]
            ?.jsonArray ?: return emptyList()
        val result = items.mapNotNull { itemEl ->
            val item = itemEl.jsonObject
            if (item["selected"]?.jsonPrimitive?.booleanOrTrue() != true) return@mapNotNull null
            val id = item["id"]!!.jsonPrimitive.content
            UserCalendar(
                id = id.toLongId(),
                name = item["summary"]?.jsonPrimitive?.contentOrNull() ?: "",
                color = item["backgroundColor"]?.jsonPrimitive?.contentOrNull()?.let { parseHexColorOrNull(it) }
                    ?: 0xFF808080.toInt(),
                accountName = id,
                ownerEmail = ownerEmail,
            )
        }
        cachedCalendars = result
        return result
    }

    override suspend fun fetchEvents(startMs: Long, endMs: Long): List<CalendarEvent> =
        fetchCalendars().flatMap { calendar -> fetchCalendarEvents(calendar, startMs, endMs) }

    suspend fun createEvent(calendarAccountName: String, event: CalendarEvent): CalendarEvent {
        val calId = encode(calendarAccountName)
        val url = "https://www.googleapis.com/calendar/v3/calendars/$calId/events"
        val response = post(url, buildEventJson(event))
        val remoteId = response["id"]!!.jsonPrimitive.content
        val calendarId = cachedCalendars?.find { it.accountName == calendarAccountName }?.id ?: event.calendarId
        return event.copy(id = remoteId.toLongId(), remoteId = remoteId, calendarId = calendarId)
    }

    suspend fun updateEvent(calendarAccountName: String, event: CalendarEvent): CalendarEvent {
        require(event.remoteId.isNotEmpty()) { "remoteId が空です" }
        val calId = encode(calendarAccountName)
        val eventId = encode(event.remoteId)
        val url = "https://www.googleapis.com/calendar/v3/calendars/$calId/events/$eventId"
        put(url, buildEventJson(event))
        return event
    }

    suspend fun deleteEvent(calendarAccountName: String, remoteId: String) {
        require(remoteId.isNotEmpty()) { "remoteId が空です" }
        val calId = encode(calendarAccountName)
        val eventId = encode(remoteId)
        val url = "https://www.googleapis.com/calendar/v3/calendars/$calId/events/$eventId"
        httpDelete(url)
    }

    private fun buildEventJson(event: CalendarEvent): JsonObject {
        val tz = event.timeZone.ifEmpty { systemZone().id }
        return buildJsonObject {
            put("summary", event.title)
            if (event.description.isNotEmpty()) put("description", event.description)
            if (event.location.isNotEmpty()) put("location", event.location)
            if (event.allDay) {
                put("start", buildJsonObject { put("date", formatIsoDate(event.startMs)) })
                put("end", buildJsonObject { put("date", formatIsoDate(event.endMs)) })
            } else {
                val zone = timeZoneOrNull(tz) ?: systemZone()
                put("start", buildJsonObject {
                    put("dateTime", formatIsoOffsetDateTime(event.startMs, zone))
                    put("timeZone", tz)
                })
                put("end", buildJsonObject {
                    put("dateTime", formatIsoOffsetDateTime(event.endMs, zone))
                    put("timeZone", tz)
                })
            }
        }
    }

    private suspend fun fetchCalendarEvents(calendar: UserCalendar, startMs: Long, endMs: Long): List<CalendarEvent> {
        val calId = encode(calendar.accountName)
        val timeMin = encode(parseIsoInstantMsToIso(startMs))
        val timeMax = encode(parseIsoInstantMsToIso(endMs))
        val url = "https://www.googleapis.com/calendar/v3/calendars/$calId/events" +
            "?timeMin=$timeMin&timeMax=$timeMax&singleEvents=true&orderBy=startTime&maxResults=250"
        val items = get(url)["items"]?.jsonArray ?: return emptyList()
        return items.map { itemEl ->
            val item = itemEl.jsonObject
            val startObj = item["start"]?.jsonObject
            val endObj = item["end"]?.jsonObject
            val (startEpoch, allDay) = parseDateTime(startObj)
            val (endEpoch, _) = parseDateTime(endObj)
            val colorId = item["colorId"]?.jsonPrimitive?.contentOrNull()
            val remoteId = item["id"]!!.jsonPrimitive.content
            CalendarEvent(
                id = remoteId.toLongId(),
                calendarId = calendar.id,
                title = item["summary"]?.jsonPrimitive?.contentOrNull() ?: "",
                startMs = startEpoch,
                endMs = endEpoch,
                allDay = allDay,
                color = colorId?.let { eventColors[it] } ?: calendar.color,
                timeZone = startObj?.get("timeZone")?.jsonPrimitive?.contentOrNull() ?: "",
                description = item["description"]?.jsonPrimitive?.contentOrNull() ?: "",
                location = item["location"]?.jsonPrimitive?.contentOrNull() ?: "",
                remoteId = remoteId,
            )
        }
    }

    private fun parseDateTime(obj: JsonObject?): Pair<Long, Boolean> {
        obj ?: return 0L to false
        val dt = obj["dateTime"]?.jsonPrimitive?.contentOrNull()
        val d = obj["date"]?.jsonPrimitive?.contentOrNull()
        return when {
            !dt.isNullOrEmpty() -> parseIsoInstantMs(dt) to false
            !d.isNullOrEmpty() -> parseIsoDateStartMs(d) to true
            else -> 0L to false
        }
    }

    private fun parseIsoInstantMsToIso(ms: Long): String {
        // timeMin/timeMax はUTCのRFC3339表記であれば十分
        val zone = kotlinx.datetime.TimeZone.UTC
        return formatIsoOffsetDateTime(ms, zone)
    }

    private fun String.toLongId(): Long = hashCode().toLong().and(0x7FFFFFFFL)
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content

private fun kotlinx.serialization.json.JsonPrimitive.booleanOrTrue(): Boolean =
    booleanOrNull ?: true

private val kotlinx.serialization.json.JsonPrimitive.booleanOrNull: Boolean?
    get() = content.toBooleanStrictOrNull()

private fun encode(value: String): String = value.encodeURLParameter()
