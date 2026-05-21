package net.kigawa.kalender.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CacheMetaDao {
    @Query("SELECT * FROM cache_meta WHERE weekStartMs = :weekStartMs")
    suspend fun getByWeekStart(weekStartMs: Long): CacheMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: CacheMetaEntity)
}
