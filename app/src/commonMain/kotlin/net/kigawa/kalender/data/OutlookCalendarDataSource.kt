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
import net.kigawa.kalender.model.EventSource
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
            // calendarView の発生インスタンスはその発生日の start/end を持つ。
            // マスターにそのまま当てると初回発生日が上書きされ series がずれるため、
            // マスターの開始日時を基準に、時間差分のみを適用する。
            val master = runCatching { get(masterUrl) }.getOrNull() ?: run {
                // マスター取得に失敗した場合はPATCHせず、エラーを投げる(fail closed)。
                // 失敗時にPATCHすると、選択した発生日でマスターが上書きされ、シリーズ全体がずれる。
                throw Exception("繰り返し予定のマスター情報を取得できませんでした")
            }
            val masterStart = master?.get("start")?.jsonObject?.get("dateTime")?.jsonPrimitive?.content
            val patchJson = if (masterStart != null && masterStart != event.startMs.toString()) {
                // 差分は分単位で扱う（タイムゾーンはUTC固定で送る）
                val deltaMinutes =
                    (event.startMs - (event.originalStartMs ?: event.startMs)) / 60_000L
                val baseStartMs = runCatching {
                    parseIsoInstantMs(normalizeIsoInstant(masterStart))
                }.getOrNull() ?: event.startMs
                event.copy(
                    startMs = baseStartMs + deltaMinutes * 60_000L,
                    endMs = baseStartMs + deltaMinutes * 60_000L + (event.endMs - event.startMs),
                ).let { buildEventJson(it) }
            } else {
                buildEventJson(event)
            }
            // 繰り返しルールの変更を処理:
            // - null なら繰り返しを「なし」に変更するために recurrence を削除
            // - 空でない文字列なら新しいルールを設定
            val finalPatchJson = if (event.recurrenceRule.isNullOrEmpty()) {
                // 繰り返しを「なし」に変更する場合、recurrence を削除して送信
                // Graph API では recurrence を null にすると繰り返しが無効になる
                buildJsonObject {
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
                    // recurrence を含めないことで、既存の繰り返し設定を削除する
                    // Graph API の PATCH で recurrence を省略すると、既存値が保持されるため、
                    // 明示的に null を設定する必要がある
                    put("recurrence", kotlinx.serialization.json.JsonNull)
                }
            } else {
                patchJson
            }
            patch(masterUrl, finalPatchJson)
            return event.copy(remoteId = masterId)
        }

        // THIS_AND_FOLLOWING: マスターを endDate で打ち切り、この予定から新シリーズを作る
        val overwrittenOriginalStart = event.originalStartMs ?: event.startMs
        val masterRrule = fetchMasterRrule(masterUrl)
        patch(masterUrl, buildSeriesCutJson(masterUrl, overwrittenOriginalStart))

        // calendarView のインスタンスは recurrence が null なので、
        // マスターから取得した rrule を引き継ぐ。これがないと
        // 「これ以降」を選んだときに以降の予定が全て消えて単発イベントだけが残る。
        val newSeries = event.copy(
            id = 0L,
            remoteId = "",
            recurringEventId = null,
            originalStartMs = null,
            recurrenceRule = event.recurrenceRule ?: masterRrule,
        )
        return createEvent(calendarAccountName, newSeries)
    }

    /** 繰り返し予定のマスターから RRULE 文字列を取得する */
    private suspend fun fetchMasterRrule(masterUrl: String): String? {
        val master = runCatching { get(masterUrl) }.getOrNull() ?: return null
        return OutlookRecurrenceConverter.graphToRrule(master["recurrence"]?.jsonObject)
    }

    companion object {
        /** Z サフィックスが無い/二重の ISO 文字列を parseIsoInstantMs 用に正規化する */
        internal fun normalizeIsoInstant(raw: String): String {
            val trimmed = raw.trim()
            return if (trimmed.endsWith("Z", ignoreCase = true)) trimmed else "${trimmed}Z"
        }
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
        patch(masterUrl, buildSeriesCutJson(masterUrl, untilMs))
    }

    /** 繰り返し予定のマスターを指定日で終了させるための部分更新JSON */
    /**
     * 繰り返し予定のマスターを指定日で終了させるための部分更新JSON。
     *
     * Graph では `recurrence` が1つのプロパティなので `range` だけを
     * 部分更新できない。マスターから取得した既存の recurrence をベースに
     * range だけ差し替えた完全な recurrence を送る必要がある。
     * (endDate range には type/endDate に加えて startDate が必須)
     */
    private suspend fun buildSeriesCutJson(masterUrl: String, untilMs: Long): JsonObject {
        val existing = runCatching { get(masterUrl) }.getOrNull()
        val existingRecurrence = existing?.get("recurrence")?.jsonObject

        val range = buildJsonObject {
            put("type", "endDate")
            put("endDate", formatIsoDate(untilMs - 24 * 60 * 60 * 1000L))
            // startDate は必須。既存値を尊重し、取得できなければ開始日で埋める
            put("startDate", existingRecurrence?.get("range")?.jsonObject?.get("startDate")?.jsonPrimitive?.content
                ?: formatIsoDate(untilMs))
        }

        return buildJsonObject {
            put(
                "recurrence",
                buildJsonObject {
                    // pattern は必須。既存値があればそれを使う
                    existingRecurrence?.get("pattern")?.jsonObject?.let { put("pattern", it) }
                        ?: put(
                            "pattern",
                            buildJsonObject {
                                put("type", "daily")
                                put("interval", 1)
                            },
                        )
                    put("range", range)
                },
            )
        }
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
                source = EventSource.OUTLOOK,
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
        // 注意: シリーズの元開始時刻は Graph では `originalStart`(DateTimeOffset文字列)。
        //       `originalStartTime` というプロパティは存在せず、$select に指定すると
        //       Graph がエラーを返して全Outlook予定の取得が失敗する。
        val url = "https://graph.microsoft.com/v1.0/me/calendars/$calId/calendarView" +
                "?startDateTime=$start&endDateTime=$end&\$select=id,subject,start,end,isAllDay,bodyPreview,location,recurrence,seriesMasterId,originalStart"

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
            // originalStart は UTC の DateTimeOffset 文字列。
            // "2026-10-09T10:00:00Z" のように Z 付きで返る場合もあるため、
            // サフィックス有無を見てから parseIsoInstantMs に渡す。
            val originalStartMs = item["originalStart"]?.jsonPrimitive?.content?.let { raw ->
                val normalized = if (raw.endsWith("Z", ignoreCase = true)) raw else "${raw}Z"
                runCatching { parseIsoInstantMs(normalized) }.getOrNull()
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
                source = EventSource.OUTLOOK,
                recurrenceRule = recurrenceRule,
                recurringEventId = recurringEventId,
                originalStartMs = originalStartMs,
            )
        }
    }

    /**
     * 文字列IDをLongに変換する。
     * hashCode()の衝突リスクを軽減するため、文字列の各バイトをハッシュに組み込む。
     */
    private fun String.toLongId(): Long {
        var hash = 0L
        for (i in indices) {
            hash = hash * 31L + this[i].code.toLong()
        }
        return hash.and(0x7FFFFFFFL)
    }

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