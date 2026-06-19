package com.example.escposvirtualprinter.features.printer.domain.parser

import com.example.escposvirtualprinter.features.printer.domain.model.ParseWarning
import com.example.escposvirtualprinter.features.printer.domain.model.Receipt
import com.example.escposvirtualprinter.features.printer.domain.model.ReceiptBlock
import com.example.escposvirtualprinter.features.printer.domain.model.ReceiptStatus
import com.example.escposvirtualprinter.features.printer.domain.model.TextAlign
import com.example.escposvirtualprinter.features.printer.domain.model.TextSegment
import com.example.escposvirtualprinter.features.printer.domain.model.TextStyle
import java.time.Instant
import java.util.UUID
import java.io.ByteArrayOutputStream

class ReceiptBuilder(
    private val sourceHost: String?,
    private val sourcePort: Int?,
) {
    private val id = UUID.randomUUID().toString()
    private val createdAt = Instant.now()
    private val blocks = mutableListOf<ReceiptBlock>()
    private val lineSegments = mutableListOf<TextSegment>()
    private val warnings = mutableListOf<ParseWarning>()
    private val rawBytes = ByteArrayOutputStream()
    private var currentAlign = TextAlign.Left
    private var currentStyle = TextStyle()
    private var byteCount = 0L

    fun addBytes(count: Int) {
        byteCount += count
    }

    fun addRawBytes(bytes: ByteArray, length: Int) {
        val allowed = (MAX_DEBUG_RAW_BYTES - rawBytes.size()).coerceAtLeast(0)
        if (allowed > 0) {
            rawBytes.write(bytes, 0, length.coerceAtMost(allowed))
        }
    }

    fun apply(event: EscposEvent): Boolean {
        when (event) {
            is EscposEvent.Text -> {
                currentStyle = event.style
                currentAlign = event.align
                lineSegments += TextSegment(event.value, event.style)
            }
            EscposEvent.LineFeed -> commitLine()
            is EscposEvent.SetAlign -> currentAlign = event.align
            is EscposEvent.SetStyle -> currentStyle = event.style
            EscposEvent.Initialize -> {
                commitLine()
                currentAlign = TextAlign.Left
                currentStyle = TextStyle()
            }
            is EscposEvent.FeedLines -> repeat(event.count.coerceAtMost(20)) { commitLine(force = true) }
            is EscposEvent.RasterImage -> {
                commitLine()
                blocks += ReceiptBlock.RasterImage(
                    widthBytes = event.widthBytes,
                    heightDots = event.heightDots,
                    data = event.data,
                    align = event.align,
                    widthScale = event.widthScale,
                    heightScale = event.heightScale,
                )
            }
            is EscposEvent.StatusRequest -> {
                blocks += ReceiptBlock.DeviceEvent("Status request", "DLE EOT ${event.value}")
            }
            is EscposEvent.IgnoredCommand -> Unit
            is EscposEvent.UnknownCommand -> {
                warnings += ParseWarning("Unknown command: ${event.bytes.joinToString(" ") { it.toString(16).padStart(2, '0') }}", event.offset)
            }
            EscposEvent.Cut -> {
                commitLine()
                blocks += ReceiptBlock.DeviceEvent("Paper cut")
                return true
            }
        }
        return false
    }

    fun snapshot(status: ReceiptStatus = ReceiptStatus.Receiving): Receipt {
        return Receipt(
            id = id,
            createdAt = createdAt,
            completedAt = if (status == ReceiptStatus.Completed) Instant.now() else null,
            sourceHost = sourceHost,
            sourcePort = sourcePort,
            byteCount = byteCount,
            status = status,
            blocks = blocks + listOfNotNull(pendingLineBlockOrNull()),
            warnings = warnings,
            rawBytes = rawBytes.toByteArray(),
        )
    }

    fun complete(): Receipt {
        commitLine()
        return snapshot(ReceiptStatus.Completed)
    }

    private fun commitLine(force: Boolean = false) {
        if (lineSegments.isEmpty() && !force) return
        blocks += ReceiptBlock.TextLine(
            segments = lineSegments.toList(),
            align = currentAlign,
        )
        lineSegments.clear()
    }

    private fun pendingLineBlockOrNull(): ReceiptBlock.TextLine? {
        if (lineSegments.isEmpty()) return null
        return ReceiptBlock.TextLine(lineSegments.toList(), currentAlign)
    }

    private companion object {
        const val MAX_DEBUG_RAW_BYTES = 256 * 1024
    }
}
