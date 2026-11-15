package io.github.soanssandha.afterimage.data

import java.util.concurrent.CopyOnWriteArraySet

/**
 * Tells the wallpaper engines in this process that [WallpaperStore] has a new photo and outline. The settings screen
 * and the wallpaper service share the app's one process, so no broadcast is needed.
 */
object WallpaperChanges {
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun add(listener: () -> Unit) {
        listeners += listener
    }

    fun remove(listener: () -> Unit) {
        listeners -= listener
    }

    /** Calls every listener on the caller's thread; listeners hand the work to their own thread. */
    fun notifyChanged() {
        listeners.forEach { it() }
    }
}
