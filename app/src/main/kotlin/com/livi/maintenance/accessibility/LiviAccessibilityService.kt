package com.livi.maintenance.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.atomic.AtomicReference

/**
 * Servicio de accesibilidad que automatiza UI de Ajustes para
 * pulsar "Borrar caché" y "Borrar datos" (incluyendo el diálogo de confirmación).
 *
 * Funciona escuchando cambios de ventana y reaccionando según el "modo" actual
 * que ActionExecutor le pide ejecutar.
 */
class LiviAccessibilityService : AccessibilityService() {

    enum class Mode { IDLE, CLEAR_CACHE, CLEAR_DATA }

    private val handler = Handler(Looper.getMainLooper())

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
        // Pequeño delay para que la UI termine de renderizar
        handler.postDelayed({ tryHandle(m) }, 350)
    }

    private fun tryHandle(m: Mode) {
        val root = rootInActiveWindow ?: return
        when (m) {
            Mode.CLEAR_CACHE -> handleClearCache(root)
            Mode.CLEAR_DATA -> handleClearData(root)
            Mode.IDLE -> {}
        }
    }

    private fun handleClearCache(root: AccessibilityNodeInfo) {
        // 1) En la pantalla "Información de la aplicación" muchos OEM esconden
        //    el botón de caché dentro de "Almacenamiento" o "Almacenamiento y caché".
        val storageEntry = findClickableByAnyText(
            root,
            listOf(
                "Almacenamiento y caché",
                "Almacenamiento",
                "Storage & cache",
                "Storage"
            )
        )
        if (storageEntry != null) {
            performClickOrAncestor(storageEntry)
            return
        }

        // 2) Una vez dentro de "Almacenamiento", pulsamos "Borrar caché"
        val clearCache = findClickableByAnyText(
            root,
            listOf("Borrar caché", "Borrar memoria caché", "Limpiar caché", "Clear cache")
        )
        if (clearCache != null) {
            performClickOrAncestor(clearCache)
            mode.set(Mode.IDLE)
            finishWithBack()
        }
    }

    private fun handleClearData(root: AccessibilityNodeInfo) {
        // 1) Entrar a Almacenamiento (si aún no estamos ahí)
        val storageEntry = findClickableByAnyText(
            root,
            listOf("Almacenamiento y caché", "Almacenamiento", "Storage & cache", "Storage")
        )
        if (storageEntry != null) {
            performClickOrAncestor(storageEntry)
            return
        }
        // 2) Pulsar "Borrar datos" / "Borrar almacenamiento"
        val clearData = findClickableByAnyText(
            root,
            listOf(
                "Borrar datos",
                "Borrar almacenamiento",
                "Borrar todos los datos",
                "Administrar espacio",
                "Clear storage",
                "Clear data"
            )
        )
        if (clearData != null) {
            performClickOrAncestor(clearData)
            return
        }
        // 3) Confirmar diálogo
        val confirm = findClickableByAnyText(
            root,
            listOf("Aceptar", "OK", "Borrar", "Eliminar", "Confirmar")
        )
        if (confirm != null) {
            performClickOrAncestor(confirm)
            mode.set(Mode.IDLE)
            finishWithBack()
        }
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
    ): AccessibilityNodeInfo? {
        for (text in candidates) {
            val list = root.findAccessibilityNodeInfosByText(text) ?: continue
            for (node in list) {
                if (node.text != null && node.text.toString().equals(text, ignoreCase = true)) {
                    return node
                }
                // Algunos OEM duplican el label en hijos; devolvemos el primero útil
                return node
            }
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

    companion object {
        private const val TAG = "LiviA11y"
        private val instance = AtomicReference<LiviAccessibilityService?>(null)
        private val mode = AtomicReference(Mode.IDLE)

        fun isConnected(): Boolean = instance.get() != null

        fun setMode(m: Mode) { mode.set(m) }

        fun reset() { mode.set(Mode.IDLE) }
    }
}
