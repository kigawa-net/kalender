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
import net.kigawa.kalender.data.db.CacheMetaDao
import net.kigawa.kalender.data.db.CacheMetaEntity
import net.kigawa.kalender.model.UserCalendar
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarRepositoryTest {

    private val startMs = 1_000_000L
    private val endMs = startMs + 7 * 24 * 60 * 60 * 1000L

    private fun mockLocalSource() = mockk<CalendarLocalSource>().also {
        every { it.observeCalendars() } returns flowOf(emptyList())
    }

    @Test
    fun `when_eventsForWeek_called_and_cache_is_absent_then_fetches_from_data_source`() = runTest {
        val dataSource = mockk<CalendarDataSource>()
        val localSource = mockLocalSource()
        val cacheMetaDao = mockk<CacheMetaDao>()
        val calendar = UserCalendar(1L, "Test", 0xFF0000FF.toInt(), "test@example.com")

        coEvery { cacheMetaDao.getByWeekStart(startMs) } returns null
        coEvery { dataSource.fetchCalendars() } returns listOf(calendar)
        coEvery { dataSource.fetchEvents(startMs, endMs) } returns emptyList()
        coEvery { localSource.upsertEventsForCalendars(any(), any(), any(), any()) } just Runs
        coEvery { cacheMetaDao.upsert(any()) } just Runs
        every { localSource.observeEvents(startMs, endMs) } returns flowOf(emptyList())

        val repository = CalendarRepository(
            dataSources = listOf(dataSource),
            localSource = localSource,
            cacheMetaDao = cacheMetaDao,
            scope = this,
        )
        repository.eventsForWeek(startMs, endMs)
        advanceUntilIdle()

        coVerify { dataSource.fetchEvents(startMs, endMs) }
    }

    @Test
    fun `when_eventsForWeek_called_and_cache_is_valid_then_does_not_fetch_from_data_source`() = runTest {
        val dataSource = mockk<CalendarDataSource>()
        val localSource = mockLocalSource()
        val cacheMetaDao = mockk<CacheMetaDao>()

        val recentFetchMs = System.currentTimeMillis() - 5 * 60 * 1000L
        coEvery { cacheMetaDao.getByWeekStart(startMs) } returns CacheMetaEntity(startMs, recentFetchMs)
        every { localSource.observeEvents(startMs, endMs) } returns flowOf(emptyList())

        val repository = CalendarRepository(
            dataSources = listOf(dataSource),
            localSource = localSource,
            cacheMetaDao = cacheMetaDao,
            scope = this,
        )
        repository.eventsForWeek(startMs, endMs)
        advanceUntilIdle()

        coVerify(exactly = 0) { dataSource.fetchEvents(any(), any()) }
    }
}
