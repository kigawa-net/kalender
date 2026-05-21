package net.kigawa.kalender.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendars")
data class CalendarEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val color: Int,
    val accountName: String,
)