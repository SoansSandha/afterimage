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
import io.github.soanssandha.afterimage.core.outline.OutlinePipeline
import io.github.soanssandha.afterimage.core.outline.OutlineStyle
import io.github.soanssandha.afterimage.render.OutlineRenderer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Turns a picked photo into what the wallpaper draws (docs/plan.md §5.3): the photo cropped to the screen, and its
 * outline computed at [WORKING_LONG_SIDE] and packed into a distance field. Blocking and CPU-heavy: run it off the
 * main thread.
 */
class OutlineMaker(private val context: Context, private val store: WallpaperStore = WallpaperStore(context)) {

    /** How much of the screen the new outline lights (0..1), and how long making it took. */
    class Result(val litFraction: Float, val millis: Long)

    fun make(uri: Uri): Result {
        val started = SystemClock.elapsedRealtime()
        val screen = portraitScreenSize()
        val photo = centerCrop(decodeCovering(uri, screen), screen)
        val working = scaleToLongSide(photo, WORKING_LONG_SIDE)
        val pixels = IntArray(working.width * working.height)
        working.getPixels(pixels, 0, working.width, 0, 0, working.width, working.height)
        val field = OutlinePipeline.run(GrayImage.fromArgb(working.width, working.height, pixels))
        val decoded = SystemClock.elapsedRealtime()
        store.save(photo, FieldCodec.pack(field))

        // The renderer's style is in screen pixels; the field is in working pixels.
        val toWorking = working.width.toFloat() / photo.width
        val screenStyle = OutlineRenderer.STYLE
        val workingStyle = OutlineStyle(
            halfWidth = screenStyle.halfWidth * toWorking,
            glowRadius = screenStyle.glowRadius * toWorking,
            glowStrength = screenStyle.glowStrength,
        )
        val result = Result(field.litFraction(workingStyle), SystemClock.elapsedRealtime() - started)
        Log.i(
            TAG,
            "Outline made: photo ${photo.width}x${photo.height}, field ${working.width}x${working.height}, " +
                "${field.lines.count()} line px, lit ${"%.1f".format(result.litFraction * 100)} %, " +
                "outline ${decoded - started} ms, total ${result.millis} ms",
        )
        return result
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
