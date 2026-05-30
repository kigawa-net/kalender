package net.kigawa.kalender.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {
    @Query("SELECT * FROM calendars")
    fun observeAll(): Flow<List<CalendarEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(calendars: List<CalendarEntity>)

    @Query("UPDATE calendars SET name = :name, color = :color, accountName = :accountName WHERE id = :id")
    suspend fun updateMeta(id: Long, name: String, color: Int, accountName: String)

    @Query("UPDATE calendars SET isVisible = :isVisible WHERE id = :id")
    suspend fun updateVisibility(id: Long, isVisible: Boolean)

    @Transaction
    suspend fun upsertAll(calendars: List<CalendarEntity>) {
        insertIgnoreAll(calendars)
        calendars.forEach { updateMeta(it.id, it.name, it.color, it.accountName) }
    }
}
