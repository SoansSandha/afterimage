package io.github.soanssandha.afterimage.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context

/** Whether Afterimage is the wallpaper right now. */
object ActiveWallpaper {

    /**
     * True if the lock screen shows Afterimage: the only place the outline matters, since AOD follows the lock screen.
     * A lock screen without a wallpaper of its own (a negative id) shows the home screen's. Android reports a live
     * wallpaper's details to apps that can see its package, and an app can always see its own, so no `<queries>` entry
     * is needed.
     */
    fun isOnLockScreen(context: Context): Boolean {
        val manager = WallpaperManager.getInstance(context)
        val lockHasItsOwn = manager.getWallpaperId(WallpaperManager.FLAG_LOCK) >= 0
        val which = if (lockHasItsOwn) WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM
        return manager.getWallpaperInfo(which)?.component == ComponentName(context, OutlineWallpaperService::class.java)
    }
}
