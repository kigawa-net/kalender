package net.kigawa.kalender.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kigawa.kalender.model.UserCalendar
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarRepositoryTest {

    private val startMs = 1_000_000L
    private val endMs = startMs + 7 * 24 * 60 * 60 * 1000L

    private fun mockLocalStore() = mockk<LocalCalendarStore>().also {
        every { it.observeCalendars() } returns flowOf(emptyList())
    }

    @Test
    fun `when_eventsForWeek_called_and_cache_is_absent_then_fetches_from_data_source`() = runTest {
        val dataSource = mockk<CalendarDataSource>()
        val localStore = mockLocalStore()
        val calendar = UserCalendar(1L, "Test", 0xFF0000FF.toInt(), "test@example.com")

        coEvery { localStore.isWeekCacheFresh(startMs, any()) } returns false
        coEvery { dataSource.fetchCalendars() } returns listOf(calendar)
        coEvery { dataSource.fetchEvents(startMs, endMs) } returns emptyList()
        coEvery { localStore.upsertEventsForCalendars(any(), any(), any(), any()) } just Runs
        coEvery { localStore.markWeekFetched(any()) } just Runs
        every { localStore.observeEvents(startMs, endMs) } returns flowOf(emptyList())

        val repository = CalendarRepository(
            dataSources = listOf(dataSource),
            localStore = localStore,
            scope = this,
        )
        repository.eventsForWeek(startMs, endMs)
        advanceUntilIdle()

        coVerify { dataSource.fetchEvents(startMs, endMs) }
    }

    @Test
    fun `when_eventsForWeek_called_with_multiple_data_sources_and_no_cache_then_all_sources_are_fetched`() = runTest {
        val dataSource1 = mockk<CalendarDataSource>()
        val dataSource2 = mockk<CalendarDataSource>()
        val localStore = mockLocalStore()
        val calendar = UserCalendar(1L, "Test", 0xFF0000FF.toInt(), "test@example.com")

        coEvery { localStore.isWeekCacheFresh(startMs, any()) } returns false
        coEvery { dataSource1.fetchCalendars() } returns listOf(calendar)
        coEvery { dataSource1.fetchEvents(startMs, endMs) } returns emptyList()
        coEvery { dataSource2.fetchCalendars() } returns listOf(calendar)
        coEvery { dataSource2.fetchEvents(startMs, endMs) } returns emptyList()
        coEvery { localStore.upsertEventsForCalendars(any(), any(), any(), any()) } just Runs
        coEvery { localStore.markWeekFetched(any()) } just Runs
        every { localStore.observeEvents(startMs, endMs) } returns flowOf(emptyList())

        val repository = CalendarRepository(
            dataSources = listOf(dataSource1, dataSource2),
            localStore = localStore,
            scope = this,
        )
        repository.eventsForWeek(startMs, endMs)
        advanceUntilIdle()

        coVerify { dataSource1.fetchEvents(startMs, endMs) }
        coVerify { dataSource2.fetchEvents(startMs, endMs) }
    }

    @Test
    fun `when_eventsForWeek_called_and_cache_is_valid_then_does_not_fetch_from_data_source`() = runTest {
        val dataSource = mockk<CalendarDataSource>()
        val localStore = mockLocalStore()

        coEvery { localStore.isWeekCacheFresh(startMs, any()) } returns true
        every { localStore.observeEvents(startMs, endMs) } returns flowOf(emptyList())

        val repository = CalendarRepository(
            dataSources = listOf(dataSource),
            localStore = localStore,
            scope = this,
        )
        repository.eventsForWeek(startMs, endMs)
        advanceUntilIdle()

        coVerify(exactly = 0) { dataSource.fetchEvents(any(), any()) }
    }
}
