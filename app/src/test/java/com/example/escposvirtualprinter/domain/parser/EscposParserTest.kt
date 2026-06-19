package com.example.escposvirtualprinter.domain.parser

import com.example.escposvirtualprinter.domain.model.TextAlign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscposParserTest {
    @Test
    fun parsesTextAndLineFeed() {
        val parser = EscposParser()

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
}
