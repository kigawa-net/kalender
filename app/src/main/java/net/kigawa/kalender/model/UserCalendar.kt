package net.kigawa.kalender.model

data class UserCalendar(
    val id: Long,
    val name: String,
    val color: Int,
    val accountName: String,
    val isVisible: Boolean = true,
    val ownerEmail: String = "",
)