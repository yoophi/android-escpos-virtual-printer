package com.example.escposvirtualprinter

import android.content.Context
import com.example.escposvirtualprinter.adapter.outbound.network.TcpEscposPrinterServer
import com.example.escposvirtualprinter.adapter.outbound.persistence.InMemoryReceiptRepository
import com.example.escposvirtualprinter.application.port.inbound.PrinterServerUseCase
import com.example.escposvirtualprinter.application.service.VirtualPrinterApplicationService
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
