package com.livi.maintenance.data

import com.livi.maintenance.actions.ActionType

/**
 * Tareas predefinidas que se cargan automáticamente la primera vez que se abre
 * LIVI tras instalarla. IT puede modificarlas entrando al modo Admin, o resetear
 * todo a estos valores con el botón "Restablecer a tareas de fábrica".
 *
 * Versión `v1` — si en el futuro cambian las tareas oficiales, incrementar a `v2`
 * y la app re-insertará el set actualizado en el siguiente arranque.
 */
object DefaultTasks {

    const val SEED_VERSION = "v1"

    /** Power Apps — Microsoft Power Apps oficial. */
    private const val PKG_POWER_APPS = "com.microsoft.msapps"

    /** Microsoft Teams — paquete principal. */
    private const val PKG_TEAMS = "com.microsoft.teams"

    fun all(): List<TaskEntity> = listOf(
        // Lunes 06:00 — Borrar caché de Power Apps
        TaskEntity(
            action = ActionType.CLEAR_CACHE,
            targetPackage = PKG_POWER_APPS,
            hour = 6, minute = 0,
            daysOfWeek = TaskEntity.DAY_MON,
            enabled = true,
            repeatWeeks = 1
        ),
        // Lunes 06:15 — Borrar caché de Teams
        TaskEntity(
            action = ActionType.CLEAR_CACHE,
            targetPackage = PKG_TEAMS,
            hour = 6, minute = 15,
            daysOfWeek = TaskEntity.DAY_MON,
            enabled = true,
            repeatWeeks = 1
        ),
        // Lunes 06:30 — Modo avión 10 segundos
        TaskEntity(
            action = ActionType.AIRPLANE_TOGGLE,
            targetPackage = null,
            hour = 6, minute = 30,
            daysOfWeek = TaskEntity.DAY_MON,
            enabled = true,
            repeatWeeks = 1
        ),
        // Sábado 18:00 — Reiniciar el celular
        TaskEntity(
            action = ActionType.REBOOT,
            targetPackage = null,
            hour = 18, minute = 0,
            daysOfWeek = TaskEntity.DAY_SAT,
            enabled = true,
            repeatWeeks = 1
        )
    )
}
