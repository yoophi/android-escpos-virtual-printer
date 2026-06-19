package com.example.escposvirtualprinter.app

import android.content.Context
import com.example.escposvirtualprinter.features.printer.adapter.outbound.network.TcpEscposPrinterServer
import com.example.escposvirtualprinter.features.printer.adapter.outbound.persistence.InMemoryReceiptRepository
import com.example.escposvirtualprinter.features.printer.application.port.inbound.PrinterServerUseCase
import com.example.escposvirtualprinter.features.printer.application.service.VirtualPrinterApplicationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object AppGraph {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val receiptRepository = InMemoryReceiptRepository(maxReceipts = 100)
    private val printerServer = TcpEscposPrinterServer(appScope)

    val printerUseCase: PrinterServerUseCase =
        VirtualPrinterApplicationService(
            server = printerServer,
            receiptRepository = receiptRepository,
            externalScope = appScope,
        )

    fun init(context: Context) {
        context.applicationContext
    }
}
