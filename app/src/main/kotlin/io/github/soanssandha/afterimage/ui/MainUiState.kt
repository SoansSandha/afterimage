package io.github.soanssandha.afterimage.ui

import io.github.soanssandha.afterimage.core.config.LineColor
import io.github.soanssandha.afterimage.core.outline.OutlineDetail
import io.github.soanssandha.afterimage.render.OutlineRenderer

/** What the settings screen shows. */
data class MainUiState(
    /** The app holds AMBIENT_WALLPAPER: the Magisk module installed it on the system image. */
    val systemApp: Boolean = false,
    /** Afterimage is what the lock screen (and so AOD) shows. */
    val active: Boolean = false,
    /** Draws the photo and outline the screen is showing; null until a photo has been chosen. */
    val preview: OutlineRenderer? = null,
    /** The preview is a draft: a photo or detail level the wallpaper doesn't show until it's set. */
    val pending: Boolean = false,
    /** The Detail slider's position; while dragging, it runs ahead of the outline in the preview. */
    val detail: Float = OutlineDetail.DEFAULT,
    /** The line colour switch's position. */
    val lineColor: LineColor = LineColor.PHOTO,
    /** Share of the screen the outline lights, known right after making it. */
    val litPercent: Int? = null,
    /** What's running; the buttons and the Detail slider wait for it. */
    val work: Work? = null,
    val error: String? = null,
) {
    enum class Work { LOADING, CHOOSING, DETAIL, LOOK, SETTING }

    val canSetWallpaper: Boolean
        get() = systemApp && preview != null && work == null && (pending || !active)
}
