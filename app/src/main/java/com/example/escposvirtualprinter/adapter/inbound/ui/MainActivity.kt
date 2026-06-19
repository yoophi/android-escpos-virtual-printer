package com.example.escposvirtualprinter.adapter.inbound.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.escposvirtualprinter.AppGraph
import com.example.escposvirtualprinter.adapter.inbound.service.PrinterForegroundService
import com.example.escposvirtualprinter.application.port.inbound.PrinterServerState
import com.example.escposvirtualprinter.domain.model.Receipt
import com.example.escposvirtualprinter.domain.model.ReceiptBlock
import com.example.escposvirtualprinter.domain.model.ReceiptStatus
import java.net.NetworkInterface
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.init(this)
        setContent {
            EscposVirtualPrinterTheme {
                VirtualPrinterApp()
            }
        }
    }
}

@Composable
private fun VirtualPrinterApp() {
    val context = LocalContext.current
    val state by AppGraph.printerUseCase.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var portText by remember { mutableStateOf(state.port.toString()) }
    val tcpAddresses = remember(state.port, state.running) { discoverTcpAddresses(state.port) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF7F4ED),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "ESC/POS Virtual Printer",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = Color(0xFF1E2526),
            )
            ServerPanel(
                state = state,
                tcpAddresses = tcpAddresses,
                portText = portText,
                onPortChange = { portText = it.filter(Char::isDigit).take(5) },
                onStart = {
                    val port = portText.toIntOrNull()?.coerceIn(1, 65535) ?: 9100
                    ContextCompat.startForegroundService(context, PrinterForegroundService.startIntent(context, port))
                },
                onStop = {
                    context.startService(PrinterForegroundService.stopIntent(context))
                },
                onClear = {
                    scope.launchClearReceipts()
                },
            )
            ReceiptList(
                receipts = state.receipts,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun kotlinx.coroutines.CoroutineScope.launchClearReceipts() {
    launch {
        AppGraph.printerUseCase.clearReceipts()
    }
}

private fun discoverTcpAddresses(port: Int): List<String> {
    return runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { networkInterface ->
                networkInterface.inetAddresses.asSequence()
                    .filter { address ->
                        !address.isLoopbackAddress &&
                            address.hostAddress?.contains(':') == false &&
                            address.isSiteLocalAddress
                    }
                    .map { address -> "tcp://${address.hostAddress}:$port" }
            }
            .distinct()
            .sorted()
            .toList()
    }.getOrDefault(emptyList())
}

@Composable
private fun ServerPanel(
    state: PrinterServerState,
    tcpAddresses: List<String>,
    portText: String,
    onPortChange: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF7)),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.border(1.dp, Color(0xFFD6D0C3), RoundedCornerShape(8.dp)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = if (state.running) "Listening" else "Stopped",
                        fontWeight = FontWeight.Bold,
                        color = if (state.running) Color(0xFF1F7A5A) else Color(0xFFA33D2F),
                    )
                    Text(
                        text = "Port ${state.port} · clients ${state.clientCount}",
                        color = Color(0xFF697070),
                        fontSize = 13.sp,
                    )
                }
                Text(
                    text = "${state.receipts.size} receipts",
                    color = Color(0xFF245F8F),
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEFE8D9), RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0xFFD6D0C3), RoundedCornerShape(6.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "TCP connection",
                    color = Color(0xFF1E2526),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
                if (tcpAddresses.isEmpty()) {
                    Text(
                        text = "No external IPv4 address found · port ${state.port}",
                        color = Color(0xFF697070),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                } else {
                    tcpAddresses.forEach { endpoint ->
                        Text(
                            text = endpoint,
                            color = if (state.running) Color(0xFF1F7A5A) else Color(0xFF697070),
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = portText,
                    onValueChange = onPortChange,
                    label = { Text("TCP port") },
                    singleLine = true,
                    enabled = !state.running,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(132.dp),
                )
                if (state.running) {
                    OutlinedButton(onClick = onStop) { Text("Stop") }
                } else {
                    Button(onClick = onStart) { Text("Start") }
                }
                OutlinedButton(onClick = onClear) { Text("Clear") }
            }

            state.lastError?.let {
                Text(text = it, color = Color(0xFFA33D2F), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ReceiptList(
    receipts: List<Receipt>,
    modifier: Modifier = Modifier,
) {
    if (receipts.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(Color(0xFFEFE8D9), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFD6D0C3), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Start the server and send ESC/POS data to this device.",
                color = Color(0xFF697070),
                modifier = Modifier.padding(28.dp),
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(receipts, key = { it.id }) { receipt ->
            ReceiptCard(receipt)
        }
    }
}

@Composable
private fun ReceiptCard(receipt: Receipt) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF7)),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.border(1.dp, Color(0xFFD6D0C3), RoundedCornerShape(8.dp)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        text = receipt.createdAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E2526),
                    )
                    Text(
                        text = listOfNotNull(receipt.sourceHost, receipt.sourcePort?.toString()).joinToString(":"),
                        color = Color(0xFF697070),
                        fontSize = 12.sp,
                    )
                }
                Text(
                    text = "${receipt.byteCount} bytes · ${receipt.status.label()}",
                    color = if (receipt.status == ReceiptStatus.Completed) Color(0xFF1F7A5A) else Color(0xFFC78B24),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            ReceiptPreview(receipt)
            if (receipt.warnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "${receipt.warnings.size} parser warning(s)",
                    color = Color(0xFFA33D2F),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun ReceiptPreview(receipt: Receipt) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .border(1.dp, Color(0xFFE5E0D6))
            .padding(horizontal = 14.dp, vertical = 18.dp),
    ) {
        receipt.blocks.forEach { block ->
            when (block) {
                is ReceiptBlock.TextLine -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = when (block.align) {
                            com.example.escposvirtualprinter.domain.model.TextAlign.Left -> Arrangement.Start
                            com.example.escposvirtualprinter.domain.model.TextAlign.Center -> Arrangement.Center
                            com.example.escposvirtualprinter.domain.model.TextAlign.Right -> Arrangement.End
                        },
                    ) {
                        block.segments.forEach { segment ->
                            Text(
                                text = segment.text.ifEmpty { " " },
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (segment.style.bold) FontWeight.Black else FontWeight.Normal,
                                textDecoration = if (segment.style.underline) androidx.compose.ui.text.style.TextDecoration.Underline else null,
                                fontSize = (14 * segment.style.heightScale.coerceIn(1, 3)).sp,
                                color = Color(0xFF1E2526),
                            )
                        }
                    }
                }
                is ReceiptBlock.DeviceEvent -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = DividerDefaults.Thickness,
                        color = Color(0xFFD6D0C3),
                    )
                    Text(
                        text = block.payload ?: block.label,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = Color(0xFF697070),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

private fun ReceiptStatus.label(): String {
    return when (this) {
        ReceiptStatus.Receiving -> "receiving"
        ReceiptStatus.Completed -> "completed"
        ReceiptStatus.Error -> "error"
    }
}

@Composable
private fun EscposVirtualPrinterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1F7A5A),
            secondary = Color(0xFF245F8F),
            background = Color(0xFFF7F4ED),
            surface = Color(0xFFFFFDF7),
            error = Color(0xFFA33D2F),
        ),
        content = content,
    )
}
