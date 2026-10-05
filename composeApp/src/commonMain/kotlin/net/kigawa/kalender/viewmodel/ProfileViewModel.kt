package net.kigawa.kalender.viewmodel

import androidx.lifecycle.ViewModel
import net.kigawa.kalender.model.UserCalendar

data class OutlookAccount(
    val email: String,
)

data class GoogleAccount(
    val email: String,
    val displayName: String?,
)

data class ProfileUiState(
    val accounts: List<OutlookAccount> = emptyList(),
    val googleAccount: GoogleAccount? = null,
    val isAddingAccount: Boolean = false,
    val isAddingGoogleAccount: Boolean = false,
    val addAccountError: String? = null,
    val addGoogleAccountError: String? = null,
    val calendarsByOwnerEmail: Map<String, List<UserCalendar>> = emptyMap(),
)

expect class ProfileViewModel : ViewModel {
    val uiState: kotlinx.coroutines.flow.StateFlow<ProfileUiState>
    fun addAccount()
    fun removeAccount(email: String)
    fun addGoogleAccount()
    fun removeGoogleAccount()
    fun dismissAddAccountError()
    fun updateCalendarVisibility(id: Long, isVisible: Boolean)
}