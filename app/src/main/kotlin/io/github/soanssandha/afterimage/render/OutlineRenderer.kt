package io.github.soanssandha.afterimage.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import io.github.soanssandha.afterimage.core.burnin.Offset
import io.github.soanssandha.afterimage.core.config.LineColor
import io.github.soanssandha.afterimage.core.outline.OutlineStyle
import io.github.soanssandha.afterimage.core.outline.PackedField
import java.nio.ByteBuffer

/**
 * Draws one frame with a single AGSL shader: the photo fading out while its outline glows in (the Fade effect,
 * docs/plan.md §5.4). The outline is the packed distance field, sampled with bilinear filtering and scaled to screen
 * pixels on the GPU, so line width and glow are uniforms (§5.2). The wallpaper engine and the settings screen's preview
 * each own one; it isn't thread-safe.
 *
 * The shading formula matches `core.outline.OutlineShading`, which the tests and the outline lab use.
 */
class OutlineRenderer(
    photo: Bitmap,
    field: PackedField,
    lineColor: LineColor = LineColor.PHOTO,
    style: OutlineStyle = STYLE,
) {

    /** The photo's size: the frame size this renderer draws at, in its own pixels. */
    val width: Int = photo.width
    val height: Int = photo.height

    private val fieldBitmap = alphaBitmap(field)
    private val shader = RuntimeShader(SOURCE)
    private val paint = Paint()

    init {
        shader.setInputShader("photo", BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
        shader.setInputShader(
            "field",
            BitmapShader(fieldBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
            },
        )
        shader.setFloatUniform("fieldScale", field.width.toFloat() / width, field.height.toFloat() / height)
        shader.setFloatUniform("maxDistance", field.maxDistance)
        shader.setFloatUniform("screenPerField", width.toFloat() / field.width)
        shader.setFloatUniform("halfWidth", style.halfWidth)
        shader.setFloatUniform("glowRadius", style.glowRadius)
        shader.setFloatUniform("glowStrength", style.glowStrength)
        shader.setFloatUniform("lineBrightness", 1f)
        shader.setFloatUniform(
            "colorAmount",
            when (lineColor) {
                LineColor.PHOTO -> 1f
                LineColor.WHITE -> 0f
            },
        )
        paint.shader = shader
    }

    /**
     * Fills [canvas]'s clip at [progress] (0 photo, 1 outline), with the outline shifted by [offset] screen pixels for
     * burn-in. The canvas must already map this renderer's [width] × [height] onto the target.
     */
    fun draw(canvas: Canvas, progress: Float, offset: Offset) {
        shader.setFloatUniform("progress", progress.coerceIn(0f, 1f))
        shader.setFloatUniform("offset", offset.dx.toFloat(), offset.dy.toFloat())
        paint.shader = shader
        canvas.drawPaint(paint)
    }

    /** Frees the field texture. The photo belongs to the caller. */
    fun release() {
        fieldBitmap.recycle()
    }

    companion object {
        /** Line style in screen pixels; Phase 3 makes it a setting. */
        val STYLE = OutlineStyle(halfWidth = 1.0f, glowRadius = 4.0f, glowStrength = 0.35f)

        /** The field as an alpha-only bitmap: alpha byte = packed distance. */
        private fun alphaBitmap(field: PackedField): Bitmap {
            val bitmap = Bitmap.createBitmap(field.width, field.height, Bitmap.Config.ALPHA_8)
            val rowBytes = bitmap.rowBytes
            val buffer = if (rowBytes == field.width) {
                ByteBuffer.wrap(field.bytes)
            } else {
                // Rows can be padded for alignment; copy row by row.
                ByteBuffer.allocate(rowBytes * field.height).apply {
                    for (y in 0 until field.height) {
                        position(y * rowBytes)
                        put(field.bytes, y * field.width, field.width)
                    }
                    rewind()
                }
            }
            bitmap.copyPixelsFromBuffer(buffer)
            return bitmap
        }

        private const val SOURCE = """
            uniform shader photo;          // the photo, at frame size
            uniform shader field;          // distance field: alpha = distance / maxDistance, in field pixels
            uniform float2 fieldScale;     // field pixels per frame pixel
            uniform float maxDistance;     // field pixels at alpha 1
            uniform float screenPerField;  // frame pixels per field pixel
            uniform float progress;        // 0 photo, 1 outline
            uniform float2 offset;         // burn-in shift of the outline, frame pixels
            uniform float halfWidth;       // line core half-width, frame pixels
            uniform float glowRadius;      // glow fall-off, frame pixels
            uniform float glowStrength;    // glow brightness at the line
            uniform float lineBrightness;  // overall line brightness
            uniform float colorAmount;     // 1: lines in the photo's colours; 0: white lines

            half4 main(float2 p) {
                half3 photoColor = photo.eval(p).rgb;
                float2 source = p - offset;
                float d = float(field.eval(source * fieldScale).a) * maxDistance * screenPerField;
                float core = 1.0 - smoothstep(halfWidth - 0.5, halfWidth + 0.5, d);
                float glow = glowStrength * exp(-d / max(glowRadius, 0.0001)) * step(0.0001, glowRadius);
                float line = clamp(max(core, glow), 0.0, 1.0) * lineBrightness;
                // Lines take the photo's own colour there, scaled up to full brightness.
                half3 tint = photo.eval(source).rgb;
                tint = tint / max(max(max(tint.r, tint.g), tint.b), 0.004);
                tint = mix(half3(1.0), tint, half(colorAmount));
                half t = half(progress);
                return half4(photoColor * (1.0 - t) + tint * half(line) * t, 1.0);
            }
        """
    }
}
