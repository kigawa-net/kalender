package net.kigawa.kalender.ui.navigation

enum class AppDestinations(val route: String) {
    HOME(""),
    EVENT_DETAIL("event/{eventId}"),
    EVENT_EDIT("event/edit/{eventId}"),
    EVENT_NEW("event/new"),
    PROFILE("profile"),
    LOGIN("login"),
}