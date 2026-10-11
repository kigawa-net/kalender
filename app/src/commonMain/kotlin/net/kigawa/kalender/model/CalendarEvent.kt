package net.kigawa.kalender.model

import kotlinx.serialization.Serializable

@Serializable
data class CalendarEvent(
    val id: Long,
    val calendarId: Long,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val allDay: Boolean,
    val color: Int,
    val timeZone: String,
    val description: String,
    val location: String,
    val remoteId: String = "",
    // 繰り返し関連
    val recurrenceRule: String? = null,      // RRULE 文字列
    val recurrenceExceptions: List<Long> = emptyList(),  // 例外発生日時(ミリ秒)
    val recurringEventId: String? = null,    // シリーズ全体を識別するID
    val originalStartMs: Long? = null,       // シリーズ元の開始日時
)