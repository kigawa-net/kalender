package net.kigawa.kalender.data

import net.kigawa.kalender.data.db.CalendarDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CacheMetaSourceImpl(private val db: CalendarDatabase) : CacheMetaSource {
    private val cacheMetaQueries = db.cacheMetaQueries

    override suspend fun getByWeekStart(weekStartMs: Long): CacheMeta? = withContext(Dispatchers.IO) {
        cacheMetaQueries.selectByWeekStart(weekStartMs)?.let { CacheMeta(it.weekStartMs, it.lastFetchedMs) }
    }

    override suspend fun upsert(meta: CacheMeta) = withContext(Dispatchers.IO) {
        cacheMetaQueries.upsert(meta.weekStartMs, meta.lastFetchedMs)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        cacheMetaQueries.deleteAll()
    }
}