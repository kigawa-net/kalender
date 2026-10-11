package net.kigawa.kalender.data

import kotlinx.coroutines.flow.Flow
import net.kigawa.kalender.model.EventTemplate

/**
 * イベントテンプレートのローカル保存インターフェース。
 * AndroidではRoom、WebではlocalStorageで実装する。
 */
interface LocalEventTemplateStore {
    fun observeAll(): Flow<List<EventTemplate>>
    fun observeById(id: Long): Flow<EventTemplate?>

    suspend fun getAll(): List<EventTemplate>
    suspend fun findById(id: Long): EventTemplate?
    suspend fun upsert(template: EventTemplate): Long
    suspend fun delete(id: Long)
    suspend fun deleteAll()
}
