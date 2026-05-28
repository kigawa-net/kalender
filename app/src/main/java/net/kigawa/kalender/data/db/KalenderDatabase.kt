package net.kigawa.kalender.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CalendarEntity::class, EventEntity::class, CacheMetaEntity::class], version = 3)
abstract class KalenderDatabase : RoomDatabase() {
    abstract fun calendarDao(): CalendarDao
    abstract fun eventDao(): EventDao
    abstract fun cacheMetaDao(): CacheMetaDao

    companion object {
        @Volatile
        private var instance: KalenderDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS cache_meta " +
                        "(weekStartMs INTEGER NOT NULL, lastFetchedMs INTEGER NOT NULL, PRIMARY KEY (weekStartMs))"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): KalenderDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    KalenderDatabase::class.java,
                    "kalender.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
