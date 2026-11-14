package io.github.soanssandha.afterimage.lab

import io.github.soanssandha.afterimage.core.outline.ComponentFilter
import io.github.soanssandha.afterimage.core.outline.DistanceTransform
import io.github.soanssandha.afterimage.core.outline.EdgeDetector
import io.github.soanssandha.afterimage.core.outline.EdgeMask
import io.github.soanssandha.afterimage.core.outline.GrayImage
import io.github.soanssandha.afterimage.core.outline.OutlineShading
import io.github.soanssandha.afterimage.core.outline.OutlineStyle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.system.exitProcess

private const val USAGE = """
Outline lab: runs Afterimage's outline pipeline (:core) on a photo and writes previews, to tune the detector and the
line style on the PC before anything runs on the phone (docs/plan.md, section 7, Phase 1a).

Usage: ./gradlew :lab:run --args="<photo> [options]"
  --out <dir>             output folder (default: build/outline-lab, inside :lab, which git ignores)
  --long-side <px>        working size the outline is computed at, as on the phone (default 1536)
  --screen-long-side <px> size the previews are drawn at; the 9 Pro XL panel is 2992 (default)
  --detector <name>       canny, xdog or both (default both)
  --half-width <px>       line core half-width in screen px (default 1.0)
  --glow-radius <px>      glow fall-off in screen px (default 4.0)
  --glow-strength <0..1>  glow brightness at the line (default 0.35)
  --min-component <px>    drop line groups smaller than this, in working px (default 24)
  --canny-sigma, --canny-high, --canny-low, --canny-min    Canny settings (EdgeDetector.Canny)
  --xdog-sigma, --xdog-k, --xdog-tau, --xdog-epsilon        XDoG settings (EdgeDetector.Xdog)

Writes <name>-<detector>-lines.png (raw lines, working size), -aod.png (the outline as the AOD frame would draw it,
white) and -tinted.png (lines in the photo's own colours). Photos and previews are personal: never commit them.
"""

fun main(args: Array<String>) {
    val options = runCatching { LabOptions.parse(args) }.getOrElse { error ->
        System.err.println(error.message)
        System.err.println(USAGE)
        exitProcess(2)
    }
    val source = ImageIO.read(options.photo) ?: run {
        System.err.println("Can't read ${options.photo} as an image")
        exitProcess(1)
    }
    val working = scaleToLongSide(source, options.longSide)
    val screen = scaleToLongSide(source, options.screenLongSide)
    val gray = GrayImage.fromArgb(working.width, working.height, working.argb())
    val screenArgb = screen.argb()
    val scale = screen.width.toFloat() / working.width
    options.out.mkdirs()
    val base = options.photo.nameWithoutExtension
    println(
        "${options.photo.name}: ${source.width}x${source.height}, outline at ${working.width}x${working.height}, " +
            "previews at ${screen.width}x${screen.height}; ${options.style}",
    )

    for ((name, detector) in options.detectors) {
        val t0 = System.nanoTime()
        val raw = detector.detect(gray)
        val t1 = System.nanoTime()
        val lines = ComponentFilter.removeSmall(raw, options.minComponent)
        val t2 = System.nanoTime()
        val distances = DistanceTransform.of(lines)
        val t3 = System.nanoTime()

        val preview = Preview.render(distances, working.width, working.height, screen, scale, options.style)
        writePng(maskImage(lines), File(options.out, "$base-$name-lines.png"))
        writePng(preview.white(), File(options.out, "$base-$name-aod.png"))
        writePng(preview.tinted(screenArgb), File(options.out, "$base-$name-tinted.png"))

        fun ms(from: Long, to: Long) = "%.0f".format((to - from) / 1e6)
        println(
            "  $name: ${raw.count()} line px, ${lines.count()} after dropping specks; " +
                "lit ${"%.1f".format(preview.litFraction * 100)} %, mean ${"%.3f".format(preview.meanIntensity)}; " +
                "detect ${ms(t0, t1)} ms, specks ${ms(t1, t2)} ms, distances ${ms(t2, t3)} ms",
        )
    }
    println("Wrote previews to ${options.out.absolutePath}")
}

/** Parsed command line. */
private class LabOptions(
    val photo: File,
    val out: File,
    val longSide: Int,
    val screenLongSide: Int,
    val detectors: List<Pair<String, EdgeDetector>>,
    val style: OutlineStyle,
    val minComponent: Int,
) {
    companion object {
        fun parse(args: Array<String>): LabOptions {
            var photo: File? = null
            val values = mutableMapOf<String, String>()
            var i = 0
            while (i < args.size) {
                val arg = args[i]
                if (arg.startsWith("--")) {
                    require(i + 1 < args.size) { "$arg needs a value" }
                    values[arg.removePrefix("--")] = args[i + 1]
                    i += 2
                } else {
                    require(photo == null) { "Only one photo, please (got $photo and $arg)" }
                    photo = File(arg)
                    i++
                }
            }
            val file = requireNotNull(photo) { "Which photo? Pass its path." }
            require(file.isFile) { "No such file: $file" }
            fun float(key: String, default: Float) = values[key]?.toFloat() ?: default
            fun int(key: String, default: Int) = values[key]?.toInt() ?: default

            val canny = EdgeDetector.Canny(
                sigma = float("canny-sigma", 1.4f),
                highPercentile = float("canny-high", 0.85f),
                lowRatio = float("canny-low", 0.4f),
                minMagnitude = float("canny-min", 0.25f),
            )
            val xdog = EdgeDetector.Xdog(
                sigma = float("xdog-sigma", 1.0f),
                k = float("xdog-k", 1.6f),
                tau = float("xdog-tau", 0.98f),
                epsilon = float("xdog-epsilon", -0.01f),
            )
            val detectors = when (val name = values["detector"] ?: "both") {
                "canny" -> listOf("canny" to canny)
                "xdog" -> listOf("xdog" to xdog)
                "both" -> listOf("canny" to canny, "xdog" to xdog)
                else -> throw IllegalArgumentException("Unknown detector '$name': use canny, xdog or both")
            }
            val unknown = values.keys - KNOWN
            require(unknown.isEmpty()) { "Unknown option(s): ${unknown.joinToString { "--$it" }}" }
            return LabOptions(
                photo = file,
                out = File(values["out"] ?: "build/outline-lab"),
                longSide = int("long-side", 1536),
                screenLongSide = int("screen-long-side", 2992),
                detectors = detectors,
                style = OutlineStyle(
                    halfWidth = float("half-width", 1.0f),
                    glowRadius = float("glow-radius", 4.0f),
                    glowStrength = float("glow-strength", 0.35f),
                ),
                minComponent = int("min-component", 24),
            )
        }

        private val KNOWN = setOf(
            "out", "long-side", "screen-long-side", "detector", "half-width", "glow-radius", "glow-strength",
            "min-component", "canny-sigma", "canny-high", "canny-low", "canny-min", "xdog-sigma", "xdog-k",
            "xdog-tau", "xdog-epsilon",
        )
    }
}

