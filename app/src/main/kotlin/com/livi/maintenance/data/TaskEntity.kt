package com.livi.maintenance.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.livi.maintenance.actions.ActionType

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: ActionType,
    val targetPackage: String?,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: Int,
    val enabled: Boolean = true,
    val lastRunAt: Long? = null,
    val lastResult: String? = null
) {
    companion object {
        const val DAY_MON = 1 shl 0
        const val DAY_TUE = 1 shl 1
        const val DAY_WED = 1 shl 2
        const val DAY_THU = 1 shl 3
        const val DAY_FRI = 1 shl 4
        const val DAY_SAT = 1 shl 5
        const val DAY_SUN = 1 shl 6
        const val EVERY_DAY = 0b1111111
        const val WEEKDAYS = DAY_MON or DAY_TUE or DAY_WED or DAY_THU or DAY_FRI
    }
}
