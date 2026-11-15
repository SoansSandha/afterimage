package io.github.soanssandha.afterimage.wallpaper

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The wallpaper service ships disabled, because system_server refuses to bind a wallpaper that declares
 * `supportsAmbientMode` unless its app holds `AMBIENT_WALLPAPER` (`signature|preinstalled`). Only the copy the Magisk
 * module puts on the system image gets that permission, so the service is enabled exactly when it's granted, and
 * disabled again if the module is removed (docs/plan.md §5.2).
 */
object AmbientComponent {

    const val PERMISSION = "android.permission.AMBIENT_WALLPAPER"

    /** Whether this copy of the app may draw on the always-on display. */
    fun isPermitted(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Enables or disables the wallpaper service to match [isPermitted], and returns it. */
    fun sync(context: Context): Boolean {
        val permitted = isPermitted(context)
        val component = ComponentName(context, OutlineWallpaperService::class.java)
        val wanted = if (permitted) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val packages = context.packageManager
        if (packages.getComponentEnabledSetting(component) != wanted) {
            packages.setComponentEnabledSetting(component, wanted, PackageManager.DONT_KILL_APP)
        }
        return permitted
    }
}
