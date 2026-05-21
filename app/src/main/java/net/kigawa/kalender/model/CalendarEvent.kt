package net.kigawa.kalender.model

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
)