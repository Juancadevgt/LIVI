package com.livi.maintenance.telemetry

import android.content.Context
import android.os.Build
import android.util.Log
import com.livi.maintenance.BuildConfig
import com.livi.maintenance.actions.ActionType
import com.livi.maintenance.data.AppRepository
import com.livi.maintenance.data.TaskEntity
import com.livi.maintenance.identity.ManualIdentity
import com.livi.maintenance.identity.UserIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Envía eventos de telemetría a Power Automate, que los persiste en SharePoint.
 *
 * Diseño:
 *  - Fire-and-forget: si falla la red, sólo logea y no rompe la ejecución de la tarea.
 *  - Corre en CoroutineScope propio (IO) — no bloquea el worker.
 *  - Si TELEMETRY_URL viene vacía en BuildConfig (no se configuró en
 *    keystore.properties al compilar), simplemente no hace nada.
 */
object Telemetry {

    private const val TAG = "LiviTelemetry"
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 8_000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val isoFormatter: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    /**
     * Construye el evento y lo envía sin bloquear al caller. Se invoca después
     * de cada ejecución de tarea desde [com.livi.maintenance.scheduler.LiviWorker].
     */
    fun report(
        context: Context,
        task: TaskEntity,
        resultado: String,
        mensaje: String,
        proximaEjecucionMs: Long?,
        origen: String
    ) {
        if (BuildConfig.TELEMETRY_URL.isBlank()) {
            Log.d(TAG, "TELEMETRY_URL vacía — telemetría deshabilitada")
            return
        }

        val identity = UserIdentity.load(context)
        val correo = when {
            identity.email.isNotBlank() -> identity.email
            ManualIdentity.email.isNotBlank() -> ManualIdentity.email
            else -> ""
        }

        val appLabel = task.targetPackage?.let { pkg ->
            AppRepository(context).getAppLabel(pkg).ifBlank { pkg }
        } ?: ""

        val event = JSONObject().apply {
            put("correo", correo)
            put("marca", Build.MANUFACTURER ?: "")
            put("modelo", Build.MODEL ?: "")
            put("versionAndroid", Build.VERSION.RELEASE ?: "")
            put("versionLivi", BuildConfig.VERSION_NAME)
            put("deviceId", identity.serial.ifBlank { Build.ID ?: "" })
            put("accion", actionLabel(task.action))
            put("appObjetivo", appLabel)
            put("horaProgramada", "%02d:%02d".format(task.hour, task.minute))
            put("dias", daysLabel(task.daysOfWeek))
            put("frecuencia", task.repeatWeeks)
            put("resultado", resultado)
            put("mensaje", mensaje)
            put("fechaEjecucion", isoFormatter.format(Date()))
            put("tareaId", task.id)
            put("proximaEjecucion", proximaEjecucionMs?.let { isoFormatter.format(Date(it)) } ?: "")
            put("origen", origen)
        }

        scope.launch {
            postJson(BuildConfig.TELEMETRY_URL, event.toString())
        }
    }

    private fun postJson(url: String, body: String) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code in 200..299) {
                Log.i(TAG, "Telemetría enviada OK ($code)")
            } else {
                val errorBody = runCatching {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }
                }.getOrNull().orEmpty()
                Log.w(TAG, "Telemetría falló — código $code: $errorBody")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Telemetría falló (sin red?): ${t.message}")
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun actionLabel(action: ActionType): String = when (action) {
        ActionType.CLEAR_CACHE -> "Borrar caché"
        ActionType.AIRPLANE_TOGGLE -> "Modo avión 10s"
        ActionType.REBOOT -> "Reiniciar celular"
    }

    private fun daysLabel(mask: Int): String {
        if (mask == 0 || mask == TaskEntity.EVERY_DAY) return "Todos los días"
        if (mask == TaskEntity.WEEKDAYS) return "L-V"
        val parts = mutableListOf<String>()
        val days = listOf(
            TaskEntity.DAY_MON to "Lunes",
            TaskEntity.DAY_TUE to "Martes",
            TaskEntity.DAY_WED to "Miércoles",
            TaskEntity.DAY_THU to "Jueves",
            TaskEntity.DAY_FRI to "Viernes",
            TaskEntity.DAY_SAT to "Sábado",
            TaskEntity.DAY_SUN to "Domingo"
        )
        days.forEach { (bit, label) -> if ((mask and bit) != 0) parts.add(label) }
        return parts.joinToString(", ")
    }

    /** Constantes para el campo `origen` de la telemetría. */
    object Origin {
        const val AUTOMATICA = "AUTOMATICA"
        const val MANUAL = "MANUAL"
    }
}
