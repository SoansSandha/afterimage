package io.github.soanssandha.afterimage.core.outline

import kotlin.math.roundToInt

/**
 * Packs an [OutlineField] for the cache and the GPU, and reads the cache back. Stored as a 20-byte header (magic
 * "AIOF", format [VERSION], width, height, maxDistance as float bits; big-endian) followed by one byte per pixel.
 * A file from another format version decodes to null, so the app recomputes it.
 */
object FieldCodec {

    const val VERSION = 1

    /**
     * Distances beyond this (working px) all look the same: about 3× the default glow reach at the 1536 → 2992 px
     * upscale. 255 levels over it give a step of under 0.05 px.
     */
    const val DEFAULT_MAX_DISTANCE = 12f

    private const val MAGIC = 0x41494F46 // "AIOF"
    private const val HEADER_BYTES = 20

    fun pack(field: OutlineField, maxDistance: Float = DEFAULT_MAX_DISTANCE): PackedField {
        require(maxDistance > 0f) { "maxDistance must be above 0, was $maxDistance" }
        val bytes = ByteArray(field.distances.size) { i ->
            val d = field.distances[i].coerceAtMost(maxDistance)
            (d / maxDistance * 255f).roundToInt().toByte()
        }
        return PackedField(field.width, field.height, maxDistance, bytes)
    }

    fun encode(field: PackedField): ByteArray {
        val out = ByteArray(HEADER_BYTES + field.bytes.size)
        putInt(out, 0, MAGIC)
        putInt(out, 4, VERSION)
        putInt(out, 8, field.width)
        putInt(out, 12, field.height)
        putInt(out, 16, field.maxDistance.toRawBits())
        field.bytes.copyInto(out, HEADER_BYTES)
        return out
    }

    /** The field stored in [data], or null if it isn't one, comes from another format version, or is cut short. */
    fun decode(data: ByteArray): PackedField? {
        if (data.size < HEADER_BYTES || getInt(data, 0) != MAGIC || getInt(data, 4) != VERSION) return null
        val width = getInt(data, 8)
        val height = getInt(data, 12)
        val maxDistance = Float.fromBits(getInt(data, 16))
        if (width <= 0 || height <= 0 || !(maxDistance > 0f)) return null
        if (data.size.toLong() != HEADER_BYTES + width.toLong() * height) return null
        return PackedField(width, height, maxDistance, data.copyOfRange(HEADER_BYTES, data.size))
    }

    private fun putInt(out: ByteArray, at: Int, value: Int) {
        for (i in 0 until 4) out[at + i] = (value ushr (24 - 8 * i)).toByte()
    }

    private fun getInt(data: ByteArray, at: Int): Int {
        var value = 0
        for (i in 0 until 4) value = (value shl 8) or (data[at + i].toInt() and 0xff)
        return value
    }
}
