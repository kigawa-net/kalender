package net.kigawa.kalender.data

import net.kigawa.kalender.data.db.CacheMetaDao
import net.kigawa.kalender.data.db.CacheMetaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CacheMetaSourceImpl(private val cacheMetaDao: CacheMetaDao) : CacheMetaSource {
    override suspend fun getByWeekStart(weekStartMs: Long): CacheMeta? = withContext(Dispatchers.IO) {
        cacheMetaDao.getByWeekStart(weekStartMs)?.let { CacheMeta(it.weekStartMs, it.lastFetchedMs) }
    }

    override suspend fun upsert(meta: CacheMeta) = withContext(Dispatchers.IO) {
        cacheMetaDao.upsert(CacheMetaEntity(meta.weekStartMs, meta.lastFetchedMs))
    }
}