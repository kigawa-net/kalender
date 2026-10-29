@file:OptIn(kotlin.time.ExperimentalTime::class)

package net.kigawa.kalender.model

import kotlinx.serialization.Serializable
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import net.kigawa.kalender.util.systemZone

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

    /**
     * 週次の既定曜日を [startMs] の曜日に設定する。
     * 既定が月曜固定だと、金曜に始めた予定が月曜に繰り返ってしまう。
     * 既に曜日が選択されている場合はそれを尊重する。
     */
    fun weeklyWithStartWeekday(startMs: Long): RecurrenceRule {
        // withFrequency は byDay が空なら月曜を補うため、その前の状態で判定する
        val alreadySelected = byDay.isNotEmpty()
        val base = withFrequency(Frequency.WEEKLY)
        if (alreadySelected || startMs <= 0L) return base
        return base.copy(byDay = listOf(weekdayNumberOf(startMs)))
    }

    /**
     * 月次の既定日を [startMs] の日付に設定する。
     * 既定が1日固定だと、15日に始めた予定が毎月1日に繰り返ってしまう。
     */
    fun monthlyWithStartDay(startMs: Long): RecurrenceRule {
        val alreadySelected = byMonthDay.isNotEmpty()
        val base = withFrequency(Frequency.MONTHLY)
        if (alreadySelected || startMs <= 0L) return base
        return base.copy(byMonthDay = listOf(dayNumberOf(startMs)))
    }

    /**
     * 年次の既定値(月日)を [startMs] に設定する。
     * 既定が1月1日固定だと、10月15日に始めた予定が毎年1月1日に繰り返ってしまう。
     */
    fun yearlyWithStartDate(startMs: Long): RecurrenceRule {
        val alreadySelected = byMonthDay.isNotEmpty() && byMonth.isNotEmpty()
        val base = withFrequency(Frequency.YEARLY)
        if (alreadySelected || startMs <= 0L) return base
        return base.copy(byMonthDay = listOf(dayNumberOf(startMs)), byMonth = listOf(monthNumberOf(startMs)))
    }

    /**
     * 繰り返しなし */
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
                // RRULE の UNTIL は基本形式 "yyyyMMddTHHmmssZ"（Zなしもありうる）。
                // kotlinx.datetime.Instant.parse は ISO-8601 を要求するため変換する。
                val normalized = basicToIsoInstant(str)
                kotlinx.datetime.Instant.parse(normalized).toEpochMilliseconds()
            } catch (e: Exception) {
                null
            }
        }

        /** "20261231T143000Z" → "2026-12-31T14:30:00Z" */
        private fun basicToIsoInstant(value: String): String {
            val v = value.trim().removeSuffix("Z").removeSuffix("z")
            // 日付部分は8桁、時刻部分はT以降6桁を期待する
            val date = v.take(8)
            val time = v.drop(8).removePrefix("T")
            if (date.length != 8) return value
            val y = date.substring(0, 4)
            val mo = date.substring(4, 6)
            val d = date.substring(6, 8)
            val hh = time.take(2).ifEmpty { "00" }
            val mm = time.drop(2).take(2).ifEmpty { "00" }
            val ss = time.drop(4).take(2).ifEmpty { "00" }
            return "$y-$mo-${d}T$hh:$mm:${ss}Z"
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
        // UNTIL は UTC の絶対時刻なので "yyyyMMddTHHmmssZ" 形式にする。
        // Z を落とすとローカル時刻として解釈され、有限ルールの解析に失敗して
        // neverEnds になったり、Google の時刻付き繰り返しで拒否される。
        // Instant.toString() は "...+00:00" または "...Z" で返るため、
        // オフセットを除去して Z を付ける。
        return instant.toString()
            .replace(":", "")
            .replace("-", "")
            .substringBefore('+')
            .let { if (it.endsWith("Z")) it else "${it}Z" }
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

/**
 * エポックミリ秒からモデルの曜日表記(1=月..7=日)を返す。
 * システムタイムゾーン基準で解釈する。
 */
internal fun weekdayNumberOf(epochMs: Long, zone: TimeZone = systemZone()): Int {
    val dayOfWeek: DayOfWeek = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone).dayOfWeek
    // isoDayNumber は 1=月 .. 7=日 でモデルの表記と一致する
    return dayOfWeek.isoDayNumber
}

/** エポックミリ秒から「日」(1-31)を返す */
internal fun dayNumberOf(epochMs: Long, zone: TimeZone = systemZone()): Int =
    Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone).dayOfMonth

/** エポックミリ秒から「月」(1-12)を返す */
internal fun monthNumberOf(epochMs: Long, zone: TimeZone = systemZone()): Int =
    Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone).monthNumber