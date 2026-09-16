package net.kigawa.kalender.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE startMs < :endMs AND endMs > :startMs")
    fun observeByRange(startMs: Long, endMs: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id")
    fun observeById(id: Long): Flow<EventEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(events: List<EventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOne(event: EventEntity)

    @Query("DELETE FROM events WHERE startMs < :endMs AND endMs > :startMs")
    suspend fun deleteByRange(startMs: Long, endMs: Long)

    @Query("DELETE FROM events WHERE calendarId IN (:calendarIds) AND startMs < :endMs AND endMs > :startMs")
    suspend fun deleteByRangeAndCalendars(startMs: Long, endMs: Long, calendarIds: List<Long>)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Transaction
    suspend fun replaceByRange(startMs: Long, endMs: Long, events: List<EventEntity>) {
        deleteByRange(startMs, endMs)
        if (events.isNotEmpty()) upsertAll(events)
    }

    @Transaction
    suspend fun replaceByRangeAndCalendars(startMs: Long, endMs: Long, events: List<EventEntity>, calendarIds: List<Long>) {
        if (calendarIds.isNotEmpty()) deleteByRangeAndCalendars(startMs, endMs, calendarIds)
        if (events.isNotEmpty()) upsertAll(events)
    }
}
