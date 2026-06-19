package com.example.escposvirtualprinter.features.printer.domain.parser

import com.example.escposvirtualprinter.features.printer.domain.model.ReceiptBlock
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptBuilderTest {
    @Test
    fun preservesBlankLineFromConsecutiveLineFeeds() {
        val parser = EscposParser(Charsets.UTF_8)
        val builder = ReceiptBuilder(sourceHost = null, sourcePort = null)

        parser.feed("Cafe latte\n\nSample ade\n".encodeToByteArray()).forEach { event ->
            builder.apply(event)
        }

        val lines = builder.complete().blocks.filterIsInstance<ReceiptBlock.TextLine>()

        assertEquals(3, lines.size)
        assertEquals("Cafe latte", lines[0].segments.joinToString("") { it.text })
        assertEquals("", lines[1].segments.joinToString("") { it.text })
        assertEquals("Sample ade", lines[2].segments.joinToString("") { it.text })
    }
}
