@file:OptIn(kotlin.time.ExperimentalTime::class)

package net.kigawa.kalender.data

import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.kigawa.kalender.model.Frequency
import net.kigawa.kalender.model.RecurrenceRule
import net.kigawa.kalender.util.parseIsoInstantMs
import net.kigawa.kalender.util.platformLogWarning
/**
 * RRULE(RFC 5545)と Microsoft Graph の recurrence オブジェクトの相互変換。
 *
 * Google Calendar は RRULE をそのまま扱えるため、Outlook(OutlookCalendarDataSource)側でのみ使用する。
 * Graph側で表現できないRRULE忠実度は落ちるため、変換時は警告を出して近似する。
 */
internal object OutlookRecurrenceConverter {

    private val json = Json { ignoreUnknownKeys = true }

    /** RRULE文字列 → Microsoft Graph recurrence オブジェクト */
    fun rruleToGraph(rrule: String, startMs: Long, formatDate: (Long) -> String): JsonObject? {
        val rule = RecurrenceRule.fromRRule(rrule)
        return ruleToGraph(rule, startMs, formatDate)
    }

    fun ruleToGraph(rule: RecurrenceRule, startMs: Long, formatDate: (Long) -> String): JsonObject? {
        if (rule.frequency == net.kigawa.kalender.model.Frequency.NONE) return null

        val pattern = buildJsonObject {
            when (rule.frequency) {
                net.kigawa.kalender.model.Frequency.DAILY -> {
                    put("type", "daily")
                    put("interval", rule.interval.coerceAtLeast(1))
                }

                net.kigawa.kalender.model.Frequency.WEEKLY -> {
                    put("type", "weekly")
                    put("interval", rule.interval.coerceAtLeast(1))
                    val days = if (rule.byDay.isEmpty()) listOf(1) else rule.byDay
                    put(
                        "daysOfWeek",
                        kotlinx.serialization.json.JsonArray(
                            days.map { kotlinx.serialization.json.JsonPrimitive(graphDayName(it)) },
                        ),
                    )
                }

                net.kigawa.kalender.model.Frequency.MONTHLY -> {
                    put("type", "absoluteMonthly")
                    put("interval", rule.interval.coerceAtLeast(1))
                    val day = rule.byMonthDay.firstOrNull() ?: 1
                    put("dayOfMonth", day.coerceIn(1, 31))
                }

                net.kigawa.kalender.model.Frequency.YEARLY -> {
                    put("type", "absoluteYearly")
                    put("interval", rule.interval.coerceAtLeast(1))
                    val day = rule.byMonthDay.firstOrNull() ?: 1
                    val month = rule.byMonth.firstOrNull() ?: 1
                    put("dayOfMonth", day.coerceIn(1, 31))
                    put("month", month.coerceIn(1, 12))
                }

                net.kigawa.kalender.model.Frequency.NONE -> return null
            }
        }

        return buildJsonObject {
            put("pattern", pattern)
            put(
                "range",
                buildJsonObject {
                    put("startDate", formatDate(startMs))
                    when {
                        rule.count != null -> {
                            put("type", "numbered")
                            put("numberOfOccurrences", rule.count)
                        }

                        rule.until != null -> {
                            put("type", "endDate")
                            // UNTIL は RRULE 上 UTC のインスタントなので、endDate も UTC で確定させる。
                            // 呼び出し元の formatDate はシステムタイムゾーン基準になり得るため使わない。
                            put("endDate", formatUtcDate(rule.until))
                        }

                        else -> put("type", "noEnd")
                    }
                },
            )
        }
    }

