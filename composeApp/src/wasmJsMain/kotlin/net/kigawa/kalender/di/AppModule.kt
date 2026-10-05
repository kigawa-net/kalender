package net.kigawa.kalender.di

import org.koin.core.module.module
import org.koin.compose.composeModule
import net.kigawa.kalender.data.CacheMetaSource
import net.kigawa.kalender.data.CacheMetaSourceImpl
import net.kigawa.kalender.data.CalendarDataSource
import net.kigawa.kalender.data.CalendarLocalSource
import net.kigawa.kalender.data.CalendarLocalSourceImpl
import net.kigawa.kalender.data.CalendarRepository
import net.kigawa.kalender.data.GoogleCalendarDataSource
import net.kigawa.kalender.data.OutlookCalendarDataSource
import net.kigawa.kalender.data.auth.GoogleAuthManager
import net.kigawa.kalender.data.auth.GoogleAuthManagerImpl
import net.kigawa.kalender.data.auth.MsalAuthManager
import net.kigawa.kalender.data.auth.MsalAuthManagerImpl
import net.kigawa.kalender.data.db.CalendarDatabase
import net.kigawa.kalender.data.db.Schema
import net.kigawa.kalender.viewmodel.AuthViewModel
import net.kigawa.kalender.viewmodel.EventDetailViewModel
import net.kigawa.kalender.viewmodel.EventEditViewModel
import net.kigawa.kalender.viewmodel.ProfileViewModel
import net.kigawa.kalender.viewmodel.WeeklyCalendarViewModel

val webModule = module {
    // Database
    single { CalendarDatabase(Schema, org.jetbrains.kotlinx.coroutines.jsMainDispatcher) }

    // Auth
    single<GoogleAuthManager> { GoogleAuthManagerImpl() }
    single<MsalAuthManager> { MsalAuthManagerImpl.create(MsalAuthManagerImpl.MsalConfig(
        clientId = "YOUR_MSAL_CLIENT_ID", // TODO: Replace with actual client ID
        authority = "https://login.microsoftonline.com/common",
    )) }

    // Data
    single<CalendarLocalSource> { CalendarLocalSourceImpl(get()) }
    single<CacheMetaSource> { CacheMetaSourceImpl(get()) }
    
    // CalendarRepository with dynamic data sources
    single { (googleAuthManager: GoogleAuthManager, msAuthManager: MsalAuthManager, calendarLocalSource: CalendarLocalSource, cacheMetaSource: CacheMetaSource, scope: kotlinx.coroutines.CoroutineScope) ->
        CalendarRepository(
            dataSources = mutableListOf(),
            localSource = calendarLocalSource,
            cacheMetaSource = cacheMetaSource,
            scope = scope
        )
    }
    
    // ViewModels
    viewModel { WeeklyCalendarViewModel(
        googleAuthManager = get(),
        msAuthManager = get(),
        calendarLocalSource = get(),
        cacheMetaSource = get(),
        scope = get()
    )}
    viewModel { EventDetailViewModel(calendarLocalSource = get(), savedStateHandle = get()) }
    viewModel { EventEditViewModel(
        calendarLocalSource = get(),
        googleAuthManager = get(),
        msAuthManager = get(),
        savedStateHandle = get(),
        scope = get()
    )}
    viewModel { ProfileViewModel(
        calendarLocalSource = get(),
        googleAuthManager = get(),
        msAuthManager = get(),
        scope = get()
    )}
    viewModel { AuthViewModel(
        googleAuthManager = get(),
        msAuthManager = get(),
        savedStateHandle = get(),
        scope = get()
    )}
}

fun startKoin() {
    org.koin.core.context.startKoin {
        modules(webModule)
    }
}