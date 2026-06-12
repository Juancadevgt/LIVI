package com.livi.maintenance.identity

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Identificador manual del usuario cuando Intune App Configuration Policy
 * todavía no envía datos. El usuario escribe su correo desde la tarjeta de
 * identidad la primera vez y queda persistido en SharedPreferences.
 *
 * Si después IT configura la policy de Intune, esa fuente toma prioridad
 * sobre este valor manual (decisión hecha en la UI / [UserIdentity]).
 */
object ManualIdentity {

    private const val PREFS = "livi_manual_identity"
    private const val KEY_EMAIL = "manual_email"

    /** Correo escrito manualmente por el usuario. Vacío si nunca se configuró. */
    var email by mutableStateOf("")
        private set

    fun load(context: Context) {
        email = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_EMAIL, "")
            .orEmpty()
    }

    fun save(context: Context, value: String) {
        email = value.trim()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EMAIL, email)
            .apply()
    }
}
