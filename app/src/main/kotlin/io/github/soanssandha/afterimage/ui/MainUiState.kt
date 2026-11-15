package io.github.soanssandha.afterimage.ui

import io.github.soanssandha.afterimage.render.OutlineRenderer

/** What the settings screen shows. */
data class MainUiState(
    /** The app holds AMBIENT_WALLPAPER: the Magisk module installed it on the system image. */
    val systemApp: Boolean = false,
    /** Draws the stored photo and outline for the preview; null until a photo has been chosen. */
    val preview: OutlineRenderer? = null,
    /** Share of the screen the outline lights, known right after making it. */
    val litPercent: Int? = null,
    val busy: Boolean = false,
    val error: String? = null,
)
