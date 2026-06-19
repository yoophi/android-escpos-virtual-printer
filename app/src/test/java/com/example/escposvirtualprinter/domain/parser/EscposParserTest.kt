package com.example.escposvirtualprinter.domain.parser

import com.example.escposvirtualprinter.domain.model.TextAlign
import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscposParserTest {
    @Test
    fun parsesTextAndLineFeed() {
        val parser = EscposParser(Charsets.UTF_8)

        val events = parser.feed("hello\n".encodeToByteArray())

        assertTrue(events[0] is EscposEvent.Text)
        assertEquals("hello", (events[0] as EscposEvent.Text).value)
        assertTrue(events[1] is EscposEvent.LineFeed)
    }

    @Test
    fun waitsForCommandSplitAcrossChunks() {
        val parser = EscposParser()

        assertEquals(emptyList<EscposEvent>(), parser.feed(byteArrayOf(0x1b)))
        val events = parser.feed(byteArrayOf(0x61, 0x01))

        assertEquals(EscposEvent.SetAlign(TextAlign.Center), events.single())
    }

    @Test
    fun parsesCutCommand() {
        val parser = EscposParser()

        val events = parser.feed(byteArrayOf(0x1d, 0x56))

        assertEquals(EscposEvent.Cut, events.single())
    }

    @Test
    fun parsesTextSize() {
        val parser = EscposParser()

        val events = parser.feed(byteArrayOf(0x1d, 0x21, 0x11))
        val style = (events.single() as EscposEvent.SetStyle).style

        assertEquals(2, style.widthScale)
        assertEquals(2, style.heightScale)
    }

    @Test
    fun parsesEscPrintModeWithoutLeakingModeByteAsText() {
        val parser = EscposParser()

        val events = parser.feed(byteArrayOf(0x1b, 0x21, 0x30, 0x41, 0x0a, 0x1b, 0x21, 0x00))

        val style = (events[0] as EscposEvent.SetStyle).style
        assertEquals(2, style.widthScale)
        assertEquals(2, style.heightScale)
        assertEquals("A", (events[1] as EscposEvent.Text).value)
        assertTrue(events[2] is EscposEvent.LineFeed)
        assertEquals(1, (events[3] as EscposEvent.SetStyle).style.widthScale)
        assertEquals(1, (events[3] as EscposEvent.SetStyle).style.heightScale)
    }

    @Test
    fun parsesEucKrKoreanText() {
        val parser = EscposParser(Charset.forName("EUC-KR"))
        val bytes = "한글 영수증\n".toByteArray(Charset.forName("EUC-KR"))

        val events = parser.feed(bytes)

        assertTrue(events[0] is EscposEvent.Text)
        assertEquals("한글 영수증", (events[0] as EscposEvent.Text).value)
        assertTrue(events[1] is EscposEvent.LineFeed)
    }

    @Test
    fun keepsEucKrMultibyteTextAcrossChunks() {
        val parser = EscposParser(Charset.forName("EUC-KR"))
        val bytes = "한글\n".toByteArray(Charset.forName("EUC-KR"))

        assertEquals(emptyList<EscposEvent>(), parser.feed(bytes.copyOfRange(0, 1)))
        val events = parser.feed(bytes.copyOfRange(1, bytes.size))

        assertTrue(events[0] is EscposEvent.Text)
        assertEquals("한글", (events[0] as EscposEvent.Text).value)
        assertTrue(events[1] is EscposEvent.LineFeed)
    }

    @Test
    fun parsesRasterImageCommand() {
        val parser = EscposParser()
        val payload = byteArrayOf(
            0x1d, 0x76, 0x30, 0x00,
            0x02, 0x00,
            0x03, 0x00,
            0xff.toByte(), 0x00,
            0x81.toByte(), 0x7e,
            0xff.toByte(), 0x00,
        )

        val events = parser.feed(payload)
        val image = events.single() as EscposEvent.RasterImage

        assertEquals(2, image.widthBytes)
        assertEquals(3, image.heightDots)
        assertEquals(TextAlign.Left, image.align)
        assertEquals(1, image.widthScale)
        assertEquals(1, image.heightScale)
        assertEquals(6, image.data.size)
    }

    @Test
    fun waitsForRasterImageDataSplitAcrossChunks() {
        val parser = EscposParser()
        val header = byteArrayOf(
            0x1d, 0x76, 0x30, 0x03,
            0x01, 0x00,
            0x02, 0x00,
        )

        assertEquals(emptyList<EscposEvent>(), parser.feed(header))
        val events = parser.feed(byteArrayOf(0xff.toByte(), 0xff.toByte()))
        val image = events.single() as EscposEvent.RasterImage

        assertEquals(1, image.widthBytes)
        assertEquals(2, image.heightDots)
        assertEquals(2, image.widthScale)
        assertEquals(2, image.heightScale)
    }

    @Test
    fun consumesKnownFsCommandsWithoutWarnings() {
        val parser = EscposParser()

        val events = parser.feed(byteArrayOf(0x1c, 0x26, 0x1c, 0x2e, 0x41, 0x0a))

        assertTrue(events[0] is EscposEvent.IgnoredCommand)
        assertTrue(events[1] is EscposEvent.IgnoredCommand)
        assertEquals("A", (events[2] as EscposEvent.Text).value)
        assertTrue(events[3] is EscposEvent.LineFeed)
    }

    @Test
    fun consumesGsParenthesizedGraphicsCommandWithoutLeakingPayload() {
        val parser = EscposParser()

        val events = parser.feed(byteArrayOf(0x1d, 0x28, 0x4c, 0x02, 0x00, 0x30, 0x45, 0x41, 0x0a))

        assertTrue(events[0] is EscposEvent.IgnoredCommand)
        assertEquals("A", (events[1] as EscposEvent.Text).value)
        assertTrue(events[2] is EscposEvent.LineFeed)
    }
}
