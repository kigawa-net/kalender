package net.kigawa.kalender.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendars")
data class CalendarEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val color: Int,
    val accountName: String,
    @ColumnInfo(defaultValue = "1") val isVisible: Boolean = true,
    @ColumnInfo(defaultValue = "") val ownerEmail: String = "",
)