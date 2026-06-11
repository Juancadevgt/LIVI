package com.livi.maintenance.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.livi.maintenance.BuildConfig

/**
 * Estado global del modo Admin.
 *
 * - Por defecto la app está en modo Usuario (solo ve tareas, no las edita).
 * - Para entrar a Admin: tocar el ícono de candado en la barra superior y digitar
 *   la contraseña compilada en BuildConfig.ADMIN_PASSWORD.
 * - Al minimizar la app (onPause), se sale automáticamente del modo Admin para
 *   que no quede "abierto" si el celular pasa a otra persona.
 *
 * Bloqueo: tras 3 intentos fallidos seguidos, se bloquea por 30 segundos para
 * evitar que alguien intente adivinar la contraseña por fuerza bruta.
 */
object AdminMode {

    var isActive by mutableStateOf(false)
        private set

    private var failedAttempts by mutableIntStateOf(0)
    private var lockedUntil: Long = 0L

    /**
     * Intenta entrar a modo Admin.
     * @return Result.Success si la contraseña es correcta, o un Result.Failure con
     *         el motivo (contraseña incorrecta, bloqueado por intentos previos).
     */
    fun tryEnter(password: String): Result {
        val now = System.currentTimeMillis()
        if (now < lockedUntil) {
            val remainingSec = ((lockedUntil - now) / 1000).coerceAtLeast(1)
            return Result.Locked(remainingSec)
        }

        if (password == BuildConfig.ADMIN_PASSWORD) {
            isActive = true
            failedAttempts = 0
            return Result.Success
        }

        failedAttempts++
        if (failedAttempts >= MAX_ATTEMPTS) {
            lockedUntil = now + LOCK_DURATION_MS
            failedAttempts = 0
            return Result.Locked(LOCK_DURATION_MS / 1000)
        }
        val remaining = MAX_ATTEMPTS - failedAttempts
        return Result.WrongPassword(remaining)
    }

    fun exit() {
        isActive = false
    }

    sealed class Result {
        data object Success : Result()
        data class WrongPassword(val attemptsLeft: Int) : Result()
        data class Locked(val secondsRemaining: Long) : Result()
    }

    private const val MAX_ATTEMPTS = 3
    private const val LOCK_DURATION_MS = 30_000L
}
