package io.github.soanssandha.afterimage.core.config

import io.github.soanssandha.afterimage.core.outline.OutlineDetail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LookCodecTest {

    @Test
    fun `a look survives encoding`() {
        for (look in listOf(
            WallpaperLook(),
            WallpaperLook(0.35f, LineColor.WHITE),
            WallpaperLook(1f, LineColor.PHOTO),
        )) {
            assertEquals(look, LookCodec.decode(LookCodec.encode(look)))
        }
    }

    @Test
    fun `the stored form is stable`() {
        assertEquals("detail=0.25\nline_color=white\n", LookCodec.encode(WallpaperLook(0.25f, LineColor.WHITE)))
    }

    @Test
    fun `missing or empty text gives the defaults`() {
        assertEquals(WallpaperLook(), LookCodec.decode(""))
        assertEquals(WallpaperLook(), LookCodec.decode("\n\n"))
    }

    @Test
    fun `each bad value falls back on its own`() {
        assertEquals(WallpaperLook(0.7f, LineColor.PHOTO), LookCodec.decode("detail=0.7\nline_color=purple"))
        assertEquals(
            WallpaperLook(OutlineDetail.DEFAULT, LineColor.WHITE),
            LookCodec.decode("detail=lots\nline_color=white"),
        )
        assertEquals(
            WallpaperLook(OutlineDetail.DEFAULT, LineColor.WHITE),
            LookCodec.decode("detail=NaN\nline_color=white"),
        )
    }

    @Test
    fun `out of range detail is clamped`() {
        assertEquals(OutlineDetail.MAX, LookCodec.decode("detail=3").detail)
        assertEquals(OutlineDetail.MIN, LookCodec.decode("detail=-1").detail)
    }

    @Test
    fun `unknown keys, stray lines and spaces are tolerated`() {
        val text = "# a comment\n=orphan\n shade = 0.4 \n line_color = white \ndetail= 0.6\nnoequals"
        assertEquals(WallpaperLook(0.6f, LineColor.WHITE), LookCodec.decode(text))
    }

    @Test
    fun `a look rejects detail outside 0 to 1`() {
        assertFailsWith<IllegalArgumentException> { WallpaperLook(detail = 1.5f) }
        assertFailsWith<IllegalArgumentException> { WallpaperLook(detail = Float.NaN) }
    }
}
