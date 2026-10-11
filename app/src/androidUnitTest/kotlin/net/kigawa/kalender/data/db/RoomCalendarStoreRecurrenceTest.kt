package net.kigawa.kalender.data.db

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.kigawa.kalender.model.CalendarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 繰り返し予定の情報がローカルキャッシュ(Room)で失われないことを検証する。
 * ここが欠けると、繰り返し予定を編集画面で開いた際にスコープ選択UIが表示されない。
 */
class RoomCalendarStoreRecurrenceTest {

    private val calendarId = 1L

    private fun storeWith(eventDao: EventDao): RoomCalendarStore {
        val calendarDao = mockk<CalendarDao>()
        every { calendarDao.observeAll() } returns flowOf(emptyList())
        every { calendarDao.observeVisible() } returns flowOf(emptyList())
        return RoomCalendarStore(calendarDao, eventDao, mockk<CacheMetaDao>())
    }

    /** 繰り返しインスタンス（例外）を表すモデル */
    private fun recurringEvent() = CalendarEvent(
        id = 42L,
        calendarId = calendarId,
        title = "毎週ミーティング",
        startMs = 1_800_000_000_000L,
        endMs = 1_800_003_600_000L,
        allDay = false,
        color = 0xFF4285F4.toInt(),
        timeZone = "Asia/Tokyo",
        description = "説明",
        location = "会議室A",
        remoteId = "master_20261009T100000Z",
        recurrenceRule = "FREQ=WEEKLY;BYDAY=FR",
        recurringEventId = "master",
        originalStartMs = 1_800_000_000_000L,
    )

    /** 同じ内容のEntity（DAOが返す値として使う） */
    private fun recurringEntity() = EventEntity(
        id = 42L,
        calendarId = calendarId,
        title = "毎週ミーティング",
        startMs = 1_800_000_000_000L,
        endMs = 1_800_003_600_000L,
        allDay = false,
        color = 0xFF4285F4.toInt(),
        timeZone = "Asia/Tokyo",
        description = "説明",
        location = "会議室A",
        remoteId = "master_20261009T100000Z",
        recurrenceRule = "FREQ=WEEKLY;BYDAY=FR",
        recurringEventId = "master",
        originalStartMs = 1_800_000_000_000L,
    )

    @Test
    fun when_observeEvents_emits_recurrence_fields_are_preserved() = runTest {
        val eventDao = mockk<EventDao>()
        every { eventDao.observeByRange(any(), any()) } returns flowOf(listOf(recurringEntity()))

        val store = storeWith(eventDao)
        val collected = mutableListOf<CalendarEvent>()
        store.observeEvents(0L, Long.MAX_VALUE).collect { collected.addAll(it) }

        assertEquals(1, collected.size)
        val event = collected[0]
        assertEquals("FREQ=WEEKLY;BYDAY=FR", event.recurrenceRule)
        assertEquals("master", event.recurringEventId)
        assertEquals(1_800_000_000_000L, event.originalStartMs)
    }

    @Test
    fun when_observeEventById_emits_recurrence_fields_are_preserved() = runTest {
        val eventDao = mockk<EventDao>()
        every { eventDao.observeById(42L) } returns flowOf(recurringEntity())

        val store = storeWith(eventDao)
        val collected = mutableListOf<CalendarEvent>()
        store.observeEventById(42L).collect { it?.let(collected::add) }

        assertEquals(1, collected.size)
        assertEquals("FREQ=WEEKLY;BYDAY=FR", collected[0].recurrenceRule)
        assertEquals("master", collected[0].recurringEventId)
        assertEquals(1_800_000_000_000L, collected[0].originalStartMs)
    }

    @Test
    fun when_upsertEvent_called_then_entity_carries_recurrence_fields() = runTest {
        val eventDao = mockk<EventDao>()
        coEvery { eventDao.upsertOne(any()) } just Runs

        val store = storeWith(eventDao)
        store.upsertEvent(recurringEvent())

        val captured = slot<EventEntity>()
        coVerify { eventDao.upsertOne(capture(captured)) }

        assertEquals("FREQ=WEEKLY;BYDAY=FR", captured.captured.recurrenceRule)
        assertEquals("master", captured.captured.recurringEventId)
        assertEquals(1_800_000_000_000L, captured.captured.originalStartMs)
    }

    @Test
    fun when_upsertEvent_called_with_nonRecurring_then_recurrence_fields_are_null() = runTest {
        val eventDao = mockk<EventDao>()
        coEvery { eventDao.upsertOne(any()) } just Runs

        val store = storeWith(eventDao)
        store.upsertEvent(
            CalendarEvent(
                id = 1L,
                calendarId = calendarId,
                title = "単発",
                startMs = 1_800_000_000_000L,
                endMs = 1_800_003_600_000L,
                allDay = false,
                color = 0xFF4285F4.toInt(),
                timeZone = "Asia/Tokyo",
                description = "",
                location = "",
                remoteId = "single",
            ),
        )

        val captured = slot<EventEntity>()
        coVerify { eventDao.upsertOne(capture(captured)) }

        assertNull(captured.captured.recurrenceRule)
        assertNull(captured.captured.recurringEventId)
        assertNull(captured.captured.originalStartMs)
    }

    @Test
    fun when_loadedFromCache_then_recurringEventId_detects_instance_forScopeSelector() = runTest {
        // スコープ選択UIの表示条件(isRecurringInstance)がローカル読み込み後も成立すること
        val eventDao = mockk<EventDao>()
        every { eventDao.observeById(42L) } returns flowOf(recurringEntity())

        val store = storeWith(eventDao)
        val collected = mutableListOf<CalendarEvent>()
        store.observeEventById(42L).collect { it?.let(collected::add) }

        val isRecurringInstance = !collected[0].recurringEventId.isNullOrEmpty()
        assertEquals(true, isRecurringInstance)
    }
}
