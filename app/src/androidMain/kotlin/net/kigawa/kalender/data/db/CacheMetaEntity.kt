package net.kigawa.kalender.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cache_meta")
data class CacheMetaEntity(
    @PrimaryKey val weekStartMs: Long,
    val lastFetchedMs: Long,
)
