package com.example.escposvirtualprinter.features.printer.domain.model

import java.time.Instant

data class Receipt(
    val id: String,
    val createdAt: Instant,
    val completedAt: Instant?,
    val sourceHost: String?,
    val sourcePort: Int?,
    val byteCount: Long,
    val status: ReceiptStatus,
    val blocks: List<ReceiptBlock>,
    val warnings: List<ParseWarning> = emptyList(),
    val rawBytes: ByteArray = byteArrayOf(),
)

enum class ReceiptStatus {
    Receiving,
    Completed,
    Error,
}

data class ParseWarning(
    val message: String,
    val offset: Long,
)
