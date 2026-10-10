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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.RecurrenceEditScope
import net.kigawa.kalender.model.UserCalendar
import net.kigawa.kalender.util.formatIsoDate
import net.kigawa.kalender.util.formatIsoOffsetDateTime
import net.kigawa.kalender.util.parseHexColorOrNull
import net.kigawa.kalender.util.parseIsoDateStartMs
import net.kigawa.kalender.util.parseIsoInstantMs
import net.kigawa.kalender.util.platformLogError
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
            val errorMsg = "Google Calendar API Error ${response.status.value}: $text"
            platformLogError("GoogleCalendarDataSource", "GET $url failed: $errorMsg")
            throw Exception(errorMsg)
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
            val errorMsg = "Google Calendar API Error ${response.status.value}: $text"
            platformLogError("GoogleCalendarDataSource", "POST $url failed: $errorMsg")
            throw Exception(errorMsg)
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
            val errorMsg = "Google Calendar API Error ${response.status.value}: $text"
            platformLogError("GoogleCalendarDataSource", "PUT $url failed: $errorMsg")
            throw Exception(errorMsg)
        }
        return Json.parseToJsonElement(text).jsonObject
    }

    private suspend fun httpDelete(url: String) {
        val response = httpClient.delete(url) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (!response.status.isSuccess() && response.status.value != 204) {
            val errorMsg = "Google Calendar API Error ${response.status.value}: ${response.bodyAsText()}"
            platformLogError("GoogleCalendarDataSource", "DELETE $url failed: $errorMsg")
            throw Exception(errorMsg)
        }
    }

    override suspend fun fetchCalendars(): List<UserCalendar> {
        cachedCalendars?.let { return it }
        val items = get("https://www.googleapis.com/calendar/v3/users/me/calendarList")["items"]
            ?.jsonArray ?: return emptyList()
        val result = items.mapNotNull { itemEl ->
            val item = itemEl.jsonObject
            if (item["selected"]?.jsonPrimitive?.booleanOrTrue() != true) return@mapNotNull null
            val id = item["id"]!!.jsonPrimitive!!.content
            UserCalendar(
                id = id.toLongId(),
                name = item["summary"]?.jsonPrimitive?.content ?: "",
                color = item["backgroundColor"]?.jsonPrimitive?.content?.let { parseHexColorOrNull(it) }
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
        val remoteId = response["id"]!!.jsonPrimitive!!.content
        val calendarId = cachedCalendars?.find { it.accountName == calendarAccountName }?.id ?: event.calendarId
        return event.copy(id = remoteId.toLongId(), remoteId = remoteId, calendarId = calendarId)
    }

    /**
     * 予定を更新する。
     *
     * @param scope 繰り返し予定の編集対象（繰り返しなしの予定では無視される）
     */
    suspend fun updateEvent(
        calendarAccountName: String,
        event: CalendarEvent,
        scope: RecurrenceEditScope = RecurrenceEditScope.ALL,
    ): CalendarEvent {
        require(event.remoteId.isNotEmpty()) { "remoteId が空です" }
        val calId = encode(calendarAccountName)
        val isRecurring = !event.recurrenceRule.isNullOrEmpty() || !event.recurringEventId.isNullOrEmpty()

        // 繰り返しなし、または「この予定のみ」の場合はインスタンス（例外）を直接更新する
        if (!isRecurring || scope == RecurrenceEditScope.THIS_EVENT) {
            val url = "https://www.googleapis.com/calendar/v3/calendars/$calId/events/${encode(event.remoteId)}"
            put(url, buildEventJson(event))
            return event
        }

        val masterId = seriesMasterId(event)
        val masterUrl = "https://www.googleapis.com/calendar/v3/calendars/$calId/events/${encode(masterId)}"

        if (scope == RecurrenceEditScope.ALL) {
            put(masterUrl, buildEventJson(event))
            return event.copy(remoteId = masterId)
        }

        // THIS_AND_FOLLOWING: 既存シリーズをこの予定の直前で打ち切り、この予定から新シリーズを作る
        val overwrittenOriginalStart = event.originalStartMs ?: event.startMs
        val existingRrule = fetchRrule(masterUrl)
        if (existingRrule.isNullOrEmpty()) {
            put(masterUrl, buildEventJson(event))
            return event.copy(remoteId = masterId)
        }
        val cut = cutRruleUntil(existingRrule, overwrittenOriginalStart)
        put(masterUrl, buildJsonObject {
            put("recurrence", listOf(JsonPrimitive(cut)).toJsonArray())
        })

        val newSeries = event.copy(
            id = 0L,
            remoteId = "",
            recurringEventId = null,
            originalStartMs = null,
        )
        return createEvent(calendarAccountName, newSeries)
    }

    /**
     * 予定を削除する。
     *
     * @param scope 繰り返し予定の削除対象（繰り返しなしの予定では無視される）
     * @param originalStartMs THIS_AND_FOLLOWING において打ち切り基準とする開始時刻
     */
    suspend fun deleteEvent(
        calendarAccountName: String,
        remoteId: String,
        scope: RecurrenceEditScope = RecurrenceEditScope.ALL,
        originalStartMs: Long? = null,
    ) {
        require(remoteId.isNotEmpty()) { "remoteId が空です" }
        val calId = encode(calendarAccountName)

        if (scope == RecurrenceEditScope.THIS_EVENT) {
            val url = "https://www.googleapis.com/calendar/v3/calendars/$calId/events/${encode(remoteId)}"
            httpDelete(url)
            return
        }

        val masterUrl = "https://www.googleapis.com/calendar/v3/calendars/$calId/events/${encode(seriesMasterIdOf(remoteId))}"
        if (scope == RecurrenceEditScope.ALL) {
            httpDelete(masterUrl)
            return
        }

        // THIS_AND_FOLLOWING: マスターをこの予定の直前で打ち切る（以降のインスタンスは消える）
        val untilMs = originalStartMs ?: return
        val existingRrule = fetchRrule(masterUrl)
        if (existingRrule.isNullOrEmpty()) {
            httpDelete(masterUrl)
            return
        }
        val cut = cutRruleUntil(existingRrule, untilMs)
        put(masterUrl, buildJsonObject {
            put("recurrence", listOf(JsonPrimitive(cut)).toJsonArray())
        })
    }

    /** 繰り返し予定からシリーズ（マスター）のイベントIDを導出する */
    private fun seriesMasterId(event: CalendarEvent): String {
        event.recurringEventId?.takeIf { it.isNotBlank() }?.let { return it }
        return seriesMasterIdOf(event.remoteId)
    }

    /**
     * Google Calendar のインスタンスIDは `{masterId}_{RFC3339相当}` なので、
     * 末尾のタイムスタンプ部分を除去してマスターIDを得る。
     */
    private fun seriesMasterIdOf(remoteId: String): String {
        val separator = remoteId.lastIndexOf('_')
        if (separator <= 0) return remoteId
        val suffix = remoteId.substring(separator + 1)
        // タイムスタンプらしい形式(20240101T100000Z)のみマスターIDとして扱う
        val looksLikeTimestamp = suffix.length >= 8 && suffix.all { it.isDigit() || it == 'T' || it == 'Z' }
        return if (looksLikeTimestamp) remoteId.substring(0, separator) else remoteId
    }

    private suspend fun fetchRrule(masterUrl: String): String? {
        val response = runCatching { get(masterUrl) }.getOrNull() ?: return null
        return response["recurrence"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive?.content }
            ?.joinToString("\n")
    }

    /**
     * RRULE に UNTIL を付与して [untilMs] の直前で終了させる。
     * 既存の UNTIL / COUNT は除去して上書きする。
     */
    private fun cutRruleUntil(rrule: String, untilMs: Long): String {
        val parts = rrule.split('\n', ';')
            .filter { it.isNotBlank() }
            .filterNot { it.startsWith("UNTIL=", ignoreCase = true) }
            .filterNot { it.startsWith("COUNT=", ignoreCase = true) }
            .toMutableList()
        val until = formatIsoBasic(untilMs - 1)
        parts.add("UNTIL=$until")
        return parts.joinToString("\n")
    }

    /** UTCの "yyyyMMddTHHmmssZ" 形式（RRULE の UNTIL 用） */
    private fun formatIsoBasic(ms: Long): String {
        val formatted = formatIsoOffsetDateTime(ms, kotlinx.datetime.TimeZone.UTC)
        return formatted.replace("-", "").replace(":", "").let {
            // "yyyyMMddTHHmmss+00:00" → "yyyyMMddTHHmmssZ"
            it.substringBefore('+').let { base ->
                if (base.endsWith("Z")) base else "${base}Z"
            }
        }
    }

    private fun buildEventJson(event: CalendarEvent): JsonObject {
        val tz = event.timeZone.ifEmpty { systemZone().id }
        return buildJsonObject {
            put("summary", event.title)
            if (event.description.isNotEmpty()) put("description", event.description)
            if (event.location.isNotEmpty()) put("location", event.location)
            if (event.recurrenceRule != null && event.recurrenceRule.isNotEmpty()) {
                val recurrenceArray = event.recurrenceRule?.split("\n")?.map { JsonPrimitive(it) } ?: emptyList()
                put("recurrence", recurrenceArray.toJsonArray())
            }
            if (event.recurringEventId != null && event.recurringEventId.isNotEmpty()) {
                put("recurringEventId", event.recurringEventId)
            }
            if (event.originalStartMs != null) {
                put("originalStartTime", buildJsonObject {
                    val zone = timeZoneOrNull(event.timeZone) ?: systemZone()
                    put("dateTime", formatIsoOffsetDateTime(event.originalStartMs!!, timeZoneOrNull(event.timeZone) ?: systemZone()))
                    put("timeZone", tz)
                })
            }
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
            val colorId = item["colorId"]?.jsonPrimitive?.content
            val remoteId = item["id"]!!.jsonPrimitive!!.content
            val recurrenceRule = item["recurrence"]?.jsonArray?.joinToString("\n")
            val recurringEventId = item["recurringEventId"]?.jsonPrimitive?.content
            val originalStartMs = item["originalStartTime"]?.jsonObject?.let { obj ->
                parseDateTime(obj).first
            }
            CalendarEvent(
                id = remoteId.toLongId(),
                calendarId = calendar.id,
                title = item["summary"]?.jsonPrimitive?.content ?: "",
                startMs = startEpoch,
                endMs = endEpoch,
                allDay = allDay,
                color = colorId?.let { eventColors[it] } ?: calendar.color,
                timeZone = startObj?.get("timeZone")?.jsonPrimitive?.content ?: "",
                description = item["description"]?.jsonPrimitive?.content ?: "",
                location = item["location"]?.jsonPrimitive?.content ?: "",
                remoteId = remoteId,
                recurrenceRule = recurrenceRule,
                recurringEventId = recurringEventId,
                originalStartMs = originalStartMs,
            )
        }
    }

    private fun parseDateTime(obj: JsonObject?): Pair<Long, Boolean> {
        obj ?: return 0L to false
        val dt = obj["dateTime"]?.jsonPrimitive?.content
        val d = obj["date"]?.jsonPrimitive?.content
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

private fun kotlinx.serialization.json.JsonPrimitive.booleanOrTrue(): Boolean =
    booleanOrNull ?: true

private val kotlinx.serialization.json.JsonPrimitive.booleanOrNull: Boolean?
    get() = content.toBooleanStrictOrNull()

private fun encode(value: String): String = value.encodeURLParameter()
