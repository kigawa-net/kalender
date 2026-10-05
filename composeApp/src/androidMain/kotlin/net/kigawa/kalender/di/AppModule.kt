package net.kigawa.kalender.di

import android.app.Application
import android.content.SharedPreferences
import org.koin.core.module.module
import org.koin.androidx.compose.composeModule
import org.koin.android.ext.koin.androidContext
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
import net.kigawa.kalender.viewmodel.AuthViewModel
import net.kigawa.kalender.viewmodel.EventDetailViewModel
import net.kigawa.kalender.viewmodel.EventEditViewModel
import net.kigawa.kalender.viewmodel.ProfileViewModel
import net.kigawa.kalender.viewmodel.WeeklyCalendarViewModel

val androidModule = module {
    // Auth
    single<GoogleAuthManager> { GoogleAuthManagerImpl() }
    single<MsalAuthManager> { MsalAuthManagerImpl.create(androidContext()) }

    // Data
    single<CalendarLocalSource> { 
        CalendarLocalSourceImpl(
            KalenderDatabase.getInstance(androidContext()).calendarDao(), 
            KalenderDatabase.getInstance(androidContext()).eventDao()
        ) 
    }
    single<CacheMetaSource> { 
        CacheMetaSourceImpl(KalenderDatabase.getInstance(androidContext()).cacheMetaDao()) 
    }
    
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

fun startKoin(app: Application) {
    org.koin.core.context.startKoin {
        androidContext(app)
        modules(androidModule)
    }
}