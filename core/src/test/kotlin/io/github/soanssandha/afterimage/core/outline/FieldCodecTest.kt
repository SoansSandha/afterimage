package io.github.soanssandha.afterimage.core.outline

import io.github.soanssandha.afterimage.core.outline.fixture.TestImages
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FieldCodecTest {

    private val field = OutlinePipeline.run(TestImages.rectangle(80, 120, 20, 30, 60, 90))

    @Test
    fun `packing keeps distances within half a step and caps far ones`() {
        val packed = FieldCodec.pack(field, maxDistance = 12f)
        val step = 12f / 255

        for (y in 0 until field.height) {
            for (x in 0 until field.width) {
                val expected = field.distances[y * field.width + x].coerceAtMost(12f)
                assertEquals(expected, packed.distanceAt(x, y), step / 2 + 1e-5f, "at ($x, $y)")
            }
        }
        assertEquals(12f, packed.distanceAt(0, 0))
    }

    @Test
    fun `an empty outline packs to all far`() {
        val empty = OutlinePipeline.run(TestImages.flat(10, 10, 0.5f))

        assertTrue(FieldCodec.pack(empty).bytes.all { it == 255.toByte() })
    }

    @Test
    fun `encoding round-trips`() {
        val packed = FieldCodec.pack(field)

        val decoded = assertNotNull(FieldCodec.decode(FieldCodec.encode(packed)))

        assertEquals(packed.width, decoded.width)
        assertEquals(packed.height, decoded.height)
        assertEquals(packed.maxDistance, decoded.maxDistance)
        assertContentEquals(packed.bytes, decoded.bytes)
    }

    @Test
    fun `anything that isn't a current-version field decodes to null`() {
        val good = FieldCodec.encode(FieldCodec.pack(field))

        assertNull(FieldCodec.decode(ByteArray(10)))
        assertNull(FieldCodec.decode(good.copyOf().also { it[0] = 0 }))
        assertNull(FieldCodec.decode(good.copyOf().also { it[7] = (FieldCodec.VERSION + 1).toByte() }))
        assertNull(FieldCodec.decode(good.copyOf(good.size - 1)))
        assertNull(FieldCodec.decode(good.copyOf().also { it[8] = 0x80.toByte() })) // negative width
        assertNull(FieldCodec.decode(good.copyOf().also { for (i in 16..19) it[i] = 0 })) // zero maxDistance
    }

    @Test
    fun `rejects inconsistent fields`() {
        assertFailsWith<IllegalArgumentException> { FieldCodec.pack(field, maxDistance = 0f) }
        assertFailsWith<IllegalArgumentException> { PackedField(0, 1, 1f, ByteArray(0)) }
        assertFailsWith<IllegalArgumentException> { PackedField(2, 2, 0f, ByteArray(4)) }
        assertFailsWith<IllegalArgumentException> { PackedField(2, 2, 1f, ByteArray(3)) }
    }
}
