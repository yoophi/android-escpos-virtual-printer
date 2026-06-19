package com.example.escposvirtualprinter.features.printer.adapter.inbound.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.escposvirtualprinter.app.AppGraph
import com.example.escposvirtualprinter.features.printer.adapter.inbound.service.PrinterForegroundService
import com.example.escposvirtualprinter.features.printer.application.port.inbound.PrinterServerState
import com.example.escposvirtualprinter.features.printer.domain.model.Receipt
import com.example.escposvirtualprinter.features.printer.domain.model.ReceiptBlock
import com.example.escposvirtualprinter.features.printer.domain.model.TextStyle
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
    val preferences = remember { context.getSharedPreferences("printer_settings", android.content.Context.MODE_PRIVATE) }
    var portText by remember { mutableStateOf(state.port.toString()) }
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Main) }
    var serverPanelVisible by rememberSaveable {
        mutableStateOf(preferences.getBoolean("server_panel_visible", true))
    }
    var autoStartServer by rememberSaveable {
        mutableStateOf(preferences.getBoolean("auto_start_server", false))
    }
    var fullscreenMode by rememberSaveable {
        mutableStateOf(preferences.getBoolean("fullscreen_mode", false))
    }
    var charsPerLine by rememberSaveable {
        mutableIntStateOf(preferences.getInt("chars_per_line", 42).takeIf { it == 21 || it == 42 } ?: 42)
    }
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

    LaunchedEffect(charsPerLine) {
        preferences.edit().putInt("chars_per_line", charsPerLine).apply()
    }

    LaunchedEffect(autoStartServer) {
        preferences.edit().putBoolean("auto_start_server", autoStartServer).apply()
    }

    LaunchedEffect(fullscreenMode) {
        preferences.edit().putBoolean("fullscreen_mode", fullscreenMode).apply()
        context.findActivity()?.let { activity ->
            applyFullscreenMode(activity, fullscreenMode)
        }
    }

    LaunchedEffect(serverPanelVisible) {
        preferences.edit().putBoolean("server_panel_visible", serverPanelVisible).apply()
    }

    LaunchedEffect(Unit) {
        if (autoStartServer && !state.running) {
            val port = portText.toIntOrNull()?.coerceIn(1, 65535) ?: 9100
            ContextCompat.startForegroundService(context, PrinterForegroundService.startIntent(context, port))
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF7F4ED),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(if (state.running) Color(0xFF1F7A5A) else Color(0xFFD6D0C3)),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 0.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (currentScreen) {
                    AppScreen.Main -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                        ) {
                            ReceiptList(
                                receipts = state.receipts,
                                charsPerLine = charsPerLine,
                                onDeleteReceipt = { receiptId ->
                                    scope.launch { AppGraph.printerUseCase.deleteReceipt(receiptId) }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (serverPanelVisible) {
                                ServerPanel(
                                    state = state,
                                    tcpAddresses = tcpAddresses,
                                    portText = portText,
                                    charsPerLine = charsPerLine,
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
                                    onOpenSettings = {
                                        currentScreen = AppScreen.Settings
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .fillMaxWidth()
                                        .padding(end = 68.dp),
                                )
                            }
                            Column(
                                modifier = Modifier.align(Alignment.TopEnd),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.End,
                            ) {
                                ServerPanelToggleButton(
                                    visible = serverPanelVisible,
                                    running = state.running,
                                    onToggle = { serverPanelVisible = !serverPanelVisible },
                                )
                                FullscreenToggleButton(
                                    fullscreen = fullscreenMode,
                                    onToggle = { fullscreenMode = !fullscreenMode },
                                )
                            }
                        }
                    }
                    AppScreen.Settings -> {
                        SettingsScreen(
                            charsPerLine = charsPerLine,
                            autoStartServer = autoStartServer,
                            fullscreenMode = fullscreenMode,
                            onCharsPerLineChange = { charsPerLine = it },
                            onAutoStartServerChange = { autoStartServer = it },
                            onFullscreenModeChange = { fullscreenMode = it },
                            onBack = { currentScreen = AppScreen.Main },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

private enum class AppScreen {
    Main,
    Settings,
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

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun applyFullscreenMode(activity: Activity, fullscreen: Boolean) {
    WindowCompat.setDecorFitsSystemWindows(activity.window, !fullscreen)
    val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    if (fullscreen) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
private fun ServerPanel(
    state: PrinterServerState,
    tcpAddresses: List<String>,
    portText: String,
    charsPerLine: Int,
    modifier: Modifier = Modifier,
    onPortChange: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF7)),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.border(1.dp, Color(0xFFD6D0C3), RoundedCornerShape(8.dp)),
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ESC/POS",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = Color(0xFF1E2526),
                )
                Text(
                    text = if (state.running) "ON" else "OFF",
                    color = if (state.running) Color.White else Color(0xFFA33D2F),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(
                            if (state.running) Color(0xFF1F7A5A) else Color(0xFFFFFDF7),
                            RoundedCornerShape(4.dp),
                        )
                        .border(1.dp, if (state.running) Color(0xFF1F7A5A) else Color(0xFFA33D2F), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            Text(
                text = tcpAddresses.firstOrNull() ?: "tcp://no-ip:${state.port}",
                color = if (state.running) Color(0xFF1F7A5A) else Color(0xFF697070),
                fontSize = 14.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = "clients ${state.clientCount}",
                color = Color(0xFF697070),
                fontSize = 13.sp,
            )

            Text(
                text = "${state.receipts.size} receipts",
                color = Color(0xFF245F8F),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = "$charsPerLine chars/line",
                color = Color(0xFF697070),
                fontSize = 13.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = portText,
                    onValueChange = onPortChange,
                    label = { Text("TCP port") },
                    singleLine = true,
                    enabled = !state.running,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(104.dp),
                )
                if (state.running) {
                    OutlinedButton(onClick = onStop) { Text("Stop") }
                } else {
                    Button(onClick = onStart) { Text("Start") }
                }
                OutlinedButton(onClick = onClear) { Text("Clear") }
                OutlinedButton(onClick = onOpenSettings) { Text("Settings") }
            }

            state.lastError?.let {
                    Text(text = it, color = Color(0xFFA33D2F), fontSize = 13.sp)
                }
            }
    }
}

@Composable
private fun ServerPanelToggleButton(
    visible: Boolean,
    running: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onToggle,
        containerColor = if (running) Color(0xFF1F7A5A) else Color(0xFFFFFDF7),
        contentColor = if (running) Color.White else Color(0xFFA33D2F),
        modifier = modifier,
    ) {
        Text(
            text = if (visible) "Hide" else "TCP",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FullscreenToggleButton(
    fullscreen: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onToggle,
        containerColor = if (fullscreen) Color(0xFF245F8F) else Color(0xFFFFFDF7),
        contentColor = if (fullscreen) Color.White else Color(0xFF245F8F),
        modifier = modifier,
    ) {
        Text(
            text = if (fullscreen) "Exit" else "Full",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ReceiptList(
    receipts: List<Receipt>,
    charsPerLine: Int,
    onDeleteReceipt: (String) -> Unit,
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

    val listState = rememberLazyListState()
    LaunchedEffect(receipts.firstOrNull()?.id) {
        listState.animateScrollToItem(0)
    }

    LazyRow(
        modifier = modifier.fillMaxSize(),
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(receipts, key = { it.id }) { receipt ->
            ReceiptCard(
                receipt = receipt,
                charsPerLine = charsPerLine,
                onDelete = { onDeleteReceipt(receipt.id) },
                modifier = Modifier.fillParentMaxHeight(),
            )
        }
    }
}

@Composable
private fun ReceiptCard(
    receipt: Receipt,
    charsPerLine: Int,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val offsetY = remember(receipt.id) { Animatable(0f) }
    val alpha = remember(receipt.id) { Animatable(1f) }
    var deleting by remember(receipt.id) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .width(receiptPaperWidth(charsPerLine))
            .fillMaxHeight()
            .graphicsLayer {
                translationY = offsetY.value
                this.alpha = alpha.value
            }
            .swipeDownToDelete(
                enabled = !deleting,
                onSwipeDown = {
                    deleting = true
                    scope.launch {
                        offsetY.animateTo(
                            targetValue = 420f,
                            animationSpec = tween(durationMillis = 220),
                        )
                        alpha.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 120),
                        )
                        onDelete()
                    }
                },
            )
            .background(Color.White),
    ) {
        ReceiptCardHeader(receipt)
        Spacer(modifier = Modifier.height(8.dp))
        ReceiptPreview(
            receipt = receipt,
            charsPerLine = charsPerLine,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        if (receipt.warnings.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            ReceiptDebugPanel(receipt)
        }
    }
}

private fun Modifier.swipeDownToDelete(
    enabled: Boolean,
    onSwipeDown: () -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(onSwipeDown) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pointerId = down.id
            val startY = down.position.y
            val startTime = down.uptimeMillis
            var lastY = startY
            var lastTime = startTime

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                lastY = change.position.y
                lastTime = change.uptimeMillis
                if (!change.pressed) {
                    val distanceY = lastY - startY
                    val elapsedMs = (lastTime - startTime).coerceAtLeast(1L)
                    val velocityY = distanceY / elapsedMs
                    val distanceThreshold = (size.height * 0.16f).coerceAtMost(220f)
                    if (distanceY > distanceThreshold && velocityY > 0.9f) {
                        onSwipeDown()
                    }
                    break
                }
            }
        }
    }
}

@Composable
private fun ReceiptDebugPanel(receipt: Receipt) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(136.dp)
            .background(Color(0xFFFFF4ED), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFFE0A08A), RoundedCornerShape(6.dp))
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "${receipt.warnings.size} parser warning(s) · raw ${receipt.rawBytes.size}/${receipt.byteCount} bytes",
            color = Color(0xFFA33D2F),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        receipt.warnings.take(8).forEach { warning ->
            Text(
                text = "@${warning.offset}: ${warning.message}",
                color = Color(0xFF1E2526),
                fontSize = 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
            Text(
                text = hexWindow(receipt.rawBytes, warning.offset),
                color = Color(0xFF697070),
                fontSize = 10.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        }
        if (receipt.warnings.size > 8) {
            Text(
                text = "... ${receipt.warnings.size - 8} more warning(s)",
                color = Color(0xFFA33D2F),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun ReceiptCardHeader(receipt: Receipt) {
    val metadataColor = Color(0xFF697070)
    val metadataFontSize = 12.sp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 5.dp, top = 5.dp, end = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = receipt.createdAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            color = metadataColor,
            fontSize = metadataFontSize,
        )
        Text(
            text = "${listOfNotNull(receipt.sourceHost, receipt.sourcePort?.toString()).joinToString(":")}  ${receipt.byteCount} bytes",
            color = metadataColor,
            fontSize = metadataFontSize,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ReceiptPreview(
    receipt: Receipt,
    charsPerLine: Int,
    modifier: Modifier = Modifier,
) {
    val cellWidth = 10.dp
    val baseFontSize = 14.sp
    val horizontalPadding = 14.dp
    val verticalPadding = 18.dp
    val canvasLines = remember(receipt, charsPerLine) {
        buildCanvasReceiptLines(receipt, charsPerLine)
    }
    val receiptHeight = remember(canvasLines) {
        canvasLines.sumOf { line -> receiptItemHeightDp(line).value.toDouble() }.dp + verticalPadding * 2
    }
    BoxWithConstraints(
        modifier = modifier
            .width(receiptPaperWidth(charsPerLine)),
    ) {
        val canvasHeight = if (receiptHeight > maxHeight) receiptHeight else maxHeight
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .verticalScroll(rememberScrollState()),
        ) {
            Canvas(
                modifier = Modifier
                    .width(receiptPaperWidth(charsPerLine))
                    .height(canvasHeight)
                    .background(Color.White),
            ) {
                val canvas = drawContext.canvas.nativeCanvas
                val cellWidthPx = cellWidth.toPx()
                val dotSizePx = 2.dp.toPx()
                val leftPx = horizontalPadding.toPx()
                val topPx = verticalPadding.toPx()
                val textColor = android.graphics.Color.rgb(30, 37, 38)
                val eventColor = android.graphics.Color.rgb(105, 112, 112)
                var y = topPx

                canvasLines.forEach { line ->
                    val lineHeight = receiptItemHeightDp(line).toPx()
                    line.image?.let { image ->
                        val imageWidthPx = image.widthDots * dotSizePx * image.widthScale.coerceIn(1, 2)
                        val contentWidthPx = charsPerLine * cellWidthPx
                        val imageLeftPx = when (image.align) {
                            com.example.escposvirtualprinter.features.printer.domain.model.TextAlign.Left -> leftPx
                            com.example.escposvirtualprinter.features.printer.domain.model.TextAlign.Center -> leftPx + ((contentWidthPx - imageWidthPx) / 2f).coerceAtLeast(0f)
                            com.example.escposvirtualprinter.features.printer.domain.model.TextAlign.Right -> leftPx + (contentWidthPx - imageWidthPx).coerceAtLeast(0f)
                        }
                        val imageTopPx = y + 6.dp.toPx()
                        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = textColor
                            style = Paint.Style.FILL
                        }
                        drawRasterImage(canvas, image, imageLeftPx, imageTopPx, dotSizePx, imagePaint)
                        y += lineHeight
                        return@forEach
                    }

                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (line.event) eventColor else textColor
                        textSize = 14.sp.toPx() * line.style.heightScale.coerceIn(1, 3)
                        typeface = Typeface.create(Typeface.MONOSPACE, if (line.style.bold) Typeface.BOLD else Typeface.NORMAL)
                        isUnderlineText = line.style.underline
                    }
                    val baseline = y + lineHeight * 0.72f

                    if (line.separator) {
                        val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.rgb(214, 208, 195)
                            strokeWidth = 1.dp.toPx()
                        }
                        canvas.drawLine(leftPx, y + lineHeight / 2f, size.width - leftPx, y + lineHeight / 2f, separatorPaint)
                        y += lineHeight
                        return@forEach
                    }

                    val lineColumns = displayColumns(line.text) * line.style.widthScale.coerceIn(1, 3)
                    val leadingColumns = when (line.align) {
                        com.example.escposvirtualprinter.features.printer.domain.model.TextAlign.Left -> 0
                        com.example.escposvirtualprinter.features.printer.domain.model.TextAlign.Center -> ((charsPerLine - lineColumns) / 2).coerceAtLeast(0)
                        com.example.escposvirtualprinter.features.printer.domain.model.TextAlign.Right -> (charsPerLine - lineColumns).coerceAtLeast(0)
                    }
                    displayTokens(line.text).forEach { token ->
                        val tokenX = leftPx + (leadingColumns + token.startColumns * line.style.widthScale.coerceIn(1, 3)) * cellWidthPx
                        val tokenWidth = token.columns * cellWidthPx * line.style.widthScale.coerceIn(1, 3)
                        val measuredWidth = paint.measureText(token.text).coerceAtLeast(1f)
                        val originalScaleX = paint.textScaleX
                        paint.textScaleX = originalScaleX * (tokenWidth / measuredWidth)
                        canvas.drawText(token.text, tokenX, baseline, paint)
                        paint.textScaleX = originalScaleX
                    }
                    y += lineHeight
                }
            }
        }
    }
}

private fun receiptPaperWidth(charsPerLine: Int): Dp {
    return when (charsPerLine) {
        21 -> 238.dp
        else -> 450.dp
    }
}

private data class CanvasReceiptLine(
    val text: String,
    val align: com.example.escposvirtualprinter.features.printer.domain.model.TextAlign,
    val style: TextStyle,
    val image: ReceiptBlock.RasterImage? = null,
    val event: Boolean = false,
    val separator: Boolean = false,
)

private data class DisplayRun(
    val text: String,
    val startColumns: Int,
    val columns: Int,
)

private fun displayTokens(text: String): List<DisplayRun> {
    val tokens = mutableListOf<DisplayRun>()
    val current = StringBuilder()
    var currentStartColumns = 0
    var currentColumns = 0
    var cursorColumns = 0

    codePointStrings(text).forEach { char ->
        val charColumns = displayColumns(char)
        if (char.isBlank()) {
            if (current.isNotEmpty()) {
                tokens += DisplayRun(current.toString(), currentStartColumns, currentColumns)
                current.clear()
                currentColumns = 0
            }
            cursorColumns += charColumns
        } else {
            if (current.isEmpty()) {
                currentStartColumns = cursorColumns
            }
            current.append(char)
            currentColumns += charColumns
            cursorColumns += charColumns
        }
    }

    if (current.isNotEmpty()) {
        tokens += DisplayRun(current.toString(), currentStartColumns, currentColumns)
    }
    return tokens
}

private fun buildCanvasReceiptLines(receipt: Receipt, charsPerLine: Int): List<CanvasReceiptLine> {
    val lines = mutableListOf<CanvasReceiptLine>()
    receipt.blocks.forEach { block ->
        when (block) {
            is ReceiptBlock.TextLine -> {
                val lineText = block.segments.joinToString(separator = "") { it.text }.ifEmpty { " " }
                val style = block.segments.lastOrNull()?.style ?: TextStyle()
                splitByDisplayColumns(lineText, charsPerLine, style.widthScale.coerceIn(1, 3)).forEach { visualLine ->
                    lines += CanvasReceiptLine(visualLine, block.align, style)
                }
            }
            is ReceiptBlock.RasterImage -> {
                lines += CanvasReceiptLine(
                    text = "",
                    align = block.align,
                    style = TextStyle(),
                    image = block,
                )
            }
            is ReceiptBlock.DeviceEvent -> {
                lines += CanvasReceiptLine("", com.example.escposvirtualprinter.features.printer.domain.model.TextAlign.Left, TextStyle(), separator = true)
                lines += CanvasReceiptLine(
                    text = block.payload ?: block.label,
                    align = com.example.escposvirtualprinter.features.printer.domain.model.TextAlign.Center,
                    style = TextStyle(),
                    event = true,
                )
            }
        }
    }
    return lines.ifEmpty {
        listOf(CanvasReceiptLine(" ", com.example.escposvirtualprinter.features.printer.domain.model.TextAlign.Left, TextStyle()))
    }
}

private fun receiptItemHeightDp(line: CanvasReceiptLine): Dp {
    line.image?.let { image ->
        return image.heightDots.dp * 2 * image.heightScale.coerceIn(1, 2) + 12.dp
    }
    return lineHeightDp(line.style)
}

private fun lineHeightDp(style: TextStyle): Dp {
    return 30.dp * style.heightScale.coerceIn(1, 3)
}

private fun drawRasterImage(
    canvas: android.graphics.Canvas,
    image: ReceiptBlock.RasterImage,
    leftPx: Float,
    topPx: Float,
    dotSizePx: Float,
    paint: Paint,
) {
    val widthScale = image.widthScale.coerceIn(1, 2)
    val heightScale = image.heightScale.coerceIn(1, 2)
    val scaledDotWidth = dotSizePx * widthScale
    val scaledDotHeight = dotSizePx * heightScale

    for (y in 0 until image.heightDots) {
        val rowOffset = y * image.widthBytes
        for (xByte in 0 until image.widthBytes) {
            val value = image.data[rowOffset + xByte].toInt() and 0xff
            for (bit in 0 until 8) {
                if (value and (0x80 shr bit) == 0) continue
                val x = xByte * 8 + bit
                val rectLeft = leftPx + x * scaledDotWidth
                val rectTop = topPx + y * scaledDotHeight
                canvas.drawRect(rectLeft, rectTop, rectLeft + scaledDotWidth, rectTop + scaledDotHeight, paint)
            }
        }
    }
}

private fun hexWindow(bytes: ByteArray, offset: Long, radius: Int = 24): String {
    if (bytes.isEmpty()) return "raw bytes not captured"
    val center = offset.coerceIn(0, (bytes.size - 1).toLong()).toInt()
    val start = (center - radius).coerceAtLeast(0)
    val endExclusive = (center + radius + 1).coerceAtMost(bytes.size)
    val hex = (start until endExclusive).joinToString(" ") { index ->
        val prefix = if (index == center) ">" else ""
        prefix + (bytes[index].toInt() and 0xff).toString(16).uppercase().padStart(2, '0')
    }
    val ascii = (start until endExclusive).joinToString("") { index ->
        val value = bytes[index].toInt() and 0xff
        if (value in 0x20..0x7e) value.toChar().toString() else "."
    }
    return "%06X: %s\nascii: %s".format(start, hex, ascii)
}

private fun splitByDisplayColumns(text: String, maxColumns: Int, widthScale: Int): List<String> {
    if (text.isEmpty()) return listOf(" ")
    val lines = mutableListOf<String>()
    val current = StringBuilder()
    var currentColumns = 0

    codePointStrings(text).forEach { char ->
        val width = displayColumns(char) * widthScale
        if (current.isNotEmpty() && currentColumns + width > maxColumns) {
            lines += current.toString()
            current.clear()
            currentColumns = 0
        }
        current.append(char)
        currentColumns += width
    }

    if (current.isNotEmpty()) {
        lines += current.toString()
    }
    return lines.ifEmpty { listOf(" ") }
}

private fun displayColumns(text: String): Int {
    var columns = 0
    codePointStrings(text).forEach { char ->
        val codePoint = char.codePointAt(0)
        columns += if (isWideCodePoint(codePoint)) 2 else 1
    }
    return columns
}

private fun codePointStrings(text: String): List<String> {
    val chars = mutableListOf<String>()
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        chars += String(Character.toChars(codePoint))
        index += Character.charCount(codePoint)
    }
    return chars
}

private fun isWideCodePoint(codePoint: Int): Boolean {
    return codePoint in 0x1100..0x11ff ||
        codePoint in 0x2190..0x21ff ||
        codePoint in 0x2500..0x257f ||
        codePoint in 0x25a0..0x25ff ||
        codePoint in 0x2e80..0xa4cf ||
        codePoint in 0xac00..0xd7a3 ||
        codePoint in 0xf900..0xfaff ||
        codePoint in 0xfe10..0xfe19 ||
        codePoint in 0xfe30..0xfe6f ||
        codePoint in 0xff00..0xff60 ||
        codePoint in 0xffe0..0xffe6
}

@Composable
private fun SettingsScreen(
    charsPerLine: Int,
    autoStartServer: Boolean,
    fullscreenMode: Boolean,
    onCharsPerLineChange: (Int) -> Unit,
    onAutoStartServerChange: (Boolean) -> Unit,
    onFullscreenModeChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF7)),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFD6D0C3), RoundedCornerShape(8.dp)),
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Settings",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = Color(0xFF1E2526),
                )
                Text(
                    text = "$charsPerLine chars/line",
                    color = Color(0xFF697070),
                    fontSize = 13.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
                OutlinedButton(onClick = onBack) { Text("Back") }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF7)),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFD6D0C3), RoundedCornerShape(8.dp)),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Receipt line width",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E2526),
                )
                Text(
                    text = "Choose how many monospace characters fit on one displayed receipt line.",
                    color = Color(0xFF697070),
                    fontSize = 13.sp,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LineWidthOption(
                        value = 21,
                        selected = charsPerLine == 21,
                        onSelect = { onCharsPerLineChange(21) },
                    )
                    LineWidthOption(
                        value = 42,
                        selected = charsPerLine == 42,
                        onSelect = { onCharsPerLineChange(42) },
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF7)),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFD6D0C3), RoundedCornerShape(8.dp)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Auto start TCP server",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E2526),
                    )
                    Text(
                        text = "Start listening automatically when the app opens.",
                        color = Color(0xFF697070),
                        fontSize = 13.sp,
                    )
                }
                Switch(
                    checked = autoStartServer,
                    onCheckedChange = onAutoStartServerChange,
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF7)),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFD6D0C3), RoundedCornerShape(8.dp)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Fullscreen mode",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E2526),
                    )
                    Text(
                        text = "Hide Android system bars and use the full display.",
                        color = Color(0xFF697070),
                        fontSize = 13.sp,
                    )
                }
                Switch(
                    checked = fullscreenMode,
                    onCheckedChange = onFullscreenModeChange,
                )
            }
        }
    }
}

@Composable
private fun LineWidthOption(
    value: Int,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    if (selected) {
        Button(onClick = onSelect) { Text("$value chars") }
    } else {
        OutlinedButton(onClick = onSelect) { Text("$value chars") }
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
