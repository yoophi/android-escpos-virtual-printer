package com.example.escposvirtualprinter.application.port.outbound

import com.example.escposvirtualprinter.domain.model.Receipt
import kotlinx.coroutines.flow.StateFlow

interface ReceiptRepository {
    val receipts: StateFlow<List<Receipt>>
    suspend fun upsert(receipt: Receipt)
    suspend fun clear()
}
