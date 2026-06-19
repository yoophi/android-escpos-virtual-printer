package com.example.escposvirtualprinter.application.service

import com.example.escposvirtualprinter.application.port.inbound.PrinterServerState
import com.example.escposvirtualprinter.application.port.inbound.PrinterServerUseCase
import com.example.escposvirtualprinter.application.port.outbound.PrinterServerEvent
import com.example.escposvirtualprinter.application.port.outbound.PrinterServerPort
import com.example.escposvirtualprinter.application.port.outbound.ReceiptRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VirtualPrinterApplicationService(
    private val server: PrinterServerPort,
    private val receiptRepository: ReceiptRepository,
    externalScope: CoroutineScope,
) : PrinterServerUseCase {
    private val scope = CoroutineScope(externalScope.coroutineContext + SupervisorJob())

    override val state: StateFlow<PrinterServerState> =
        combine(server.status, receiptRepository.receipts) { status, receipts ->
            PrinterServerState(
                running = status.running,
                port = status.port,
                clientCount = status.clientCount,
                receipts = receipts,
                lastError = status.lastError,
            )
        }.stateIn(scope, SharingStarted.Eagerly, PrinterServerState())

    init {
        scope.launch {
            server.events.collect { event ->
                when (event) {
                    is PrinterServerEvent.ReceiptUpdated -> receiptRepository.upsert(event.receipt)
                    is PrinterServerEvent.ReceiptCompleted -> receiptRepository.upsert(event.receipt)
                    is PrinterServerEvent.Error -> Unit
                }
            }
        }
    }

    override suspend fun start(port: Int) {
        server.start(port)
    }

    override suspend fun stop() {
        server.stop()
    }

    override suspend fun clearReceipts() {
        receiptRepository.clear()
    }
}
