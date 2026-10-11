package net.kigawa.kalender.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "event_templates")
data class EventTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val durationMinutes: Int = 60,
    val allDay: Boolean = false,
    val recurrenceRule: String? = null,
    /** 繰り返し設定(RecurrenceRule)のJSON文字列 */
    val recurrenceJson: String? = null,
    val preferredCalendarId: Long = 0L,
    val color: Int? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
