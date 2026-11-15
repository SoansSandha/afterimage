package io.github.soanssandha.afterimage.core.config

import io.github.soanssandha.afterimage.core.outline.OutlineDetail

/**
 * [WallpaperLook] as text: one `key=value` per line. Stored text is untrusted, so each missing, malformed or unknown
 * value decodes to its default on its own, and unknown keys are ignored (a newer build may have written them).
 */
object LookCodec {
    const val DETAIL = "detail"
    const val LINE_COLOR = "line_color"

    private val defaults = WallpaperLook()

    fun encode(look: WallpaperLook): String = "$DETAIL=${look.detail}\n$LINE_COLOR=${look.lineColor.key}\n"

    fun decode(text: String): WallpaperLook {
        val values = text.lineSequence()
            .mapNotNull { line ->
                val split = line.indexOf('=')
                if (split <= 0) null else line.substring(0, split).trim() to line.substring(split + 1).trim()
            }
            .toMap()
        return WallpaperLook(
            detail = values[DETAIL]?.toFloatOrNull()?.let(OutlineDetail::coerce) ?: defaults.detail,
            lineColor = LineColor.entries.firstOrNull { it.key == values[LINE_COLOR] } ?: defaults.lineColor,
        )
    }
}
