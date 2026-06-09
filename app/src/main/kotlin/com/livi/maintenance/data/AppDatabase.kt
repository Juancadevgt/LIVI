package com.livi.maintenance.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.livi.maintenance.actions.ActionType

class ActionTypeConverter {
    @TypeConverter
    fun fromAction(a: ActionType): String = a.name

    @TypeConverter
    fun toAction(s: String): ActionType = ActionType.valueOf(s)
}

@Database(entities = [TaskEntity::class], version = 1, exportSchema = false)
@TypeConverters(ActionTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "livi.db"
            ).build().also { instance = it }
        }
    }
}
