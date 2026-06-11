package com.livi.maintenance.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Preferencia de tema (claro/oscuro) persistida en SharedPreferences.
 * Compose observa `isDarkMode` y recompone toda la UI al cambiarlo.
 */
object ThemePreference {

    private const val PREFS = "livi_theme_prefs"
    private const val KEY_DARK = "dark_mode"

    var isDarkMode by mutableStateOf(false)
        private set

    fun load(context: Context) {
        isDarkMode = context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK, false)
    }

    fun setDarkMode(context: Context, dark: Boolean) {
        isDarkMode = dark
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK, dark)
            .apply()
    }
}
