package io.github.soanssandha.afterimage.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.soanssandha.afterimage.core.outline.FieldCodec
import io.github.soanssandha.afterimage.core.outline.PackedField
import java.io.File
import java.io.OutputStream

/**
 * The chosen photo, cropped to the screen, and its packed outline, in the app's private storage (excluded from
 * backup). The settings screen writes them and the wallpaper engine reads them; [WallpaperChanges] tells it when.
 * Blocking file I/O: keep it off the main thread.
 */
class WallpaperStore(context: Context) {

    private val dir = File(context.filesDir, "wallpaper")
    private val photoFile = File(dir, "photo.jpg")
    private val fieldFile = File(dir, "outline.aiof")

    fun loadPhoto(): Bitmap? = if (photoFile.isFile) BitmapFactory.decodeFile(photoFile.path) else null

    /** The stored outline, or null if there's none or it was written by another format version. */
    fun loadField(): PackedField? = if (fieldFile.isFile) FieldCodec.decode(fieldFile.readBytes()) else null

    /** Replaces the photo and outline, each written aside and renamed into place, then notifies the engines. */
    fun save(photo: Bitmap, field: PackedField) {
        dir.mkdirs()
        writeAtomically(photoFile) { out ->
            check(photo.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) { "Couldn't encode the photo" }
        }
        writeAtomically(fieldFile) { out -> out.write(FieldCodec.encode(field)) }
        WallpaperChanges.notifyChanged()
    }

    private fun writeAtomically(target: File, write: (OutputStream) -> Unit) {
        val temp = File(dir, "${target.name}.tmp")
        temp.outputStream().use(write)
        check(temp.renameTo(target)) { "Couldn't replace ${target.name}" }
    }

    private companion object {
        // The photo is shown full-screen on the lock screen, so keep it close to the source.
        const val JPEG_QUALITY = 95
    }
}
