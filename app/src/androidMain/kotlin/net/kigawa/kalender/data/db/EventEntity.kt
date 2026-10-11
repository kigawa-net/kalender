package net.kigawa.kalender.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = CalendarEntity::class,
            parentColumns = ["id"],
            childColumns = ["calendarId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("calendarId")],
)
data class EventEntity(
    @PrimaryKey val id: Long,
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
    // 繰り返し予定関連
    /** RRULE文字列（繰り返しなしの場合はnull） */
    val recurrenceRule: String? = null,
    /** 繰り返しシリーズの識別子（インスタンスの場合に設定） */
    val recurringEventId: String? = null,
    /** シリーズ元の開始日時（例外/変更済みインスタンスの基準） */
    val originalStartMs: Long? = null,
)
