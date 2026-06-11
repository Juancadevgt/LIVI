package com.livi.maintenance.identity

import android.content.Context
import android.content.RestrictionsManager
import android.os.Build
import com.livi.maintenance.BuildConfig

/**
 * Identifica al usuario corporativo del celular leyendo la configuración que
 * Intune envía via App Configuration Policies.
 *
 * Si la app NO está enrolada en Intune (sideload / debug), todos los campos
 * remotos vienen vacíos y se usa el modelo + serial del celular como fallback.
 */
object UserIdentity {

    fun load(context: Context): Identity {
        val rm = context.getSystemService(RestrictionsManager::class.java)
        val r = rm?.applicationRestrictions

        val email = r?.getString("user_email").orEmpty().trim()
        val upn = r?.getString("user_upn").orEmpty().trim()
        val displayName = r?.getString("user_display_name").orEmpty().trim()
        val supportEmail = r?.getString("support_email")
            ?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_SUPPORT_EMAIL

        val serial = readSerial()
        val model = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

        return Identity(
            email = email,
            upn = upn,
            displayName = displayName,
            supportEmail = supportEmail,
            serial = serial,
            model = model
        )
    }

    /**
     * Build.SERIAL solo funciona en Android < 8. En 8+ requiere permiso especial
     * que LIVI no pide. Si no se puede leer, retornamos vacío.
     */
    private fun readSerial(): String = try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            Build.SERIAL.takeIf { it != Build.UNKNOWN } ?: ""
        } else ""
    } catch (_: Throwable) {
        ""
    }

    data class Identity(
        val email: String,
        val upn: String,
        val displayName: String,
        val supportEmail: String,
        val serial: String,
        val model: String
    ) {
        /** True si Intune envió al menos un identificador del usuario. */
        val isIdentified: Boolean
            get() = email.isNotBlank() || upn.isNotBlank() || displayName.isNotBlank()

        /** Texto principal para mostrar en la card de identidad. */
        val displayLabel: String
            get() = when {
                displayName.isNotBlank() -> displayName
                email.isNotBlank() -> email
                upn.isNotBlank() -> upn
                else -> "(Sin identificar)"
            }

        /** Texto secundario (correo si el principal es el nombre). */
        val secondaryLabel: String
            get() = when {
                displayName.isNotBlank() && email.isNotBlank() -> email
                displayName.isNotBlank() && upn.isNotBlank() -> upn
                else -> model.ifBlank { "Dispositivo sin identificar" }
            }
    }
}
