package com.example.escposvirtualprinter.features.printer.domain.parser

import com.example.escposvirtualprinter.features.printer.domain.model.TextAlign
import com.example.escposvirtualprinter.features.printer.domain.model.TextStyle

sealed interface EscposEvent {
    data class Text(val value: String, val style: TextStyle, val align: TextAlign) : EscposEvent
    data object LineFeed : EscposEvent
    data class SetAlign(val align: TextAlign) : EscposEvent
    data class SetStyle(val style: TextStyle) : EscposEvent
    data object Initialize : EscposEvent
    data object Cut : EscposEvent
    data class FeedLines(val count: Int) : EscposEvent
    data class RasterImage(
        val widthBytes: Int,
        val heightDots: Int,
        val data: ByteArray,
        val align: TextAlign,
        val widthScale: Int,
        val heightScale: Int,
    ) : EscposEvent
    data class IgnoredCommand(val label: String) : EscposEvent
    data class StatusRequest(val value: Int) : EscposEvent
    data class UnknownCommand(val bytes: List<Int>, val offset: Long) : EscposEvent
}