/**
 * The outline drawn at screen size the way the phone will: the working-size distance field sampled bilinearly,
 * scaled to screen pixels, then shaded with [OutlineShading].
 */
private class Preview(val width: Int, val height: Int, val intensity: FloatArray) {
    val litFraction: Float get() = intensity.count { it > 0.05f }.toFloat() / intensity.size
    val meanIntensity: Float get() = intensity.sum() / intensity.size

    fun white(): BufferedImage = image { i ->
        val v = (intensity[i] * 255).roundToInt()
        (v shl 16) or (v shl 8) or v
    }

    /** Each line pixel in the photo's colour there, scaled to full brightness, then dimmed by the line intensity. */
    fun tinted(photoArgb: IntArray): BufferedImage = image { i ->
        val c = photoArgb[i]
        val r = (c shr 16) and 0xff
        val g = (c shr 8) and 0xff
        val b = c and 0xff
        val peak = max(max(r, g), b).coerceAtLeast(1)
        val k = intensity[i] * 255f / peak
        ((r * k).roundToInt() shl 16) or ((g * k).roundToInt() shl 8) or (b * k).roundToInt()
    }

    private fun image(rgbAt: (Int) -> Int): BufferedImage {
        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        out.setRGB(0, 0, width, height, IntArray(width * height) { rgbAt(it) }, 0, width)
        return out
    }

    companion object {
        // Stands in for "no line anywhere" so bilinear sampling stays finite.
        private const val FAR = 1e6f

        fun render(
            distances: FloatArray,
            w: Int,
            h: Int,
            screen: BufferedImage,
            scale: Float,
            style: OutlineStyle,
        ): Preview {
            val sw = screen.width
            val sh = screen.height
            fun at(x: Int, y: Int): Float = distances[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)].coerceAtMost(FAR)
            val out = FloatArray(sw * sh)
            for (sy in 0 until sh) {
                val fy = (sy + 0.5f) / scale - 0.5f
                val y0 = floor(fy).toInt()
                val ty = fy - y0
                for (sx in 0 until sw) {
                    val fx = (sx + 0.5f) / scale - 0.5f
                    val x0 = floor(fx).toInt()
                    val tx = fx - x0
                    val top = at(x0, y0) * (1 - tx) + at(x0 + 1, y0) * tx
                    val bottom = at(x0, y0 + 1) * (1 - tx) + at(x0 + 1, y0 + 1) * tx
                    val d = (top * (1 - ty) + bottom * ty) * scale
                    out[sy * sw + sx] = OutlineShading.intensity(d, style)
                }
            }
            return Preview(sw, sh, out)
        }
    }
}

private fun BufferedImage.argb(): IntArray = getRGB(0, 0, width, height, null, 0, width)

private fun maskImage(mask: EdgeMask): BufferedImage {
    val out = BufferedImage(mask.width, mask.height, BufferedImage.TYPE_INT_RGB)
    out.setRGB(
        0,
        0,
        mask.width,
        mask.height,
        IntArray(mask.edges.size) { if (mask.edges[it]) 0xffffff else 0 },
        0,
        mask.width,
    )
    return out
}

/** [source] scaled so its longer side is [longSide] px (never up), halving first for a clean bilinear result. */
private fun scaleToLongSide(source: BufferedImage, longSide: Int): BufferedImage {
    var image = resize(source, source.width, source.height)
    val factor = longSide.toDouble() / max(image.width, image.height)
    if (factor >= 1.0) return image
    val targetW = (image.width * factor).roundToInt().coerceAtLeast(1)
    val targetH = (image.height * factor).roundToInt().coerceAtLeast(1)
    while (image.width / 2 >= targetW && image.height / 2 >= targetH) {
        image = resize(image, image.width / 2, image.height / 2)
    }
    return if (image.width != targetW || image.height != targetH) resize(image, targetW, targetH) else image
}

private fun resize(source: BufferedImage, width: Int, height: Int): BufferedImage {
    val out = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g = out.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g.drawImage(source, 0, 0, width, height, null)
    g.dispose()
    return out
}

private fun writePng(image: BufferedImage, file: File) {
    check(ImageIO.write(image, "png", file)) { "No PNG writer available" }
}
