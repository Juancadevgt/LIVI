package com.livi.maintenance.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Servicio de accesibilidad que automatiza UI de Ajustes y Quick Settings.
 *
 * Anti-cancelación: `taskSucceeded` se marca a true SOLO cuando el tap real
 * se ejecuta. Si el usuario presiona Home/Back antes de que LIVI complete,
 * el timeout llega sin que la flag se haya marcado y ActionExecutor devuelve
 * Result.Interrupted (que dispara re-notificación en LiviWorker).
 */
class LiviAccessibilityService : AccessibilityService() {

    enum class Mode { IDLE, CLEAR_CACHE, AIRPLANE_TOGGLE_ON, AIRPLANE_TOGGLE_OFF }

    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var lastTapAt: Long = 0L
    /** Momento (ms) en que vimos por primera vez el botón "Borrar caché" deshabilitado.
     *  Se usa para distinguir entre Android calculando tamaño (~1-2s) vs caché ya vacía (>4s).
     */
    @Volatile private var firstSeenDisabledAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance.set(this)
        Log.i(TAG, "AccessibilityService conectado")
    }

    override fun onDestroy() {
        instance.compareAndSet(this, null)
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val m = mode.get()
        if (m == Mode.IDLE) return
        handler.postDelayed({ tryHandle(m) }, 700)
    }

    private fun tryHandle(m: Mode) {
        val root = rootInActiveWindow ?: return
        when (m) {
            Mode.CLEAR_CACHE -> handleClearCache(root)
            Mode.AIRPLANE_TOGGLE_ON, Mode.AIRPLANE_TOGGLE_OFF -> handleAirplaneToggle(root)
            Mode.IDLE -> {}
        }
    }

    private fun handleClearCache(root: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - lastTapAt < 1200) return

        val clearCache = findClickableByAnyText(root, CACHE_LABELS)
        if (clearCache != null) {
            val ancestor = findClickableAncestor(clearCache)
            val effectiveEnabled = ancestor?.isEnabled == true || clearCache.isEnabled
            Log.i(TAG, "Borrar caché encontrado: text='${clearCache.text}' enabled=$effectiveEnabled")

            if (!effectiveEnabled) {
                // Distinguir entre "Android calculando" (transitorio) vs "caché vacía" (permanente).
                if (firstSeenDisabledAt == 0L) {
                    firstSeenDisabledAt = now
                    Log.d(TAG, "Botón deshabilitado por primera vez — esperando cálculo de Android")
                    return
                }
                val elapsed = now - firstSeenDisabledAt
                if (elapsed < CACHE_EMPTY_TIMEOUT_MS) {
                    Log.d(TAG, "Botón aún deshabilitado, esperando ($elapsed/$CACHE_EMPTY_TIMEOUT_MS ms)")
                    return
                }
                // Pasaron >4s con el botón sigue deshabilitado → la caché ya está vacía.
                Log.i(TAG, "Caché ya estaba vacía (botón deshabilitado tras ${elapsed}ms) — saliendo OK")
                taskSucceeded.set(true)  // contar como éxito: no había nada que borrar
                mode.set(Mode.IDLE)
                finishWithBack()
                return
            }

            // Botón habilitado → tap real
            performClickOrAncestor(clearCache)
            lastTapAt = now
            taskSucceeded.set(true)
            Log.i(TAG, "Tap ejecutado sobre Borrar caché — MARKED SUCCESS")
            mode.set(Mode.IDLE)
            finishWithBack()
            return
        }

        // Resetear el contador si salimos de la pantalla de almacenamiento
        firstSeenDisabledAt = 0L

        val storageEntry = findClickableByAnyText(root, STORAGE_LABELS)
        if (storageEntry != null) {
            Log.i(TAG, "Tap en Almacenamiento para entrar")
            performClickOrAncestor(storageEntry)
            lastTapAt = now
        } else {
            Log.d(TAG, "Ni Borrar caché ni Almacenamiento visibles aún")
        }
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = node
        var hops = 0
        while (n != null && hops < 5) {
            if (n.isClickable) return n
            n = n.parent
            hops++
        }
        return null
    }

    private fun handleAirplaneToggle(root: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - lastTapAt < 2500) return

        val tile = findNodeByAnyText(root, AIRPLANE_LABELS)
        if (tile == null) {
            Log.w(TAG, "Tile de modo avión no encontrado")
            return
        }
        Log.i(TAG, "Tile modo avión: text='${tile.text}' desc='${tile.contentDescription}'")
        performClickOrAncestor(tile)
        lastTapAt = now
        taskSucceeded.set(true)
        Log.i(TAG, "Tap ejecutado sobre tile modo avión — MARKED SUCCESS")
        mode.set(Mode.IDLE)
    }

    private fun finishWithBack() {
        handler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
            handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 400)
            handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 900)
        }, 600)
    }

    private fun findClickableByAnyText(
        root: AccessibilityNodeInfo,
        candidates: List<String>
    ): AccessibilityNodeInfo? = findNodeByAnyText(root, candidates)

    private fun findNodeByAnyText(
        root: AccessibilityNodeInfo,
        candidates: List<String>
    ): AccessibilityNodeInfo? {
        for (text in candidates) {
            val list = root.findAccessibilityNodeInfosByText(text) ?: continue
            for (node in list) return node
        }
        return findByContentDescription(root, candidates)
    }

    private fun findByContentDescription(
        node: AccessibilityNodeInfo?,
        candidates: List<String>
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString()
        if (desc != null) {
            for (c in candidates) {
                if (desc.contains(c, ignoreCase = true)) return node
            }
        }
        for (i in 0 until node.childCount) {
            val found = findByContentDescription(node.getChild(i), candidates)
            if (found != null) return found
        }
        return null
    }

    private fun performClickOrAncestor(node: AccessibilityNodeInfo) {
        var n: AccessibilityNodeInfo? = node
        var hops = 0
        while (n != null && hops < 5) {
            if (n.isClickable && n.isEnabled) {
                n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
            n = n.parent
            hops++
        }
    }

    fun openQuickSettings() {
        lastTapAt = 0L
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    }

    fun goHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    companion object {
        private const val TAG = "LiviA11y"
        private val instance = AtomicReference<LiviAccessibilityService?>(null)
        private val mode = AtomicReference(Mode.IDLE)

        /** Flag para detectar interrupción del usuario (Home/Back antes de terminar) */
        private val taskSucceeded = AtomicBoolean(false)

        private val AIRPLANE_LABELS = listOf(
            "Modo avión", "Modo de avión", "Modo vuelo", "Avión",
            "Airplane mode", "Flight mode", "Aeroplane mode"
        )
        private val STORAGE_LABELS = listOf(
            "Almacenamiento y caché", "Almacenamiento",
            "Storage & cache", "Storage"
        )
        private val CACHE_LABELS = listOf(
            "Borrar caché", "Borrar memoria caché", "Limpiar caché",
            "Vaciar caché", "Eliminar caché", "Clear cache"
        )

        fun isConnected(): Boolean = instance.get() != null
        fun service(): LiviAccessibilityService? = instance.get()
        fun setMode(m: Mode) { mode.set(m) }
        fun reset() { mode.set(Mode.IDLE) }

        /** Resetea el flag de éxito antes de iniciar una nueva tarea */
        fun resetSuccess() {
            taskSucceeded.set(false)
            // También reseteamos el contador de "botón deshabilitado" en la instancia activa
            instance.get()?.firstSeenDisabledAt = 0L
        }
        /** Devuelve true si el tap real se ejecutó durante la última tarea */
        fun wasSuccessful(): Boolean = taskSucceeded.get()

        /** Tiempo máximo en ms para esperar a que Android termine de calcular el tamaño
         *  de la caché antes de asumir que está vacía. */
        private const val CACHE_EMPTY_TIMEOUT_MS = 4_000L
    }
}
