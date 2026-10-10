@file:OptIn(kotlin.time.ExperimentalTime::class)

package net.kigawa.kalender.model

import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * 繰り返しルールを表現するモデル
 * Google Calendar (RRULE RFC 5545) と Microsoft Graph (recurrence pattern) の両方に対応
 */
@Serializable
data class RecurrenceRule(
    /** 繰り返し頻度 */
    val frequency: Frequency,
    /** 間隔 (毎週なら1、隔週なら2など) */
    val interval: Int = 1,
    /** 週的場合: 曜日 (1=月〜7=日、複数指定可) */
    val byDay: List<Int> = emptyList(),
    /** 月的場合: 日 (1-31) */
    val byMonthDay: List<Int> = emptyList(),
    /** 年的場合: 月 (1-12) */
    val byMonth: List<Int> = emptyList(),
    /** 終了条件: 回数指定 */
    val count: Int? = null,
    /** 終了条件: 終了日 (ミリ秒) */
    val until: Long? = null,
    /** 終了条件: 無期限 */
    val neverEnds: Boolean = true,
) {
    /**
     * 周波数を変更し、新しい周波数に適用されないフィールドをクリアする。
     *
     * `copy(frequency = ...)` だけだと、週次の byDay や月次の byMonthDay が
     * 日次にも残り `FREQ=DAILY;BYDAY=MO` のような不正なRRULEになる。
     */
    fun withFrequency(frequency: Frequency): RecurrenceRule = when (frequency) {
        Frequency.NONE -> copy(frequency = Frequency.NONE, byDay = emptyList(), byMonthDay = emptyList(), byMonth = emptyList())
        Frequency.DAILY -> copy(frequency = Frequency.DAILY, byDay = emptyList(), byMonthDay = emptyList(), byMonth = emptyList())
        Frequency.WEEKLY -> copy(
            frequency = Frequency.WEEKLY,
            byDay = byDay.ifEmpty { listOf(1) },
            byMonthDay = emptyList(),
            byMonth = emptyList(),
        )
        Frequency.MONTHLY -> copy(
            frequency = Frequency.MONTHLY,
            byDay = emptyList(),
            byMonthDay = byMonthDay.ifEmpty { listOf(1) },
            byMonth = emptyList(),
        )
        Frequency.YEARLY -> copy(
            frequency = Frequency.YEARLY,
            byDay = emptyList(),
            byMonthDay = byMonthDay.ifEmpty { listOf(1) },
            byMonth = byMonth.ifEmpty { listOf(1) },
        )
    }

    /** 繰り返しなし */
    companion object {
        val NONE = RecurrenceRule(frequency = Frequency.NONE)

        /** 每日 */
        fun daily(interval: Int = 1) = RecurrenceRule(frequency = Frequency.DAILY, interval = interval)

        /** 每週 (曜日指定可能) */
        fun weekly(interval: Int = 1, byDay: List<Int> = listOf(1)) = RecurrenceRule(
            frequency = Frequency.WEEKLY,
            interval = interval,
            byDay = byDay,
        )

        /** 每月 (日期指定) */
        fun monthly(interval: Int = 1, byMonthDay: List<Int> = listOf(1)) = RecurrenceRule(
            frequency = Frequency.MONTHLY,
            interval = interval,
            byMonthDay = byMonthDay,
        )

        /** 每年 */
        fun yearly(interval: Int = 1, byMonth: List<Int> = listOf(1)) = RecurrenceRule(
            frequency = Frequency.YEARLY,
            interval = interval,
            byMonth = byMonth,
        )

        /** RRULE文字列からパース (簡易版) */
        fun fromRRule(rrule: String): RecurrenceRule {
            // 簡易パース実装
            val frequency = when {
                rrule.contains("FREQ=DAILY") -> Frequency.DAILY
                rrule.contains("FREQ=WEEKLY") -> Frequency.WEEKLY
                rrule.contains("FREQ=MONTHLY") -> Frequency.MONTHLY
                rrule.contains("FREQ=YEARLY") -> Frequency.YEARLY
                else -> Frequency.NONE
            }
            val interval = "INTERVAL=(\\d+)".toRegex().find(rrule)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val byDay = "BYDAY=([A-Za-z,]+)".toRegex().find(rrule)?.groupValues?.get(1)
                ?.split(",")
                ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                ?.map(::dayToInt)
                ?: emptyList()
            val byMonthDay = "BYMONTHDAY=(\\d+(?:,\\d+)*)".toRegex().find(rrule)?.groupValues?.get(1)
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?: emptyList()
            val byMonth = "BYMONTH=(\\d+(?:,\\d+)*)".toRegex().find(rrule)?.groupValues?.get(1)
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?: emptyList()
            val count = "COUNT=(\\d+)".toRegex().find(rrule)?.groupValues?.get(1)?.toIntOrNull()
            val until = "UNTIL=([^;]+)".toRegex().find(rrule)?.groupValues?.get(1)?.let { parseUntil(it) }
            val neverEnds = count == null && until == null

            return RecurrenceRule(
                frequency = frequency,
                interval = interval,
                byDay = byDay,
                byMonthDay = byMonthDay,
                byMonth = byMonth,
                count = count,
                until = until,
                neverEnds = neverEnds,
            )
        }

        private fun dayToInt(day: String): Int {
            val upperDay = day.uppercase()
            return if (upperDay == "MO") 1
            else if (upperDay == "TU") 2
            else if (upperDay == "WE") 3
            else if (upperDay == "TH") 4
            else if (upperDay == "FR") 5
            else if (upperDay == "SA") 6
            else if (upperDay == "SU") 7
            else 1
        }

        private fun parseUntil(str: String): Long? {
            return try {
                // UNTIL=20241231T235959Z 形式
                kotlinx.datetime.Instant.parse(str.replace("Z", "") + "Z").toEpochMilliseconds()
            } catch (e: Exception) {
                null
            }
        }
    }

    /** RRULE文字列に変換 (Google Calendar API用) */
    fun toRRule(): String {
        val parts = mutableListOf<String>()
        parts.add("FREQ=${frequency.name}")
        if (interval > 1) parts.add("INTERVAL=$interval")
        if (byDay.isNotEmpty()) parts.add("BYDAY=${byDay.map { intToDay(it) }.toList().joinToString(",")}")
        if (byMonthDay.isNotEmpty()) parts.add("BYMONTHDAY=${byMonthDay.joinToString(",")}")
        if (byMonth.isNotEmpty()) parts.add("BYMONTH=${byMonth.joinToString(",")}")
        if (count != null) parts.add("COUNT=$count")
        if (until != null) parts.add("UNTIL=${formatUntil(until!!)}")
        return parts.joinToString(";")
    }

    private fun intToDay(day: Int): String = when (day) {
        1 -> "MO"
        2 -> "TU"
        3 -> "WE"
        4 -> "TH"
        5 -> "FR"
        6 -> "SA"
        7 -> "SU"
        else -> "MO"
    }

    @OptIn(kotlin.time.ExperimentalTime::class)
    private fun formatUntil(ms: Long): String {
        val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(ms)
        return instant.toString().replace(":", "").replace("-", "").replace("Z", "")
    }
}

/** 繰り返し頻度 */
enum class Frequency {
    NONE,    // 繰り返しなし
    DAILY,   // 每日
    WEEKLY,  // 每週
    MONTHLY, // 每月
    YEARLY,  // 每年
}