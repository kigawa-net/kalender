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
)
