package com.example.ui

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SettingsCell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.AppScreen
import com.example.ShutterViewModel
import com.example.bluetooth.RemoteConnectionState
import com.example.ui.theme.AlertRed
import com.example.ui.theme.NavyDark
import com.example.ui.theme.OffWhite
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SuccessGreen

@Composable
fun CameraModeScreen(
    viewModel: ShutterViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // 1. Keep the screen active (on) during photo session
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            viewModel.btController.stopTriggerServer()
        }
    }

    // Capture VM States via direct observers
    val lensFacing by viewModel.lensFacing.collectAsState()
    val countdown by viewModel.countdownValue.collectAsState()
    val isCapturing by viewModel.isCapturing.collectAsState()
    val shotIndex by viewModel.activeShotIndex.collectAsState()
    val totalShots by viewModel.totalShotsToTake.collectAsState()
    val uris by viewModel.capturedUris.collectAsState()

    val btState by viewModel.btController.state.collectAsState()
    val connState = btState.connectionState
    val serverDeviceName = btState.connectedDeviceName ?: "Control Remoto"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // --- Core Jetpack CameraX Viewfinder ---
        CameraPreviewView(
            lensFacing = lensFacing,
            shutterTriggerFlow = viewModel.shutterTriggerSignal,
            onPhotoCaptured = { uri ->
                viewModel.addCapturedPhoto(uri)
            },
            modifier = Modifier.fillMaxSize()
        )

        // --- Aesthetic Dynamic Gradient Overlay (Top & Bottom) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .align(Alignment.TopCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.BottomCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
        )

        // --- Top Utility Layer ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Exit button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .size(48.dp)
                        .testTag("camera_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Volver",
                        tint = PureWhite
                    )
                }

                // Bluetooth Connection Status Tag
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (connState) {
                            RemoteConnectionState.CONNECTED -> SuccessGreen.copy(alpha = 0.85f)
                            RemoteConnectionState.CONNECTING -> SkyBlue.copy(alpha = 0.85f)
                            else -> Color.Black.copy(alpha = 0.65f)
                        }
                    ),
                    shape = RoundedCornerShape(24.dp),
                    border = borderIndicatorForState(connState),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PulsingIndicator(active = connState != RemoteConnectionState.CONNECTED)
                        Text(
                            text = when (connState) {
                                RemoteConnectionState.CONNECTED -> "Vinculado con $serverDeviceName"
                                RemoteConnectionState.CONNECTING -> "Conectando..."
                                else -> "Esperando Control Remoto..."
                            },
                            color = PureWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Switch Camera Lens Button
                IconButton(
                    onClick = { viewModel.toggleLens() },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .size(48.dp)
                        .testTag("switch_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Cached,
                        contentDescription = "Cambiar Cámara",
                        tint = PureWhite
                    )
                }
            }
        }

        // --- Giant Countdown / Shutter Status Overlay ---
        AnimatedVisibility(
            visible = countdown != null || isCapturing,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(28.dp))
                    .padding(32.dp)
                    .width(220.dp)
            ) {
                if (countdown != null) {
                    // Big circular countdown number
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .border(4.dp, SkyBlue, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$countdown",
                            color = SkyBlue,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Foto en camino...",
                        color = PureWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                } else if (isCapturing) {
                    // Capture progress indicator
                    CircularProgressIndicator(
                        color = SkyBlue,
                        trackColor = Color.DarkGray,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Tomando Foto",
                        color = SkyBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "$shotIndex de $totalShots disparos",
                        color = OffWhite,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Cancel Button during shooting series
                OutlinedButton(
                    onClick = { viewModel.cancelCapture() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed),
                    border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("cancel_capture_button")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(text = "CANCELAR", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // --- Bottom Media Thumbnail Gallery & Quick Trigger Overlay ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Thumbnail Roll of Photos taken during this session
            AnimatedVisibility(
                visible = uris.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp).padding(bottom = 8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null, tint = SkyBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                "Carrete de la Sesión (${uris.size} fotos)",
                                color = PureWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            items(uris.reversed()) { uri ->
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Miniatura",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, PureWhite.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                )
                            }
                        }
                    }
                }
            }

            // Local manual trigger option (In case you want to test trigger locally)
            if (!isCapturing && connState != RemoteConnectionState.CONNECTED) {
                OutlinedButton(
                    onClick = { viewModel.initiateCapture() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite),
                    border = BorderStroke(2.dp, PureWhite),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("manual_capture_btn")
                ) {
                    Text("Probar Disparador Local", fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun PulsingIndicator(active: Boolean) {
    if (!active) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(PureWhite, CircleShape)
        )
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier
            .size(10.dp)
            .background(SkyBlue.copy(alpha = scale), CircleShape)
    )
}

@Composable
private fun borderIndicatorForState(state: RemoteConnectionState): BorderStroke? {
    return when (state) {
        RemoteConnectionState.CONNECTED -> BorderStroke(1.dp, SuccessGreen)
        RemoteConnectionState.CONNECTING -> BorderStroke(1.dp, SkyBlue)
        else -> BorderStroke(1.dp, PureWhite.copy(alpha = 0.2f))
    }
}
