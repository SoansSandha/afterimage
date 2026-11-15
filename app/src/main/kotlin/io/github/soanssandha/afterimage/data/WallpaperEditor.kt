package io.github.soanssandha.afterimage.data

import android.graphics.Bitmap
import android.net.Uri
import io.github.soanssandha.afterimage.core.config.LineColor
import io.github.soanssandha.afterimage.core.config.WallpaperLook
import io.github.soanssandha.afterimage.core.outline.PackedField

/**
 * The settings screen's edits. A picked photo, a new detail level or a new line colour goes into the store's draft,
 * which the preview shows and the wallpaper ignores until [apply]. The shown photo stays prepared in memory, so a new
 * detail level only redoes the fast half of the outline. Blocking and CPU-heavy, and not thread-safe: call it off the
 * main thread, one call at a time. After a failed call, [load] shows what the store really holds.
 */
class WallpaperEditor(private val store: WallpaperStore, private val maker: OutlineMaker) {

    /**
     * What the preview shows: the draft if there is one ([pending]), otherwise the wallpaper itself. [litFraction] is
     * known only right after an outline is made.
     */
    class Shown(
        val photo: Bitmap,
        val field: PackedField,
        val look: WallpaperLook,
        val pending: Boolean,
        val litFraction: Float? = null,
    )

    /** What's shown, and its photo prepared for outlining (made on first need). */
    private var shown: Shown? = null
    private var prepared: OutlineMaker.Prepared? = null

    /** The draft or, without a readable one, the wallpaper; null before any photo has been chosen. */
    fun load(): Shown? {
        shown = null
        prepared = null
        for (slot in listOf(store.draft, store.current)) {
            if (!slot.isComplete()) continue
            val photo = slot.loadPhoto() ?: continue
            val field = slot.loadField() ?: continue
            return Shown(photo, field, slot.loadLook(), pending = slot === store.draft).also { shown = it }
        }
        return null
    }

    /** Makes [uri]'s outline with the shown look (or the defaults) and stores it all as the draft. */
    fun choose(uri: Uri): Shown {
        val look = shown?.look ?: WallpaperLook()
        val next = maker.prepare(uri)
        val made = maker.outline(next, look.detail)
        val draft = store.draft
        draft.clear()
        draft.savePhoto(next.photo)
        draft.saveOutline(made.field)
        draft.saveLook(look)
        prepared = next
        return remember(Shown(next.photo, made.field, look, pending = true, litFraction = made.litFraction))
    }

    /** Remakes the shown photo's outline at [level], as the draft. Null if there's no photo yet. */
    fun changeDetail(level: Float): Shown? {
        val current = shown ?: return null
        val source = prepared ?: maker.prepare(current.photo).also { prepared = it }
        val made = maker.outline(source, level)
        val look = current.look.copy(detail = level)
        val draft = startDraft(withOutline = false)
        draft.saveOutline(made.field)
        draft.saveLook(look)
        return remember(Shown(current.photo, made.field, look, pending = true, litFraction = made.litFraction))
    }

    /** Changes the shown wallpaper's line colour, as the draft. The outline stays. Null if there's no photo yet. */
    fun changeLineColor(color: LineColor): Shown? {
        val current = shown ?: return null
        val look = current.look.copy(lineColor = color)
        startDraft(withOutline = true).saveLook(look)
        return remember(Shown(current.photo, current.field, look, pending = true))
    }

    /**
     * Makes the draft the wallpaper. Without a draft, the wallpaper's own photo is already in place; without either,
     * there's nothing to set and it throws.
     */
    fun apply() {
        if (store.draft.isComplete()) {
            store.applyDraft()
        } else {
            check(store.current.isComplete()) { "There's no photo to set" }
        }
    }

    /**
     * The draft to write into. When the wallpaper itself is shown, a draft is started from its files first, so the
     * wallpaper keeps them; its outline is copied too when the edit keeps the outline.
     */
    private fun startDraft(withOutline: Boolean): WallpaperStore.Slot {
        val draft = store.draft
        if (!draft.isComplete()) {
            draft.clear()
            draft.copyFrom(store.current, withOutline)
        }
        return draft
    }

    private fun remember(next: Shown): Shown = next.also { shown = it }
}
