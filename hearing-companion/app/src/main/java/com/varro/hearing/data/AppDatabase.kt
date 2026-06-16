package com.varro.hearing.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun toAction(name: String): HaAction = HaAction.valueOf(name)
    @TypeConverter fun fromAction(a: HaAction): String = a.name
}

@Database(entities = [DiaryEntry::class, Schedule::class, Place::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun placeDao(): PlaceDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "hearing.db"
            ).build().also { instance = it }
        }
    }
}
