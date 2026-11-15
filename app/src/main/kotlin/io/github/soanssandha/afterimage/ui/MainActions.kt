package io.github.soanssandha.afterimage.ui

import io.github.soanssandha.afterimage.core.config.LineColor

/** What [MainScreen]'s controls do; the activity wires them to the view model and the system pickers. */
class MainActions(
    val choosePhoto: () -> Unit,
    val setWallpaper: () -> Unit,
    /** The Detail slider is moving. */
    val changeDetail: (Float) -> Unit,
    /** The Detail slider was let go. */
    val chooseDetail: () -> Unit,
    val changeLineColor: (LineColor) -> Unit,
)
