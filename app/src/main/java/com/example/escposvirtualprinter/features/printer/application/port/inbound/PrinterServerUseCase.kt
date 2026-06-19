package com.example.escposvirtualprinter.features.printer.application.port.inbound

import com.example.escposvirtualprinter.features.printer.domain.model.Receipt
import kotlinx.coroutines.flow.StateFlow

interface PrinterServerUseCase {
    val state: StateFlow<PrinterServerState>
    suspend fun start(port: Int)
    suspend fun stop()
    suspend fun clearReceipts()
    suspend fun deleteReceipt(receiptId: String)
}

data class PrinterServerState(
    val running: Boolean = false,
    val port: Int = 9100,
    val clientCount: Int = 0,
    val receipts: List<Receipt> = emptyList(),
    val lastError: String? = null,
)
