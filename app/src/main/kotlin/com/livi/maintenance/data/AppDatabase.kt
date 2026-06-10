package com.livi.maintenance.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.livi.maintenance.actions.ActionType

class ActionTypeConverter {
    @TypeConverter
    fun fromAction(a: ActionType): String = a.name

    @TypeConverter
    fun toAction(s: String): ActionType =
        try { ActionType.valueOf(s) }
        catch (_: IllegalArgumentException) {
            ActionType.CLEAR_CACHE
        }
}

/**
 * v1 → v2: CLEAR_DATA → CLEAR_CACHE
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE tasks SET action = 'CLEAR_CACHE' WHERE action = 'CLEAR_DATA'")
    }
}

/**
 * v2 → v3: agregar columna pendingExecution para workaround sin Device Owner.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN pendingExecution INTEGER")
    }
}

/**
 * v3 → v4: agregar columna repeatWeeks para frecuencia de repetición.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN repeatWeeks INTEGER NOT NULL DEFAULT 1")
    }
}

@Database(entities = [TaskEntity::class], version = 4, exportSchema = false)
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
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
