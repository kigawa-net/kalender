package net.kigawa.kalender.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EventTemplateDao {
    @Query("SELECT * FROM event_templates ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<EventTemplateEntity>>

    @Query("SELECT * FROM event_templates ORDER BY updatedAt DESC")
    suspend fun getAll(): List<EventTemplateEntity>

    @Query("SELECT * FROM event_templates WHERE id = :id")
    fun observeById(id: Long): Flow<EventTemplateEntity?>

    @Query("SELECT * FROM event_templates WHERE id = :id")
    suspend fun findById(id: Long): EventTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(template: EventTemplateEntity): Long

    @Update
    suspend fun update(template: EventTemplateEntity)

    @Delete
    suspend fun delete(template: EventTemplateEntity)

    @Query("DELETE FROM event_templates WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM event_templates")
    suspend fun deleteAll()
}
