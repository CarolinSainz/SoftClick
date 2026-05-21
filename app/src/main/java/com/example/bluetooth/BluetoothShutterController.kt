package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

private const val TAG = "BTShutterController"
private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
private const val APP_NAME = "ClickRemoto"

enum class RemoteConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

data class BTDeviceDisplay(
    val name: String,
    val address: String,
    val device: BluetoothDevice
)

data class BTState(
    val isSupported: Boolean = false,
    val isEnabled: Boolean = false,
    val hasPermissions: Boolean = false,
    val connectionState: RemoteConnectionState = RemoteConnectionState.DISCONNECTED,
    val connectedDeviceName: String? = null,
    val isServerRunning: Boolean = false,
    val pairedDevices: List<BTDeviceDisplay> = emptyList(),
    val discoveredDevices: List<BTDeviceDisplay> = emptyList(),
    val isScanning: Boolean = false
)

class BluetoothShutterController(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    
    private val _state = MutableStateFlow(BTState(isSupported = bluetoothAdapter != null))
    val state: StateFlow<BTState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    // Sockets & Threads
    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    
    private var serverJob: Job? = null
    private var clientJob: Job? = null
    private var activeConnectionJob: Job? = null

    private var outputStream: OutputStream? = null

    var onTriggerReceived: ((timerSecs: Int, burstCount: Int, intervalSecs: Int) -> Unit)? = null
    var onCancelReceived: (() -> Unit)? = null
    var onConnectionStateChanged: ((RemoteConnectionState) -> Unit)? = null

    init {
        checkPermissionsAndStatus()
    }

    fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scan = ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN)
            val connect = ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT)
            val advertise = ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_ADVERTISE)
            scan == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    connect == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    advertise == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            val fineLocation = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
            fineLocation == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun checkPermissionsAndStatus() {
        val supported = bluetoothAdapter != null
        val enabled = bluetoothAdapter?.isEnabled == true
        val permissions = hasRequiredPermissions()

        _state.value = _state.value.copy(
            isSupported = supported,
            isEnabled = enabled,
            hasPermissions = permissions
        )

        if (supported && enabled && permissions) {
            loadPairedDevices()
        }
    }

    @SuppressLint("MissingPermission")
    fun loadPairedDevices() {
        if (!hasRequiredPermissions()) return
        try {
            val bonded = bluetoothAdapter?.bondedDevices ?: emptySet()
            val list = bonded.map { device ->
                BTDeviceDisplay(
                    name = device.name ?: "Dispositivo desconocido",
                    address = device.address,
                    device = device
                )
            }
            _state.value = _state.value.copy(pairedDevices = list)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException loading paired devices", e)
        }
    }

    // --- Broadcast Receiver for Scanning ---
    private val btReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (device != null) {
                    val name = device.name ?: "Dispositivo desconocido"
                    val address = device.address
                    val display = BTDeviceDisplay(name, address, device)

                    val currentList = _state.value.discoveredDevices
                    if (!currentList.any { it.address == address }) {
                        _state.value = _state.value.copy(
                            discoveredDevices = currentList + display
                        )
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action) {
                _state.value = _state.value.copy(isScanning = false)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (!hasRequiredPermissions() || bluetoothAdapter == null) return
        try {
            stopScanning()
            _state.value = _state.value.copy(discoveredDevices = emptyList(), isScanning = true)
            
            context.registerReceiver(
                btReceiver,
                IntentFilter(BluetoothDevice.ACTION_FOUND).apply {
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                }
            )
            bluetoothAdapter.startDiscovery()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting bluetooth discovery", e)
            _state.value = _state.value.copy(isScanning = false)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (bluetoothAdapter?.isDiscovering == true) {
            try {
                bluetoothAdapter.cancelDiscovery()
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException canceling discovery", e)
            }
        }
        try {
            context.unregisterReceiver(btReceiver)
        } catch (e: Exception) {
            // Might not be registered
        }
        _state.value = _state.value.copy(isScanning = false)
    }

    // --- Server Mode (Camera Phone) ---
    @SuppressLint("MissingPermission")
    fun startTriggerServer() {
        if (!hasRequiredPermissions() || bluetoothAdapter == null) return
        stopAllConnections()

        _state.value = _state.value.copy(isServerRunning = true, connectionState = RemoteConnectionState.DISCONNECTED)

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, SPP_UUID)
                Log.d(TAG, "Server listening on SPP UUID...")

                while (true) {
                    val socket = serverSocket?.accept() ?: break
                    Log.d(TAG, "Server accepted a connection!")
                    
                    // Connected to remote device
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(
                            connectionState = RemoteConnectionState.CONNECTED,
                            connectedDeviceName = socket.remoteDevice.name ?: "Control Remoto"
                        )
                        onConnectionStateChanged?.invoke(RemoteConnectionState.CONNECTED)
                    }

                    manageConnectedSocket(socket)
                    break // For simplicity, only support one remote connection at a time.
                }
            } catch (e: IOException) {
                Log.e(TAG, "Server socket closed or errored", e)
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(isServerRunning = false, connectionState = RemoteConnectionState.DISCONNECTED)
                    onConnectionStateChanged?.invoke(RemoteConnectionState.DISCONNECTED)
                }
            }
        }
    }

    fun stopTriggerServer() {
        serverJob?.cancel()
        serverJob = null
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket", e)
        }
        serverSocket = null
        _state.value = _state.value.copy(isServerRunning = false)
        stopAllConnections()
    }

    // --- Client Mode (Remote Control Phone) ---
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        if (!hasRequiredPermissions()) return
        stopAllConnections()
        stopScanning()

        _state.value = _state.value.copy(
            connectionState = RemoteConnectionState.CONNECTING,
            connectedDeviceName = device.name ?: "Cámara"
        )
        onConnectionStateChanged?.invoke(RemoteConnectionState.CONNECTING)

        clientJob = scope.launch(Dispatchers.IO) {
            try {
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                clientSocket = socket
                
                // Block until connected or timed out
                socket.connect()
                Log.d(TAG, "Client successfully connected to the Camera!")

                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        connectionState = RemoteConnectionState.CONNECTED
                    )
                    onConnectionStateChanged?.invoke(RemoteConnectionState.CONNECTED)
                }

                manageConnectedSocket(socket)
            } catch (e: IOException) {
                Log.e(TAG, "Client failed to connect", e)
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        connectionState = RemoteConnectionState.DISCONNECTED,
                        connectedDeviceName = null
                    )
                    onConnectionStateChanged?.invoke(RemoteConnectionState.DISCONNECTED)
                }
                try {
                    clientSocket?.close()
                } catch (e2: Exception) {}
                clientSocket = null
            }
        }
    }

    // --- Handle Active Sockets ---
    private fun manageConnectedSocket(socket: BluetoothSocket) {
        activeConnectionJob?.cancel()
        activeConnectionJob = scope.launch(Dispatchers.IO) {
            var inputStream: InputStream? = null
            try {
                inputStream = socket.inputStream
                outputStream = socket.outputStream

                val buffer = ByteArray(1024)
                var bytes: Int

                while (true) {
                    bytes = inputStream.read(buffer) ?: -1
                    if (bytes == -1) break
                    
                    val text = String(buffer, 0, bytes).trim()
                    Log.d(TAG, "Received message over BT: $text")
                    
                    withContext(Dispatchers.Main) {
                        parseIncomingCommand(text)
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Active socket disconnected", e)
            } finally {
                try {
                    inputStream?.close()
                } catch (e: Exception) {}
                try {
                    outputStream?.close()
                } catch (e: Exception) {}
                try {
                    socket.close()
                } catch (e: Exception) {}
                
                outputStream = null
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        connectionState = RemoteConnectionState.DISCONNECTED,
                        connectedDeviceName = null
                    )
                    onConnectionStateChanged?.invoke(RemoteConnectionState.DISCONNECTED)
                }
            }
        }
    }

    // Parses string commands like "TRIGGER:timer=3,burst=5,interval=2"
    private fun parseIncomingCommand(msg: String) {
        if (msg.startsWith("TRIGGER:")) {
            try {
                val params = msg.substring(8).split(",")
                var timer = 0
                var burst = 1
                var interval = 2

                for (p in params) {
                    val kv = p.split("=")
                    if (kv.size == 2) {
                        when (kv[0]) {
                            "timer" -> timer = kv[1].toIntOrNull() ?: 0
                            "burst" -> burst = kv[1].toIntOrNull() ?: 1
                            "interval" -> interval = kv[1].toIntOrNull() ?: 2
                        }
                    }
                }
                onTriggerReceived?.invoke(timer, burst, interval)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing TRIGGER: $msg", e)
            }
        } else if (msg == "CANCEL") {
            onCancelReceived?.invoke()
        }
    }

    // --- Sending Messages ---
    fun sendTrigger(timerSecs: Int, burstCount: Int, intervalSecs: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                val msg = "TRIGGER:timer=$timerSecs,burst=$burstCount,interval=$intervalSecs"
                outputStream?.write(msg.toByteArray())
                outputStream?.flush()
                Log.d(TAG, "Sent TRIGGER command: $msg")
            } catch (e: IOException) {
                Log.e(TAG, "Error sending TRIGGER command", e)
            }
        }
    }

    fun sendCancel() {
        scope.launch(Dispatchers.IO) {
            try {
                outputStream?.write("CANCEL".toByteArray())
                outputStream?.flush()
                Log.d(TAG, "Sent CANCEL command")
            } catch (e: IOException) {
                Log.e(TAG, "Error sending CANCEL command", e)
            }
        }
    }

    // --- Cleanup ---
    fun stopAllConnections() {
        stopScanning()
        clientJob?.cancel()
        clientJob = null
        activeConnectionJob?.cancel()
        activeConnectionJob = null
        
        try {
            clientSocket?.close()
        } catch (e: Exception) {}
        clientSocket = null
        outputStream = null

        _state.value = _state.value.copy(
            connectionState = RemoteConnectionState.DISCONNECTED,
            connectedDeviceName = null
        )
    }

    fun release() {
        stopTriggerServer()
        stopAllConnections()
    }
}
