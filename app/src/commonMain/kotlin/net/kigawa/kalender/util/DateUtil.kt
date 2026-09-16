@file:OptIn(kotlin.time.ExperimentalTime::class)

package net.kigawa.kalender.util

import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime

/**
 * java.time はJVM専用のためwasmJsではビルドできない。kotlinx-datetimeベースの
 * 共通ヘルパーをここに集約する。
 */

fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

fun systemZone(): TimeZone = TimeZone.currentSystemDefault()

fun todayLocalDate(zone: TimeZone = systemZone()): LocalDate = Clock.System.todayIn(zone)

fun nowLocalTime(zone: TimeZone = systemZone()): LocalTime =
    Clock.System.now().toLocalDateTime(zone).time

/** ISO の月曜始まり週の開始日を返す */
fun mondayOfWeek(date: LocalDate): LocalDate {
    val isoDow = date.dayOfWeek.isoDayNumber // 1=Mon..7=Sun
    return date.minus(isoDow - 1, DateTimeUnit.DAY)
}

fun LocalDate.plusDays(days: Int): LocalDate = this.plus(days, DateTimeUnit.DAY)
fun LocalDate.plusWeeks(weeks: Int): LocalDate = this.plus(weeks, DateTimeUnit.WEEK)

fun LocalDate.startOfDayMs(zone: TimeZone = systemZone()): Long =
    atStartOfDayIn(zone).toEpochMilliseconds()

fun Long.toLocalDate(zone: TimeZone = systemZone()): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(zone).date

fun Long.toLocalTime(zone: TimeZone = systemZone()): LocalTime =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(zone).time

fun Int.pad2(): String = toString().padStart(2, '0')

private val JP_WEEKDAY = arrayOf("月", "火", "水", "木", "金", "土", "日")

fun LocalDate.jpWeekdayShort(): String = JP_WEEKDAY[dayOfWeek.isoDayNumber - 1]

/** "yyyy年M月d日(曜)" 形式 */
fun LocalDate.formatJp(): String = "${year}年${monthNumber}月${dayOfMonth}日(${jpWeekdayShort()})"

/** "HH:mm" 形式 */
fun LocalTime.formatHm(): String = "${hour.pad2()}:${minute.pad2()}"

fun timeZoneOrNull(id: String): TimeZone? = if (id.isEmpty()) null else runCatching { TimeZone.of(id) }.getOrNull()

/** ISO8601 オフセット付き日時文字列 (Google Calendar API向け) */
fun formatIsoOffsetDateTime(ms: Long, zone: TimeZone): String {
    val instant = Instant.fromEpochMilliseconds(ms)
    val ldt = instant.toLocalDateTime(zone)
    val offset = zone.offsetAt(instant)
    return "${ldt.year}-${ldt.monthNumber.pad2()}-${ldt.dayOfMonth.pad2()}T" +
        "${ldt.hour.pad2()}:${ldt.minute.pad2()}:${ldt.second.pad2()}$offset"
}

/** "yyyy-MM-dd" 形式 (終日イベントの日付) */
fun formatIsoDate(ms: Long, zone: TimeZone = systemZone()): String = ms.toLocalDate(zone).toString()

/**
 * 終日イベント向けのローカル日付0時の日時文字列 ("yyyy-MM-ddT00:00:00.0000000")。
 * 終日イベントはタイムゾーンを持たない概念のため、実時刻をUTC変換せずローカル日付のみを使う
 * (Microsoft Graph APIはisAllDay=trueの場合、日時が厳密に00:00:00であることを要求する)。
 */
fun formatIsoDateAtMidnight(ms: Long, zone: TimeZone = systemZone()): String =
    "${formatIsoDate(ms, zone)}T00:00:00.0000000"

/** オフセットなしのローカル日時文字列 (Outlook Graph API向け) */
fun formatLocalDateTimeNoOffset(ms: Long, zone: TimeZone): String {
    val ldt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone)
    return "${ldt.year}-${ldt.monthNumber.pad2()}-${ldt.dayOfMonth.pad2()}T" +
        "${ldt.hour.pad2()}:${ldt.minute.pad2()}:${ldt.second.pad2()}"
}

/** ISO8601オフセット付き/UTC日時文字列(末尾Zを含む)をエポックmsへ */
fun parseIsoInstantMs(text: String): Long = Instant.parse(text).toEpochMilliseconds()

/** "yyyy-MM-dd" をその日の0時(指定タイムゾーン)のエポックmsへ */
fun parseIsoDateStartMs(text: String, zone: TimeZone = systemZone()): Long =
    LocalDate.parse(text).atStartOfDayIn(zone).toEpochMilliseconds()
