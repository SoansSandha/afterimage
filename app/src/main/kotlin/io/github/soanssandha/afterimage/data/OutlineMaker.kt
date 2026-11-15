package io.github.soanssandha.afterimage.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.WindowManager
import io.github.soanssandha.afterimage.core.outline.FieldCodec
import io.github.soanssandha.afterimage.core.outline.GrayImage
import io.github.soanssandha.afterimage.core.outline.OutlineSource
import io.github.soanssandha.afterimage.core.outline.OutlineStyle
import io.github.soanssandha.afterimage.core.outline.PackedField
import io.github.soanssandha.afterimage.render.OutlineRenderer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Turns a picked photo into what the wallpaper draws (docs/plan.md §5.3), in two steps: [prepare] crops it to the
 * screen and does the slow half of the outline once, at [WORKING_LONG_SIDE]; [outline] finishes it for one detail
 * level and packs it into a distance field. Blocking and CPU-heavy: run it off the main thread.
 */
class OutlineMaker(private val context: Context) {

    /** A photo cropped to the screen, ready to be outlined at any detail level. */
    class Prepared(val photo: Bitmap, val source: OutlineSource)

    /** An outline at one detail level, and how much of the screen it lights (0..1). */
    class Made(val field: PackedField, val litFraction: Float)

    /** Decodes the photo at [uri], crops it to the screen and prepares it. */
    fun prepare(uri: Uri): Prepared {
        val screen = portraitScreenSize()
        return prepare(centerCrop(decodeCovering(uri, screen), screen))
    }

    /** Prepares a photo that's already cropped to the screen, such as one loaded back from [WallpaperStore]. */
    fun prepare(photo: Bitmap): Prepared {
        val started = SystemClock.elapsedRealtime()
        val working = scaleToLongSide(photo, WORKING_LONG_SIDE)
        val pixels = IntArray(working.width * working.height)
        working.getPixels(pixels, 0, working.width, 0, 0, working.width, working.height)
        val source = OutlineSource.of(GrayImage.fromArgb(working.width, working.height, pixels))
        Log.i(
            TAG,
            "Photo prepared: ${photo.width}x${photo.height}, field ${working.width}x${working.height}, " +
                "${SystemClock.elapsedRealtime() - started} ms",
        )
        return Prepared(photo, source)
    }

    fun outline(prepared: Prepared, detail: Float): Made {
        val started = SystemClock.elapsedRealtime()
        val field = prepared.source.outline(detail)
        val packed = FieldCodec.pack(field)

        // The renderer's style is in screen pixels; the field is in working pixels.
        val toWorking = field.width.toFloat() / prepared.photo.width
        val screenStyle = OutlineRenderer.STYLE
        val workingStyle = OutlineStyle(
            halfWidth = screenStyle.halfWidth * toWorking,
            glowRadius = screenStyle.glowRadius * toWorking,
            glowStrength = screenStyle.glowStrength,
        )
        val lit = field.litFraction(workingStyle)
        Log.i(
            TAG,
            "Outline made: detail ${"%.2f".format(detail)}, ${field.lines.count()} line px, " +
                "lit ${"%.1f".format(lit * 100)} %, ${SystemClock.elapsedRealtime() - started} ms",
        )
        return Made(packed, lit)
    }

    /** The display's full size, portrait. The wallpaper surface is this size on a phone. */
    private fun portraitScreenSize(): Size {
        val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        return Size(min(bounds.width(), bounds.height()), max(bounds.width(), bounds.height()))
    }

    /** Decodes [uri] just large enough to cover [screen], as a software ARGB_8888 bitmap. */
    private fun decodeCovering(uri: Uri, screen: Size): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val scale = max(screen.width.toFloat() / info.size.width, screen.height.toFloat() / info.size.height)
            decoder.setTargetSize(
                (info.size.width * scale).roundToInt().coerceAtLeast(screen.width),
                (info.size.height * scale).roundToInt().coerceAtLeast(screen.height),
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return if (decoded.config == Bitmap.Config.ARGB_8888) decoded else decoded.copy(Bitmap.Config.ARGB_8888, false)
    }

    private fun centerCrop(bitmap: Bitmap, size: Size): Bitmap {
        if (bitmap.width == size.width && bitmap.height == size.height) return bitmap
        val x = (bitmap.width - size.width) / 2
        val y = (bitmap.height - size.height) / 2
        return Bitmap.createBitmap(bitmap, x, y, size.width, size.height)
    }

    private fun scaleToLongSide(bitmap: Bitmap, longSide: Int): Bitmap {
        val factor = longSide.toFloat() / max(bitmap.width, bitmap.height)
        if (factor >= 1f) return bitmap
        val width = (bitmap.width * factor).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * factor).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    companion object {
        /** The outline's working size; the Canny defaults are tuned for it (`EdgeDetector.Canny`). */
        const val WORKING_LONG_SIDE = 1536
        private const val TAG = "Afterimage"
    }
}
