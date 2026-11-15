package io.github.soanssandha.afterimage.core.config

/** How the outline's lines are coloured. [key] is what's stored, so it never changes once released. */
enum class LineColor(val key: String) {
    /** Each line takes the photo's colour where it runs, at full brightness. */
    PHOTO("photo"),

    /** Every line is white. */
    WHITE("white"),
}
