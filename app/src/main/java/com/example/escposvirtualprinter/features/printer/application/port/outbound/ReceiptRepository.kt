package com.example.escposvirtualprinter.features.printer.application.port.outbound

import com.example.escposvirtualprinter.features.printer.domain.model.Receipt
import kotlinx.coroutines.flow.StateFlow

interface ReceiptRepository {
    val receipts: StateFlow<List<Receipt>>
    suspend fun upsert(receipt: Receipt)
    suspend fun delete(receiptId: String)
    suspend fun clear()
}
