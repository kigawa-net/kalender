package net.kigawa.kalender.data

import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.coEvery
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.kigawa.kalender.data.db.CalendarDao
import net.kigawa.kalender.data.db.CalendarEntity
import net.kigawa.kalender.data.db.EventDao
import org.junit.Test

class CalendarLocalSourceTest {

    private val calendarId = 1L

    private fun makeSource(calendarDao: CalendarDao): CalendarLocalSource {
        val eventDao = mockk<EventDao>()
        return CalendarLocalSource(calendarDao, eventDao)
    }

    @Test
    fun `when_updateCalendarVisibility_called_then_delegates_to_dao`() = runTest {
        val calendarDao = mockk<CalendarDao>()
        every { calendarDao.observeAll() } returns flowOf(emptyList())
        coEvery { calendarDao.updateVisibility(any(), any()) } just Runs

        val localSource = makeSource(calendarDao)
        localSource.updateCalendarVisibility(calendarId, false)

        coVerify { calendarDao.updateVisibility(calendarId, false) }
    }

    @Test
    fun `when_observeCalendars_emits_then_isVisible_is_preserved`() = runTest {
        val calendarDao = mockk<CalendarDao>()
        val entity = CalendarEntity(id = calendarId, name = "Test", color = 0xFF0000FF.toInt(), accountName = "test@example.com", isVisible = false)
        every { calendarDao.observeAll() } returns flowOf(listOf(entity))

        val localSource = makeSource(calendarDao)
        val calendars = mutableListOf<net.kigawa.kalender.model.UserCalendar>()
        localSource.observeCalendars().collect { calendars.addAll(it) }

        assert(calendars.size == 1)
        assert(!calendars[0].isVisible)
    }
}
