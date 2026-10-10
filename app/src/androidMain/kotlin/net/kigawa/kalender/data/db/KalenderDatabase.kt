package net.kigawa.kalender.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CalendarEntity::class, EventEntity::class, CacheMetaEntity::class, EventTemplateEntity::class], version = 7)
abstract class KalenderDatabase : RoomDatabase() {
    abstract fun calendarDao(): CalendarDao
    abstract fun eventDao(): EventDao
    abstract fun cacheMetaDao(): CacheMetaDao
    abstract fun eventTemplateDao(): EventTemplateDao

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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE calendars ADD COLUMN isVisible INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE calendars ADD COLUMN ownerEmail TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v5→v6: 繰り返し予定の情報を events に永積化する。
         *
         * 注: この内容は #61(feature/61-recurring-events) で追加されたものと同一。
         * #61 を先にマージしている環境では既に適用済みなので、ここで同じ
         * ALTER TABLE を再度実行すると duplicate column エラーになる。
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addRecurrenceColumnsIfMissing(db)
            }
        }

        /**
         * v6→v7: テンプレートテーブルを追加する。
         * #61 で v5→v6 により繰り返しカラムを追加しているため、ここでは触らない。
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS event_templates (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "title TEXT NOT NULL DEFAULT '', " +
                        "description TEXT NOT NULL DEFAULT '', " +
                        "location TEXT NOT NULL DEFAULT '', " +
                        "durationMinutes INTEGER NOT NULL DEFAULT 60, " +
                        "allDay INTEGER NOT NULL DEFAULT 0, " +
                        "recurrenceRule TEXT, " +
                        "recurrenceJson TEXT, " +
                        "preferredCalendarId INTEGER NOT NULL DEFAULT 0, " +
                        "color INTEGER, " +
                        "createdAt INTEGER NOT NULL DEFAULT 0, " +
                        "updatedAt INTEGER NOT NULL DEFAULT 0)"
                )
            }
        }

        /** events に繰り返しカラムを追加する(既存なら何もしない) */
        private fun addRecurrenceColumnsIfMissing(db: SupportSQLiteDatabase) {
            val existing = mutableSetOf<String>()
            db.query("PRAGMA table_info(events)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0) existing += cursor.getString(nameIndex)
                }
            }
            if ("recurrenceRule" !in existing) {
                db.execSQL("ALTER TABLE events ADD COLUMN recurrenceRule TEXT")
            }
            if ("recurringEventId" !in existing) {
                db.execSQL("ALTER TABLE events ADD COLUMN recurringEventId TEXT")
            }
            if ("originalStartMs" !in existing) {
                db.execSQL("ALTER TABLE events ADD COLUMN originalStartMs INTEGER")
            }
        }

        fun getInstance(context: Context): KalenderDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    KalenderDatabase::class.java,
                    "kalender.db",
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                ).build().also { instance = it }
            }
    }
}
