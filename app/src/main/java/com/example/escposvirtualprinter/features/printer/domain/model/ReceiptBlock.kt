package com.example.escposvirtualprinter.features.printer.domain.model

sealed interface ReceiptBlock {
    data class TextLine(
        val segments: List<TextSegment>,
        val align: TextAlign,
    ) : ReceiptBlock

    data class DeviceEvent(
        val label: String,
        val payload: String? = null,
    ) : ReceiptBlock

    data class RasterImage(
        val widthBytes: Int,
        val heightDots: Int,
        val data: ByteArray,
        val align: TextAlign,
        val widthScale: Int = 1,
        val heightScale: Int = 1,
    ) : ReceiptBlock {
        val widthDots: Int = widthBytes * 8
    }
}

data class TextSegment(
    val text: String,
    val style: TextStyle,
)

data class TextStyle(
    val bold: Boolean = false,
    val underline: Boolean = false,
    val widthScale: Int = 1,
    val heightScale: Int = 1,
)

enum class TextAlign {
    Left,
    Center,
    Right,
}
