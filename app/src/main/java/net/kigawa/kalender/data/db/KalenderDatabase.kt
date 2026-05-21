package net.kigawa.kalender.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CalendarEntity::class, EventEntity::class], version = 1)
abstract class KalenderDatabase : RoomDatabase() {
    abstract fun calendarDao(): CalendarDao
    abstract fun eventDao(): EventDao

    companion object {
        @Volatile
        private var instance: KalenderDatabase? = null

        fun getInstance(context: Context): KalenderDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    KalenderDatabase::class.java,
                    "kalender.db",
                ).build().also { instance = it }
            }
    }
}
