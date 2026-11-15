package io.github.soanssandha.afterimage.core.config

import io.github.soanssandha.afterimage.core.outline.OutlineDetail

/**
 * The settings stored with one wallpaper, beside its photo and outline (docs/plan.md §5.7). A draft carries its own,
 * so Set as wallpaper applies them together with the photo. [LookCodec] reads and writes them.
 */
data class WallpaperLook(
    /** The [OutlineDetail] level the outline was made at. */
    val detail: Float = OutlineDetail.DEFAULT,
    val lineColor: LineColor = LineColor.PHOTO,
) {
    init {
        require(detail in OutlineDetail.MIN..OutlineDetail.MAX) { "detail must be within 0..1, was $detail" }
    }
}
