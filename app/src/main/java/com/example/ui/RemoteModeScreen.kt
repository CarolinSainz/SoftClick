package com.example.ui

import android.bluetooth.BluetoothDevice
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.AppScreen
import com.example.CaptureConfig
import com.example.ShutterViewModel
import com.example.bluetooth.BTDeviceDisplay
import com.example.bluetooth.RemoteConnectionState
import com.example.ui.theme.*

@Composable
fun RemoteModeScreen(
    viewModel: ShutterViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config by viewModel.captureConfig.collectAsState()
    val isCapturing by viewModel.isCapturing.collectAsState()
    val shotIndex by viewModel.activeShotIndex.collectAsState()
    val totalShots by viewModel.totalShotsToTake.collectAsState()

    val btState by viewModel.btController.state.collectAsState()
    val connState = btState.connectionState
    val connectedName = btState.connectedDeviceName ?: "Cámara"
    val pairedDevices = btState.pairedDevices
    val discoveredDevices = btState.discoveredDevices
    val isScanning = btState.isScanning

    // Initiate Bluetooth updates when screen is active
    LaunchedEffect(Unit) {
        viewModel.btController.loadPairedDevices()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NavyDark) // High contrast dark slate backdrop #0D1B2A
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // --- Elegant Screen Top Bar ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        viewModel.btController.stopAllConnections()
                        onBack()
                    },
                    modifier = Modifier
                        .background(NavyMedium, CircleShape) // #1B263B
                        .border(1.dp, PureWhite.copy(alpha = 0.08f), CircleShape)
                        .size(44.dp)
                        .testTag("remote_back_btn")
                ) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Atrás", tint = PureWhite)
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "Control Remoto",
                        color = OffWhite, // #E0E1DD Elegant Light Text
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "BlueShutter", // Design HTML spec title
                        color = SkyLight, // #778DA9 Cool steel blue
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- Real-time Connection State Banner ---
            ConnectionBanner(
                connState = connState,
                connectedName = connectedName,
                onDisconnect = { viewModel.btController.stopAllConnections() }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // --- Mode 1: Search & Connection Controller ---
            if (connState != RemoteConnectionState.CONNECTED && connState != RemoteConnectionState.CONNECTING) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Por favor conéctate a la cámara:",
                            color = OffWhite, // High-contrast text in dark theme
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Scan Control Button
                        TextButton(
                            onClick = { viewModel.btController.startScanning() },
                            colors = ButtonDefaults.textButtonColors(contentColor = SkyBlue)
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = SkyBlue
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Buscando...", fontSize = 13.sp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Buscar Dispositivos", fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Section: Paired Devices (Extremely Robust)
                        item {
                            Text(
                                "Dispositivos Vinculados (Sistema)",
                                color = SkyLight, // Clean steel grey color (#778DA9)
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        if (pairedDevices.isEmpty()) {
                            item {
                                EmptyStateCard(text = "Vincula la cámara en los ajustes Bluetooth de tu teléfono para una conexión rápida de 1-click.")
                            }
                        } else {
                            items(pairedDevices) { btd ->
                                DeviceListItem(
                                    deviceName = btd.name,
                                    deviceAddress = btd.address,
                                    onClick = { 
                                        viewModel.btController.connectToDevice(btd.device)
                                    }
                                )
                            }
                        }

                        // Section: Discovered Devices
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Otros Dispositivos Cercanos Detectados",
                                color = SkyLight, // Clean steel grey color (#778DA9)
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        if (discoveredDevices.isEmpty() && !isScanning) {
                            item {
                                EmptyStateCard(text = "Toca 'Buscar Dispositivos' para escanear teléfonos no vinculados.")
                            }
                        } else {
                            items(discoveredDevices) { btd ->
                                DeviceListItem(
                                    deviceName = btd.name,
                                    deviceAddress = btd.address,
                                    onClick = { 
                                        viewModel.btController.connectToDevice(btd.device)
                                    }
                                )
                            }
                        }
                    }
                }
            } else if (connState == RemoteConnectionState.CONNECTING) {
                // Connecting State display
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = SkyBlue,
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Conectando con la cámara...",
                        color = OffWhite, // Contrast text color
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Asegúrate de que el otro teléfono tenga abierto el 'Modo Cámara'.",
                        color = SkyLight, // Steel grey color
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp).padding(top = 6.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedButton(
                        onClick = { viewModel.btController.stopAllConnections() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed),
                        border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f))
                    ) {
                        Text("CANCELAR CONEXIÓN")
                    }
                }
            } else {
                // --- Mode 2: Camera Trigger Controls (Connected) ---
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Option A: Temporizador
                        SelectorCard(title = "Temporizador / Retardo") {
                            val timerOptions = listOf(
                                Pair(0, "Inmediato"),
                                Pair(3, "3 seg"),
                                Pair(5, "5 seg"),
                                Pair(10, "10 seg")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                timerOptions.forEach { opt ->
                                    val isSelected = config.timerSecs == opt.first
                                    PillSelector(
                                        label = opt.second,
                                        selected = isSelected,
                                        onClick = { viewModel.updateConfig(timer = opt.first) },
                                        modifier = Modifier.weight(1f).testTag("timer_opt_${opt.first}")
                                    )
                                }
                            }
                        }

                        // Option B: Número de disparos
                        SelectorCard(title = "Número de Disparos (Ráfaga)") {
                            val burstOptions = listOf(1, 3, 5, 10)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                burstOptions.forEach { count ->
                                    val isSelected = config.burstCount == count
                                    PillSelector(
                                        label = "$count ${if (count == 1) "foto" else "fotos"}",
                                        selected = isSelected,
                                        onClick = { viewModel.updateConfig(burst = count) },
                                        modifier = Modifier.weight(1f).testTag("burst_opt_$count")
                                    )
                                }
                            }
                        }

                        // Option C: Intervalo entre fotos
                        if (config.burstCount > 1) {
                            SelectorCard(title = "Intervalo entre Disparos") {
                                val intervalOptions = listOf(2, 5, 10)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    intervalOptions.forEach { secs ->
                                        val isSelected = config.intervalSecs == secs
                                        PillSelector(
                                            label = "$secs seg",
                                            selected = isSelected,
                                            onClick = { viewModel.updateConfig(interval = secs) },
                                            modifier = Modifier.weight(1f).testTag("interval_opt_$secs")
                                        )
                                    }
                                }
                                
                                // "Poder hacer disparos 5 a 10 disparos en 20 segundos" hint info
                                if (config.burstCount in 5..10) {
                                    val totalSecs = config.burstCount * config.intervalSecs
                                    Text(
                                        text = "⚡ Sesión recomendada: $totalShots disparos en aprox. ${config.burstCount * config.intervalSecs} segundos.",
                                        fontSize = 11.sp,
                                        color = SkyBlue,
                                        modifier = Modifier.padding(top = 8.dp),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // --- GIANT CAMERA SHUTTER SHIFT ---
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 20.dp)
                    ) {
                        if (isCapturing) {
                            // Cancel active shooting sessions
                            Button(
                                onClick = { viewModel.cancelCapture() },
                                colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .testTag("cancel_active_capture_btn")
                            ) {
                                Icon(imageVector = Icons.Default.Cancel, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("CANCELAR SESIÓN Activa (${shotIndex}/${totalShots})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        } else {
                            // High Fidelity Custom Shutter Button (Immersive UI: White solid interior, thick grey border, soft blur cyan halo)
                            Box(
                                modifier = Modifier
                                    .size(160.dp)
                                    .drawBehind {
                                        // Radiant Blue Halo backdrop glow
                                        drawCircle(
                                            color = SkyBlue.copy(alpha = 0.12f),
                                            radius = 80.dp.toPx()
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(140.dp)
                                        .border(4.dp, NavyPrimary, CircleShape) // #415A77 Mid Slate border
                                        .clip(CircleShape)
                                        .clickable { viewModel.initiateCapture() }
                                        .testTag("shutter_trigger_btn"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(110.dp)
                                            .background(PureWhite, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.CameraAlt,
                                                contentDescription = "Trigger",
                                                tint = NavyDark, // #0D1B2A dark text pairing with white background
                                                modifier = Modifier.size(28.dp)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "DISPARAR",
                                                color = NavyDark,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 0.5.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionBanner(
    connState: RemoteConnectionState,
    connectedName: String,
    onDisconnect: () -> Unit
) {
    val bgColor = when (connState) {
        RemoteConnectionState.CONNECTED -> SuccessGreen.copy(alpha = 0.1f)
        RemoteConnectionState.CONNECTING -> SkyBlue.copy(alpha = 0.1f)
        else -> AlertRed.copy(alpha = 0.08f)
    }
    val contentColor = when (connState) {
        RemoteConnectionState.CONNECTED -> SuccessGreen
        RemoteConnectionState.CONNECTING -> SkyBlue
        else -> AlertRed
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = NavyMedium), // Make the connection banner dark slate
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (connState) {
                        RemoteConnectionState.CONNECTED -> Icons.Default.BluetoothConnected
                        RemoteConnectionState.CONNECTING -> Icons.Default.BluetoothSearching
                        else -> Icons.Default.BluetoothDisabled
                    },
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = when (connState) {
                            RemoteConnectionState.CONNECTED -> "Cámara Conectada"
                            RemoteConnectionState.CONNECTING -> "Estableciendo conexión..."
                            else -> "Sin Conexión con Cámara"
                        },
                        color = OffWhite, // Contrast soft text colors
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = when (connState) {
                            RemoteConnectionState.CONNECTED -> connectedName
                            RemoteConnectionState.CONNECTING -> "Buscando canal RFCOMM..."
                            else -> "Teléfono en tripié no encontrado"
                        },
                        color = SkyLight, // Steel blue details
                        fontSize = 12.sp
                    )
                }
            }

            if (connState == RemoteConnectionState.CONNECTED) {
                Text(
                    text = "DESCONECTAR",
                    color = AlertRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onDisconnect() }
                        .padding(4.dp)
                        .testTag("disconnect_banner_btn")
                )
            }
        }
    }
}

@Composable
private fun SelectorCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NavyMedium), // #1B263B Dark Slate Card Base
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.05f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title.uppercase(),
                color = SkyLight, // #778DA9 Steel Grey label
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
private fun PillSelector(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) SkyBlue else NavyPrimary.copy(alpha = 0.3f)
    val textCol = if (selected) PureWhite else OffWhite.copy(alpha = 0.8f)
    val borderCol = if (selected) SkyBlue else PureWhite.copy(alpha = 0.05f)

    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, borderCol, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textCol,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DeviceListItem(
    deviceName: String,
    deviceAddress: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NavyMedium),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.05f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(NavyDark, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.SettingsCell, contentDescription = null, tint = SkyBlue)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = deviceName, color = OffWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(text = deviceAddress, color = SkyLight, fontSize = 11.sp)
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = SkyLight,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun EmptyStateCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NavyMedium.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.03f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = SkyLight, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = text, color = SkyLight, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}
