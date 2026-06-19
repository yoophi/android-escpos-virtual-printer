package com.example.escposvirtualprinter.adapter.outbound.network

import com.example.escposvirtualprinter.application.port.outbound.PrinterServerEvent
import com.example.escposvirtualprinter.application.port.outbound.PrinterServerPort
import com.example.escposvirtualprinter.application.port.outbound.PrinterServerPortStatus
import com.example.escposvirtualprinter.domain.model.ReceiptStatus
import com.example.escposvirtualprinter.domain.parser.EscposParser
import com.example.escposvirtualprinter.domain.parser.ReceiptBuilder
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.Charset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TcpEscposPrinterServer(
    private val scope: CoroutineScope,
    private val charset: Charset = Charset.forName("EUC-KR"),
) : PrinterServerPort {
    private val mutableStatus = MutableStateFlow(PrinterServerPortStatus())
    private val mutableEvents = MutableSharedFlow<PrinterServerEvent>(extraBufferCapacity = 64)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val clientJobs = mutableSetOf<Job>()

    override val status: StateFlow<PrinterServerPortStatus> = mutableStatus
    override val events: Flow<PrinterServerEvent> = mutableEvents.asSharedFlow()

    override suspend fun start(port: Int) = withContext(Dispatchers.IO) {
        if (mutableStatus.value.running) return@withContext
        runCatching {
            val socket = ServerSocket(port).apply {
                reuseAddress = true
            }
            serverSocket = socket
            mutableStatus.value = PrinterServerPortStatus(running = true, port = port)
            acceptJob = scope.launch(Dispatchers.IO) { acceptLoop(socket, port) }
        }.onFailure { error ->
            mutableStatus.value = PrinterServerPortStatus(
                running = false,
                port = port,
                lastError = error.message ?: "Failed to start TCP server",
            )
            mutableEvents.tryEmit(PrinterServerEvent.Error(error.message ?: "Failed to start TCP server"))
        }
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        acceptJob?.cancel()
        acceptJob = null
        clientJobs.forEach { it.cancel() }
        clientJobs.clear()
        runCatching { serverSocket?.close() }
        serverSocket = null
        mutableStatus.update { it.copy(running = false, clientCount = 0) }
    }

    private suspend fun acceptLoop(socket: ServerSocket, port: Int) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (error: Throwable) {
                if (!socket.isClosed) {
                    mutableStatus.update { it.copy(lastError = error.message) }
                }
                continue
            }
            val job = scope.launch(Dispatchers.IO) {
                handleClient(client)
            }
            clientJobs += job
            mutableStatus.update { it.copy(running = true, port = port, clientCount = clientJobs.size) }
            job.invokeOnCompletion {
                clientJobs.remove(job)
                mutableStatus.update { current -> current.copy(clientCount = clientJobs.size) }
            }
        }
    }

    private suspend fun handleClient(client: Socket) {
        client.use { socket ->
            socket.soTimeout = READ_TIMEOUT_MS
            val parser = EscposParser(charset)
            var builder = ReceiptBuilder(
                sourceHost = socket.inetAddress?.hostAddress,
                sourcePort = socket.port,
            )
            var hasData = false
            var lastDataAt = System.currentTimeMillis()
            val buffer = ByteArray(BUFFER_SIZE)

            while (!socket.isClosed) {
                val read = try {
                    socket.getInputStream().read(buffer)
                } catch (_: SocketTimeoutException) {
                    if (hasData && System.currentTimeMillis() - lastDataAt >= RECEIPT_IDLE_COMPLETE_MS) {
                        completeBuilder(builder)
                        builder = ReceiptBuilder(socket.inetAddress?.hostAddress, socket.port)
                        hasData = false
                    }
                    continue
                }

                if (read == -1) break
                if (read == 0) continue

                hasData = true
                lastDataAt = System.currentTimeMillis()
                builder.addBytes(read)
                builder.addRawBytes(buffer, read)

                val events = parser.feed(buffer.copyOf(read))
                var completedByCut = false
                events.forEach { event ->
                    if (builder.apply(event)) {
                        completeBuilder(builder)
                        builder = ReceiptBuilder(socket.inetAddress?.hostAddress, socket.port)
                        completedByCut = true
                    }
                }

                if (!completedByCut) {
                    mutableEvents.tryEmit(PrinterServerEvent.ReceiptUpdated(builder.snapshot()))
                }
            }

            parser.flush().forEach { builder.apply(it) }
            if (hasData && builder.snapshot().blocks.isNotEmpty()) {
                completeBuilder(builder)
            }
        }
    }

    private fun completeBuilder(builder: ReceiptBuilder) {
        val completed = builder.complete()
        if (completed.blocks.isNotEmpty() || completed.status == ReceiptStatus.Completed) {
            mutableEvents.tryEmit(PrinterServerEvent.ReceiptCompleted(completed))
        }
    }

    private companion object {
        const val BUFFER_SIZE = 4096
        const val READ_TIMEOUT_MS = 500
        const val RECEIPT_IDLE_COMPLETE_MS = 2_000L
    }
}
