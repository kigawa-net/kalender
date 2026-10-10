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
import net.kigawa.kalender.data.jsonArray
import net.kigawa.kalender.data.jsonObject
import net.kigawa.kalender.data.optJSONObject
import net.kigawa.kalender.data.optJSONArray
import net.kigawa.kalender.data.optString
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.model.RecurrenceEditScope
import net.kigawa.kalender.model.UserCalendar
import net.kigawa.kalender.util.formatIsoDate
import net.kigawa.kalender.util.formatIsoDateAtMidnight
import net.kigawa.kalender.util.formatLocalDateTimeNoOffset
import net.kigawa.kalender.util.parseIsoInstantMs
import net.kigawa.kalender.util.platformLogError

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
            val errorMsg = "Graph API Error ${response.status.value}: $text"
            platformLogError("OutlookCalendarDataSource", "GET $url failed: $errorMsg")
            throw Exception(errorMsg)
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
            val errorMsg = "Graph API Error ${response.status.value}: $text"
            platformLogError("OutlookCalendarDataSource", "POST $url failed: $errorMsg")
            throw Exception(errorMsg)
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
            val errorMsg = "Graph API Error ${response.status.value}: $text"
            platformLogError("OutlookCalendarDataSource", "PATCH $url failed: $errorMsg")
            throw Exception(errorMsg)
        }
        return Json.parseToJsonElement(text).jsonObject
    }

    private suspend fun httpDelete(url: String) {
        val response = httpClient.delete(url) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header(HttpHeaders.Accept, "application/json")
        }
        if (!response.status.isSuccess() && response.status.value != 204) {
            val errorMsg = "Graph API Error ${response.status.value}: ${response.bodyAsText()}"
            platformLogError("OutlookCalendarDataSource", "DELETE $url failed: $errorMsg")
            throw Exception(errorMsg)
        }
    }

    private fun buildEventJson(event: CalendarEvent): JsonObject {
        return buildJsonObject {
            put("subject", event.title)
            put("isAllDay", event.allDay)
            put("start", buildJsonObject {
                put(
                    "dateTime",
                    if (event.allDay) formatIsoDateAtMidnight(event.startMs)
                    else formatLocalDateTimeNoOffset(event.startMs, TimeZone.UTC)
                )
                put("timeZone", "UTC")
            })
            put("end", buildJsonObject {
                put(
                    "dateTime",
                    if (event.allDay) formatIsoDateAtMidnight(event.endMs)
                    else formatLocalDateTimeNoOffset(event.endMs, TimeZone.UTC)
                )
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
            // 繰り返し設定（RRULE → Microsoft Graph recurrence）
            // 注意: range.startDate/endDate は Graph API 上 "yyyy-MM-dd" 形式(Date)が必須。
            //       start.dateTime 用の formatIsoDateAtMidnight は使えない。
            val graphRecurrence = event.recurrenceRule?.let { rrule ->
                OutlookRecurrenceConverter.rruleToGraph(rrule, event.startMs) { ms ->
                    formatIsoDate(ms)
                }
            }
            if (graphRecurrence != null) {
                put("recurrence", graphRecurrence)
            }
            if (event.recurringEventId != null && event.recurringEventId.isNotEmpty()) {
                put("seriesMasterId", event.recurringEventId)
            }
            if (event.originalStartMs != null) {
                // Outlookでは元の開始時刻を明示的に扱わないが、念のため
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
        val calId = calendarAccountName.encode()
        val isRecurring = !event.recurrenceRule.isNullOrEmpty() || !event.recurringEventId.isNullOrEmpty()

        if (!isRecurring || scope == RecurrenceEditScope.THIS_EVENT) {
            val url = "https://graph.microsoft.com/v1.0/me/calendars/$calId/events/${event.remoteId.encode()}"
            patch(url, buildEventJson(event))
            return event
        }

        val masterId = event.recurringEventId?.takeIf { it.isNotBlank() } ?: event.remoteId
        val masterUrl = "https://graph.microsoft.com/v1.0/me/calendars/$calId/events/${masterId.encode()}"

        if (scope == RecurrenceEditScope.ALL) {
            patch(masterUrl, buildEventJson(event))
            return event.copy(remoteId = masterId)
        }

        // THIS_AND_FOLLOWING: マスターを endDate で打ち切り、この予定から新シリーズを作る
        val overwrittenOriginalStart = event.originalStartMs ?: event.startMs
        patch(masterUrl, buildSeriesCutJson(overwrittenOriginalStart))

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
        recurringEventId: String? = null,
    ) {
        require(remoteId.isNotEmpty()) { "remoteId が空です" }
        val calId = calendarAccountName.encode()

        if (scope == RecurrenceEditScope.THIS_EVENT) {
            val url = "https://graph.microsoft.com/v1.0/me/calendars/$calId/events/${remoteId.encode()}"
            httpDelete(url)
            return
        }

        val masterId = recurringEventId?.takeIf { it.isNotBlank() } ?: remoteId
        val masterUrl = "https://graph.microsoft.com/v1.0/me/calendars/$calId/events/${masterId.encode()}"

        if (scope == RecurrenceEditScope.ALL) {
            httpDelete(masterUrl)
            return
        }

        // THIS_AND_FOLLOWING: マスターをこの予定の前日で終了させる
        val untilMs = originalStartMs ?: return
        patch(masterUrl, buildSeriesCutJson(untilMs))
    }

    /** 繰り返し予定のマスターを指定日で終了させるための部分更新JSON */
    private fun buildSeriesCutJson(untilMs: Long): JsonObject = buildJsonObject {
        put(
            "recurrence",
            buildJsonObject {
                put(
                    "range",
                    buildJsonObject {
                        put("type", "endDate")
                        put("endDate", formatIsoDate(untilMs - 24 * 60 * 60 * 1000L))
                    },
                )
            },
        )
    }

    override suspend fun fetchCalendars(): List<UserCalendar> {
        cachedCalendars?.let { return it }
        val items = get("https://graph.microsoft.com/v1.0/me/calendars")
            .jsonArray("value") ?: return emptyList()
        val result = items.map { itemEl ->
            val item = itemEl.jsonObject
            val id = item["id"]!!.jsonPrimitive!!.content
            UserCalendar(
                id = id.toLongId(),
                name = item.optString("name", ""),
                color = item.optString("color").toOutlookColor(),
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
                "?startDateTime=$start&endDateTime=$end&\$select=id,subject,start,end,isAllDay,bodyPreview,location,recurrence,seriesMasterId,originalStartTime"

        val items = get(url, mapOf("Prefer" to "outlook.timezone=\"UTC\""))
            .jsonArray("value") ?: return emptyList()
        return items.map { itemEl ->
            val item = itemEl.jsonObject
            val startObj = item["start"]!!.jsonObject
            val endObj = item["end"]!!.jsonObject
            val isAllDay = item["isAllDay"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val remoteId = item["id"]!!.jsonPrimitive!!.content
            val recurrenceRule = OutlookRecurrenceConverter.graphToRrule(
                item["recurrence"]?.jsonObject,
            )
            val recurringEventId = item["seriesMasterId"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            val originalStartMs = item["originalStartTime"]?.jsonObject?.let { obj ->
                obj["dateTime"]?.jsonPrimitive?.content?.let { parseIsoInstantMs(it + "Z") }
            }

            CalendarEvent(
                id = remoteId.toLongId(),
                calendarId = calendar.id,
                title = item.optString("subject", "(タイトルなし)"),
                startMs = parseIsoInstantMs(startObj["dateTime"]!!.jsonPrimitive!!.content + "Z"),
                endMs = parseIsoInstantMs(endObj["dateTime"]!!.jsonPrimitive!!.content + "Z"),
                allDay = isAllDay,
                color = calendar.color,
                timeZone = "UTC",
                description = item.optString("bodyPreview", ""),
                location = item["location"]?.jsonObject?.optString("displayName", "") ?: "",
                remoteId = remoteId,
                recurrenceRule = recurrenceRule,
                recurringEventId = recurringEventId,
                originalStartMs = originalStartMs,
            )
        }
    }

    private fun String.toLongId(): Long = hashCode().toLong().and(0x7FFFFFFFL)

    private fun String.toOutlookColor(): Int {
        return when (lowercase()) {
            "lightblue" -> 0xFF99CCFF.toInt()
            "lightgreen" -> 0xFF99FF99.toInt()
            "lightorange" -> 0xFFFFCC99.toInt()
            "lightred" -> 0xFFFF9999.toInt()
            "lightyellow" -> 0xFFFFFFCC.toInt()
            else -> 0xFF0078D4.toInt() // Default Outlook Blue
        }
    }

    private fun String.encode(): String = encodeURLParameter()
}