package com.example

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.with
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.CameraModeScreen
import com.example.ui.MainMenuScreen
import com.example.ui.RemoteModeScreen
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {

    // Dynamic Permission request launcher
    private var onPermissionGrantedCallback: (() -> Unit)? = null

    val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            onPermissionGrantedCallback?.invoke()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                // Instantiates the core ViewModel holding state flows
                val viewModel: ShutterViewModel = viewModel()
                val currentScreen by viewModel.currentScreen.collectAsState()

                // State tracker for our dynamic permissions barrier
                var pendingScreenTransition by remember { mutableStateOf<AppScreen?>(null) }
                var showPermissionDialog by remember { mutableStateOf(false) }

                // Check and trigger permissions on mode transition
                val requestPermissionsForMode = { intendedScreen: AppScreen ->
                    val required = getRequiredPermissions(this)
                    val missing = required.filter {
                        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                    }

                    if (missing.isEmpty()) {
                        viewModel.navigateTo(intendedScreen)
                    } else {
                        pendingScreenTransition = intendedScreen
                        showPermissionDialog = true
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // High Fidelity Navigation Content Transitions with stable Crossfade
                        androidx.compose.animation.Crossfade(
                            targetState = currentScreen,
                            label = "screen_router"
                        ) { screen ->
                            when (screen) {
                                AppScreen.MENU -> {
                                    MainMenuScreen(
                                        onNavigate = { target ->
                                            requestPermissionsForMode(target)
                                        }
                                    )
                                }
                                AppScreen.CAMERA -> {
                                    CameraModeScreen(
                                        viewModel = viewModel,
                                        onBack = {
                                            viewModel.navigateTo(AppScreen.MENU)
                                        }
                                    )
                                }
                                AppScreen.REMOTE -> {
                                    RemoteModeScreen(
                                        viewModel = viewModel,
                                        onBack = {
                                            viewModel.navigateTo(AppScreen.MENU)
                                        }
                                    )
                                }
                            }
                        }

                        // --- Elegantly Structured Custom Permission Barrier Dialog ---
                        if (showPermissionDialog) {
                            PermissionBarrierDialog(
                                isCameraTarget = pendingScreenTransition == AppScreen.CAMERA,
                                onDismiss = { showPermissionDialog = false },
                                onRequest = {
                                    val required = getRequiredPermissions(this@MainActivity)
                                    onPermissionGrantedCallback = {
                                        showPermissionDialog = false
                                        pendingScreenTransition?.let { viewModel.navigateTo(it) }
                                    }
                                    permissionLauncher.launch(required.toTypedArray())
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun getRequiredPermissions(context: Context): List<String> {
        val list = mutableListOf<String>()
        list.add(android.Manifest.permission.CAMERA)
        list.add(android.Manifest.permission.VIBRATE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(android.Manifest.permission.BLUETOOTH_SCAN)
            list.add(android.Manifest.permission.BLUETOOTH_CONNECT)
            list.add(android.Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            list.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
            list.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        return list
    }
}

@Composable
fun PermissionBarrierDialog(
    isCameraTarget: Boolean,
    onDismiss: () -> Unit,
    onRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("grant_permissions_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Conceder Permisos", fontWeight = FontWeight.Bold, color = PureWhite)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().testTag("cancel_permissions_btn")
            ) {
                Text("Cancelar", color = SkyLight, fontWeight = FontWeight.Medium)
            }
        },
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = SkyBlue,
                modifier = Modifier.size(40.dp)
            )
        },
        title = {
            Text(
                text = "Permisos Requeridos",
                color = OffWhite,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Para que la app conecte ambos teléfonos y tome las fotos automáticamente en tu tripié, necesitamos que apruebes los siguientes accesos:",
                    color = SkyLight,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Requirement 1: Camera
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NavyDark, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, tint = SkyBlue, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Acceso a Cámara", fontWeight = FontWeight.Bold, color = OffWhite, fontSize = 13.sp)
                        Text("Muestra la vista en vivo y captura fotos de productos.", color = SkyLight, fontSize = 11.sp)
                    }
                }

                // Requirement 2: Bluetooth
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NavyDark, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.BluetoothConnected, contentDescription = null, tint = SkyBlue, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Acceso a Bluetooth de Proximidad", fontWeight = FontWeight.Bold, color = OffWhite, fontSize = 13.sp)
                        Text("Descubre o anuncia la señal de gatillo remoto sin cables.", color = SkyLight, fontSize = 11.sp)
                    }
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = NavyMedium, // Sleek dark container
        modifier = Modifier.padding(16.dp)
    )
}
