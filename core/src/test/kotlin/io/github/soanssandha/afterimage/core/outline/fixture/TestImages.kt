package io.github.soanssandha.afterimage.core.outline.fixture

import io.github.soanssandha.afterimage.core.outline.EdgeMask
import io.github.soanssandha.afterimage.core.outline.GrayImage
import kotlin.random.Random

/** Synthetic test images. Tests never use photos (.claude/rules/git-and-privacy.md §3). */
object TestImages {

    fun flat(width: Int, height: Int, value: Float): GrayImage =
        GrayImage(width, height, FloatArray(width * height) { value })

    /** A rectangle covering x in [x0, x1) and y in [y0, y1), [inside] on [outside]. */
    fun rectangle(
        width: Int,
        height: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        inside: Float = 0.9f,
        outside: Float = 0.1f,
    ): GrayImage = of(width, height) { x, y -> if (x in x0 until x1 && y in y0 until y1) inside else outside }

    fun of(width: Int, height: Int, value: (Int, Int) -> Float): GrayImage =
        GrayImage(width, height, FloatArray(width * height) { i -> value(i % width, i / width) })

    /** [base] plus uniform noise of at most [amplitude], repeatable through [seed]. */
    fun noisy(base: GrayImage, amplitude: Float, seed: Int): GrayImage {
        val random = Random(seed)
        return GrayImage(
            base.width,
            base.height,
            FloatArray(base.pixels.size) { i -> base.pixels[i] + (random.nextFloat() * 2 - 1) * amplitude },
        )
    }

    fun mask(width: Int, height: Int, vararg points: Pair<Int, Int>): EdgeMask {
        val edges = BooleanArray(width * height)
        for ((x, y) in points) edges[y * width + x] = true
        return EdgeMask(width, height, edges)
    }

    fun randomMask(width: Int, height: Int, density: Float, seed: Int): EdgeMask {
        val random = Random(seed)
        return EdgeMask(width, height, BooleanArray(width * height) { random.nextFloat() < density })
    }
}
