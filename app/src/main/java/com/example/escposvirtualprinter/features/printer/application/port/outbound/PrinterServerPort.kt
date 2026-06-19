package com.example.escposvirtualprinter.features.printer.application.port.outbound

import com.example.escposvirtualprinter.features.printer.domain.model.Receipt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface PrinterServerPort {
    val status: StateFlow<PrinterServerPortStatus>
    val events: Flow<PrinterServerEvent>
    suspend fun start(port: Int)
    suspend fun stop()
}

data class PrinterServerPortStatus(
    val running: Boolean = false,
    val port: Int = 9100,
    val clientCount: Int = 0,
    val lastError: String? = null,
)

sealed interface PrinterServerEvent {
    data class ReceiptUpdated(val receipt: Receipt) : PrinterServerEvent
    data class ReceiptCompleted(val receipt: Receipt) : PrinterServerEvent
    data class Error(val message: String) : PrinterServerEvent
}
