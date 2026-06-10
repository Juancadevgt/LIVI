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
            // fallback por si la DB tiene una accion eliminada (ej: CLEAR_DATA viejo)
            ActionType.CLEAR_CACHE
        }
}

/**
 * Migración v1 → v2: convertir tareas con acción "CLEAR_DATA" (eliminada) en "CLEAR_CACHE".
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE tasks SET action = 'CLEAR_CACHE' WHERE action = 'CLEAR_DATA'")
    }
}

@Database(entities = [TaskEntity::class], version = 2, exportSchema = false)
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
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration() // último recurso si falla la migración
                .build()
                .also { instance = it }
        }
    }
}