    /** Microsoft Graph recurrence オブジェクト → 繰り返しルールのRRULE文字列 */
    fun graphToRrule(recurrence: JsonObject?): String? {
        if (recurrence == null) return null
        val pattern = recurrence["pattern"]?.jsonObject ?: return null
        val range = recurrence["range"]?.jsonObject

        val type = pattern["type"]?.jsonPrimitive?.content ?: return null
        val interval = pattern["interval"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1

        val rule = when (type) {
            "daily" -> RecurrenceRule(
                frequency = net.kigawa.kalender.model.Frequency.DAILY,
                interval = interval,
            )

            "weekly" -> {
                val days = pattern["daysOfWeek"]?.jsonArray?.mapNotNull {
                    it.jsonPrimitive.content.let(::rruleDayFromGraph)
                } ?: emptyList()
                RecurrenceRule(
                    frequency = net.kigawa.kalender.model.Frequency.WEEKLY,
                    interval = interval,
                    byDay = days,
                )
            }

            "absoluteMonthly" -> RecurrenceRule(
                frequency = net.kigawa.kalender.model.Frequency.MONTHLY,
                interval = interval,
                byMonthDay = listOf(pattern["dayOfMonth"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1),
            )

            "absoluteYearly" -> RecurrenceRule(
                frequency = net.kigawa.kalender.model.Frequency.YEARLY,
                interval = interval,
                byMonthDay = listOf(pattern["dayOfMonth"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1),
                byMonth = listOf(pattern["month"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1),
            )

            "relativeMonthly", "relativeYearly" -> {
                // relative系は weekday 指定に近似して読み取る
                val index = pattern["index"]?.jsonPrimitive?.content
                val weekDays = pattern["daysOfWeek"]?.jsonArray?.mapNotNull {
                    it.jsonPrimitive.content.let(::rruleDayFromGraph)
                } ?: emptyList()
                if (type == "relativeYearly") {
                    RecurrenceRule(
                        frequency = net.kigawa.kalender.model.Frequency.YEARLY,
                        interval = interval,
                        byMonth = listOf(pattern["month"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1),
                        byDay = weekDays,
                    )
                } else {
                    RecurrenceRule(
                        frequency = net.kigawa.kalender.model.Frequency.MONTHLY,
                        interval = interval,
                        byDay = weekDays,
                    ).let { rule ->
                        // index(first等)は RecurrenceRule に保持できないため警告のみ
                        if (index != null && index != "first") {
                            platformLogWarning("OutlookRecurrenceConverter", "relativeMonthly index=$index は保持できません")
                        }
                        rule
                    }
                }
            }

            else -> null
        } ?: return null

        val withEnd = when (range?.get("type")?.jsonPrimitive?.content) {
            "numbered" -> rule.copy(count = range["numberOfOccurrences"]?.jsonPrimitive?.content?.toIntOrNull())
            "endDate" -> rule.copy(
                until = range["endDate"]?.jsonPrimitive?.content?.let { parseIsoDateMsOrNull(it) },
            )

            else -> rule
        }
        return withEnd.toRRule()
    }

    /** 曜日int(1=月..7=日)をGraphの文字列表現に */
    private fun graphDayName(day: Int): String = when (day) {
        1 -> "monday"
        2 -> "tuesday"
        3 -> "wednesday"
        4 -> "thursday"
        5 -> "friday"
        6 -> "saturday"
        7 -> "sunday"
        else -> "monday"
    }

    /** UTC基準の "yyyy-MM-dd"（RRULE の UNTIL を Graph の endDate にする際に使用） */
    private fun formatUtcDate(ms: Long): String {
        val ldt = kotlinx.datetime.Instant.fromEpochMilliseconds(ms)
            .toLocalDateTime(kotlinx.datetime.TimeZone.UTC)
        return "${ldt.year}-${ldt.monthNumber.toString().padStart(2, '0')}-${ldt.dayOfMonth.toString().padStart(2, '0')}"
    }

    private fun rruleDayFromGraph(name: String): Int = when (name.lowercase()) {
        "monday" -> 1
        "tuesday" -> 2
        "wednesday" -> 3
        "thursday" -> 4
        "friday" -> 5
        "saturday" -> 6
        "sunday" -> 7
        else -> 1
    }

    private fun parseIsoDateMsOrNull(text: String): Long? {
        // "yyyy-MM-dd" または "yyyy-MM-ddTHH:mm:ss" を受け取りUTCのmsへ
        return try {
            val normalized = if (text.contains('T')) text else "${text}T00:00:00Z"
            net.kigawa.kalender.util.parseIsoInstantMs(normalized)
        } catch (e: Exception) {
            null
        }
    }
}
