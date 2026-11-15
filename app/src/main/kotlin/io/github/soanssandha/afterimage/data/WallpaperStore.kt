package io.github.soanssandha.afterimage.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.soanssandha.afterimage.core.config.LookCodec
import io.github.soanssandha.afterimage.core.config.WallpaperLook
import io.github.soanssandha.afterimage.core.outline.FieldCodec
import io.github.soanssandha.afterimage.core.outline.PackedField
import java.io.File
import java.io.OutputStream

/**
 * The wallpaper's photo (cropped to the screen), its packed outline and its [WallpaperLook], in the app's private
 * storage (excluded from backup). Two slots: [current], which the wallpaper engine draws, and [draft], which
 * the settings screen edits and previews. Only [applyDraft] changes what the wallpaper shows, and it tells the engines
 * through [WallpaperChanges]. Blocking file I/O: keep it off the main thread.
 */
class WallpaperStore(context: Context) {

    private val dir = File(context.filesDir, "wallpaper")

    /** What the wallpaper draws; at the top of the folder, where the first builds kept it. */
    val current = Slot(dir)

    /** A photo or detail level the user hasn't applied yet. */
    val draft = Slot(File(dir, "draft"))

    fun loadPhoto(): Bitmap? = current.loadPhoto()

    /** The wallpaper's outline, or null if there's none or it was written by another format version. */
    fun loadField(): PackedField? = current.loadField()

    fun loadLook(): WallpaperLook = current.loadLook()

    /** Moves the draft over [current] and tells the engines to reload. */
    fun applyDraft() {
        check(draft.isComplete()) { "There's no draft to apply" }
        draft.moveTo(current)
        WallpaperChanges.notifyChanged()
    }

    /** One photo with its outline. Each file is written aside and renamed into place. */
    class Slot internal constructor(private val dir: File) {

        private val photoFile = File(dir, "photo.jpg")
        private val fieldFile = File(dir, "outline.aiof")
        private val lookFile = File(dir, "look")

        /** Has a photo and an outline. */
        fun isComplete(): Boolean = photoFile.isFile && fieldFile.isFile

        fun loadPhoto(): Bitmap? = if (photoFile.isFile) BitmapFactory.decodeFile(photoFile.path) else null

        fun loadField(): PackedField? = if (fieldFile.isFile) FieldCodec.decode(fieldFile.readBytes()) else null

        /** The stored look; the defaults for wallpapers saved before it existed. */
        fun loadLook(): WallpaperLook = if (lookFile.isFile) LookCodec.decode(lookFile.readText()) else WallpaperLook()

        fun savePhoto(photo: Bitmap) {
            writeAtomically(photoFile) { out ->
                check(photo.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) { "Couldn't encode the photo" }
            }
        }

        fun saveOutline(field: PackedField) {
            writeAtomically(fieldFile) { out -> out.write(FieldCodec.encode(field)) }
        }

        fun saveLook(look: WallpaperLook) {
            writeAtomically(lookFile) { out -> out.write(LookCodec.encode(look).toByteArray()) }
        }

        /** Copies [other]'s photo, and its outline if [withOutline], byte for byte. */
        fun copyFrom(other: Slot, withOutline: Boolean) {
            copyFile(other.photoFile, photoFile)
            if (withOutline) copyFile(other.fieldFile, fieldFile)
        }

        fun clear() {
            listOf(photoFile, fieldFile, lookFile).forEach { it.delete() }
        }

        internal fun moveTo(target: Slot) {
            target.dir.mkdirs()
            check(photoFile.renameTo(target.photoFile)) { "Couldn't move the photo" }
            check(fieldFile.renameTo(target.fieldFile)) { "Couldn't move the outline" }
            if (!lookFile.renameTo(target.lookFile)) target.lookFile.delete()
        }

        private fun copyFile(from: File, to: File) {
            writeAtomically(to) { out -> from.inputStream().use { it.copyTo(out) } }
        }

        private fun writeAtomically(target: File, write: (OutputStream) -> Unit) {
            dir.mkdirs()
            val temp = File(dir, "${target.name}.tmp")
            temp.outputStream().use(write)
            check(temp.renameTo(target)) { "Couldn't replace ${target.name}" }
        }
    }

    private companion object {
        // The photo is shown full-screen on the lock screen, so keep it close to the source.
        const val JPEG_QUALITY = 95
    }
}
