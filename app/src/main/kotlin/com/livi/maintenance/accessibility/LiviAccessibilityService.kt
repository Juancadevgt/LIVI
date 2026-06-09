package com.livi.maintenance.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.atomic.AtomicReference

/**
 * Servicio de accesibilidad que automatiza UI de Ajustes y Quick Settings:
 *  - CLEAR_CACHE / CLEAR_DATA: navega a la info de la app y pulsa el botón
 *  - AIRPLANE_TOGGLE_ON / AIRPLANE_TOGGLE_OFF: abre Quick Settings y toca
 *    el botón "Modo avión" (única forma confiable de apagar radios reales
 *    en Android moderno sin Device Owner)
 */
class LiviAccessibilityService : AccessibilityService() {

    enum class Mode { IDLE, CLEAR_CACHE, CLEAR_DATA, AIRPLANE_TOGGLE_ON, AIRPLANE_TOGGLE_OFF }

    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var lastTapAt: Long = 0L

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
        handler.postDelayed({ tryHandle(m) }, 350)
    }

    private fun tryHandle(m: Mode) {
        val root = rootInActiveWindow ?: return
        when (m) {
            Mode.CLEAR_CACHE -> handleClearCache(root)
            Mode.CLEAR_DATA -> handleClearData(root)
            Mode.AIRPLANE_TOGGLE_ON, Mode.AIRPLANE_TOGGLE_OFF -> handleAirplaneToggle(root)
            Mode.IDLE -> {}
        }
    }

    private fun handleClearCache(root: AccessibilityNodeInfo) {
        val storageEntry = findClickableByAnyText(root, listOf(
            "Almacenamiento y caché", "Almacenamiento",
            "Storage & cache", "Storage"
        ))
        if (storageEntry != null) { performClickOrAncestor(storageEntry); return }

        val clearCache = findClickableByAnyText(root, listOf(
            "Borrar caché", "Borrar memoria caché", "Limpiar caché", "Clear cache"
        ))
        if (clearCache != null) {
            performClickOrAncestor(clearCache)
            mode.set(Mode.IDLE)
            finishWithBack()
        }
    }

    private fun handleClearData(root: AccessibilityNodeInfo) {
        val storageEntry = findClickableByAnyText(root, listOf(
            "Almacenamiento y caché", "Almacenamiento", "Storage & cache", "Storage"
        ))
        if (storageEntry != null) { performClickOrAncestor(storageEntry); return }

        val clearData = findClickableByAnyText(root, listOf(
            "Borrar datos", "Borrar almacenamiento", "Borrar todos los datos",
            "Administrar espacio", "Clear storage", "Clear data"
        ))
        if (clearData != null) { performClickOrAncestor(clearData); return }

        val confirm = findClickableByAnyText(root, listOf(
            "Aceptar", "OK", "Borrar", "Eliminar", "Confirmar"
        ))
        if (confirm != null) {
            performClickOrAncestor(confirm)
            mode.set(Mode.IDLE)
            finishWithBack()
        }
    }

    /**
     * Busca el tile "Modo avión" en Quick Settings y hace tap.
     * Anti-doble-tap: si ya tocamos hace menos de 2.5s, ignoramos eventos.
     */
    private fun handleAirplaneToggle(root: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - lastTapAt < 2500) return  // ya tocamos hace poco

        val tile = findNodeByAnyText(root, AIRPLANE_LABELS)
        if (tile == null) {
            Log.w(TAG, "Tile no encontrado en esta ventana — esperando próximo evento")
            return
        }
        Log.i(TAG, "Tile candidate: text='${tile.text}' desc='${tile.contentDescription}' " +
            "class=${tile.className} clickable=${tile.isClickable}")
        performClickOrAncestor(tile)
        lastTapAt = now
        Log.i(TAG, "Tap ejecutado sobre tile de modo avión")
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
            for (node in list) {
                return node
            }
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
        // Reset del anti-doble-tap para permitir el siguiente tap aunque haya pasado poco tiempo
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

        private val AIRPLANE_LABELS = listOf(
            "Modo avión",
            "Modo de avión",
            "Modo vuelo",
            "Avión",
            "Airplane mode",
            "Flight mode",
            "Aeroplane mode"
        )

        fun isConnected(): Boolean = instance.get() != null
        fun service(): LiviAccessibilityService? = instance.get()
        fun setMode(m: Mode) { mode.set(m) }
        fun reset() { mode.set(Mode.IDLE) }
    }
}
