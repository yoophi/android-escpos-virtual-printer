package com.example.escposvirtualprinter.domain.parser

import com.example.escposvirtualprinter.domain.model.TextAlign
import com.example.escposvirtualprinter.domain.model.TextStyle
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

class EscposParser(
    private val charset: Charset = Charsets.UTF_8,
) {
    private val pending = ArrayDeque<Int>()
    private val textBuffer = ByteArrayOutputStream()
    private var offset = 0L
    private var style = TextStyle()
    private var align = TextAlign.Left

    fun feed(chunk: ByteArray): List<EscposEvent> {
        chunk.forEach { pending.addLast(it.toInt() and 0xff) }
        val events = mutableListOf<EscposEvent>()

        while (pending.isNotEmpty()) {
            val first = pending.first()
            when (first) {
                LF -> {
                    consume(1)
                    flushText(events)
                    events += EscposEvent.LineFeed
                }
                CR -> {
                    consume(1)
                }
                ESC -> {
                    if (!parseEsc(events)) break
                }
                GS -> {
                    if (!parseGs(events)) break
                }
                DLE -> {
                    if (!parseDle(events)) break
                }
                else -> {
                    textBuffer.write(first)
                    consume(1)
                }
            }
        }

        return events
    }

    fun flush(): List<EscposEvent> {
        val events = mutableListOf<EscposEvent>()
        flushText(events)
        return events
    }

    private fun parseEsc(events: MutableList<EscposEvent>): Boolean {
        if (pending.size < 2) return false
        return when (val command = pending.elementAt(1)) {
            0x40 -> {
                consume(2)
                flushText(events)
                style = TextStyle()
                align = TextAlign.Left
                events += EscposEvent.Initialize
                events += EscposEvent.SetStyle(style)
                events += EscposEvent.SetAlign(align)
                true
            }
            0x61 -> {
                if (pending.size < 3) return false
                val value = pending.elementAt(2)
                consume(3)
                flushText(events)
                align = when (value) {
                    1 -> TextAlign.Center
                    2 -> TextAlign.Right
                    else -> TextAlign.Left
                }
                events += EscposEvent.SetAlign(align)
                true
            }
            0x45 -> {
                if (pending.size < 3) return false
                val value = pending.elementAt(2)
                consume(3)
                flushText(events)
                style = style.copy(bold = value and 0x01 == 1)
                events += EscposEvent.SetStyle(style)
                true
            }
            0x2d -> {
                if (pending.size < 3) return false
                val value = pending.elementAt(2)
                consume(3)
                flushText(events)
                style = style.copy(underline = value != 0)
                events += EscposEvent.SetStyle(style)
                true
            }
            0x64 -> {
                if (pending.size < 3) return false
                val value = pending.elementAt(2)
                consume(3)
                flushText(events)
                events += EscposEvent.FeedLines(value)
                true
            }
            else -> {
                consume(2)
                flushText(events)
                events += EscposEvent.UnknownCommand(listOf(ESC, command), offset)
                true
            }
        }
    }

    private fun parseGs(events: MutableList<EscposEvent>): Boolean {
        if (pending.size < 2) return false
        return when (val command = pending.elementAt(1)) {
            0x21 -> {
                if (pending.size < 3) return false
                val value = pending.elementAt(2)
                consume(3)
                flushText(events)
                val width = ((value shr 4) and 0x07) + 1
                val height = (value and 0x07) + 1
                style = style.copy(widthScale = width, heightScale = height)
                events += EscposEvent.SetStyle(style)
                true
            }
            0x56 -> {
                if (pending.size < 2) return false
                val commandSize = when {
                    pending.size >= 3 && pending.elementAt(2) in 0..1 -> 3
                    pending.size >= 4 && pending.elementAt(2) in 65..66 -> 4
                    else -> 2
                }
                consume(commandSize)
                flushText(events)
                events += EscposEvent.Cut
                true
            }
            0x76 -> parseRasterImage(events)
            else -> {
                consume(2)
                flushText(events)
                events += EscposEvent.UnknownCommand(listOf(GS, command), offset)
                true
            }
        }
    }

    private fun parseRasterImage(events: MutableList<EscposEvent>): Boolean {
        if (pending.size < 8) return false
        val marker = pending.elementAt(2)
        if (marker != 0x30) {
            consume(2)
            flushText(events)
            events += EscposEvent.UnknownCommand(listOf(GS, 0x76), offset)
            return true
        }

        val mode = pending.elementAt(3)
        val widthBytes = pending.elementAt(4) + pending.elementAt(5) * 256
        val heightDots = pending.elementAt(6) + pending.elementAt(7) * 256
        val dataSize = widthBytes * heightDots
        if (mode !in RASTER_IMAGE_MODES || widthBytes <= 0 || heightDots <= 0 || dataSize > MAX_RASTER_IMAGE_BYTES) {
            consume(8)
            flushText(events)
            events += EscposEvent.UnknownCommand(listOf(GS, 0x76, marker, mode), offset)
            return true
        }
        if (pending.size < 8 + dataSize) return false

        val data = ByteArray(dataSize) { index -> pending.elementAt(8 + index).toByte() }
        consume(8 + dataSize)
        flushText(events)
        events += EscposEvent.RasterImage(
            widthBytes = widthBytes,
            heightDots = heightDots,
            data = data,
            align = align,
            widthScale = if (mode == 1 || mode == 49 || mode == 3 || mode == 51) 2 else 1,
            heightScale = if (mode == 2 || mode == 50 || mode == 3 || mode == 51) 2 else 1,
        )
        return true
    }

    private fun parseDle(events: MutableList<EscposEvent>): Boolean {
        if (pending.size < 2) return false
        val command = pending.elementAt(1)
        if (command == 0x04) {
            if (pending.size < 3) return false
            val value = pending.elementAt(2)
            consume(3)
            flushText(events)
            events += EscposEvent.StatusRequest(value)
            return true
        }
        consume(2)
        flushText(events)
        events += EscposEvent.UnknownCommand(listOf(DLE, command), offset)
        return true
    }

    private fun flushText(events: MutableList<EscposEvent>) {
        if (textBuffer.size() == 0) return
        events += EscposEvent.Text(textBuffer.toByteArray().toString(charset), style, align)
        textBuffer.reset()
    }

    private fun consume(count: Int) {
        repeat(count) {
            pending.removeFirst()
            offset++
        }
    }

    private companion object {
        const val LF = 0x0a
        const val CR = 0x0d
        const val ESC = 0x1b
        const val GS = 0x1d
        const val DLE = 0x10
        const val MAX_RASTER_IMAGE_BYTES = 1_048_576
        val RASTER_IMAGE_MODES = setOf(0, 1, 2, 3, 48, 49, 50, 51)
    }
}
