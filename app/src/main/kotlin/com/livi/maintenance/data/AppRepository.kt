package com.livi.maintenance.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repositorio para listar y consultar las apps instaladas en el dispositivo.
 * Usa PackageManager. Requiere el permiso QUERY_ALL_PACKAGES en Android 11+.
 */
class AppRepository(private val context: Context) {

    suspend fun listInstalledApps(includeSystem: Boolean = false): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .asSequence()
                .filter { it.packageName != context.packageName } // excluir a nosotros mismos
                .filter { app ->
                    if (includeSystem) true
                    else (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                         (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                }
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null } // solo apps con launcher
                .map { info ->
                    InstalledApp(
                        packageName = info.packageName,
                        label = pm.getApplicationLabel(info).toString(),
                        isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                }
                .sortedBy { it.label.lowercase() }
                .toList()
        }

    fun getAppLabel(packageName: String?): String {
        if (packageName.isNullOrBlank()) return ""
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    fun getAppIcon(packageName: String?): ImageBitmap? {
        if (packageName.isNullOrBlank()) return null
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawableToBitmap(drawable).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
