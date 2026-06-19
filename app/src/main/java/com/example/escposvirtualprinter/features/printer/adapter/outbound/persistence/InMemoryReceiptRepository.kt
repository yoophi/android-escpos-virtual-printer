package com.example.escposvirtualprinter.features.printer.adapter.outbound.persistence

import com.example.escposvirtualprinter.features.printer.application.port.outbound.ReceiptRepository
import com.example.escposvirtualprinter.features.printer.domain.model.Receipt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class InMemoryReceiptRepository(
    private val maxReceipts: Int,
) : ReceiptRepository {
    private val mutableReceipts = MutableStateFlow<List<Receipt>>(emptyList())

    override val receipts: StateFlow<List<Receipt>> = mutableReceipts

    override suspend fun upsert(receipt: Receipt) {
        mutableReceipts.update { current ->
            val withoutExisting = current.filterNot { it.id == receipt.id }
            (listOf(receipt) + withoutExisting).take(maxReceipts)
        }
    }

    override suspend fun delete(receiptId: String) {
        mutableReceipts.update { current -> current.filterNot { it.id == receiptId } }
    }

    override suspend fun clear() {
        mutableReceipts.value = emptyList()
    }
}
