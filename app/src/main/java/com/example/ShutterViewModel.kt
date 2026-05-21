package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.BluetoothShutterController
import com.example.bluetooth.RemoteConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "ShutterViewModel"

enum class AppScreen {
    MENU,
    CAMERA,
    REMOTE
}

data class CaptureConfig(
    val timerSecs: Int = 0,         // 0 (Inmediato), 3, 5, 10
    val burstCount: Int = 1,        // 1, 3, 5, 10
    val intervalSecs: Int = 2       // 2, 5, 10 segundos
)

class ShutterViewModel(application: Application) : AndroidViewModel(application) {

    // Bluetooth Controller
    val btController = BluetoothShutterController(application.applicationContext)
    
    // Screens State
    private val _currentScreen = MutableStateFlow(AppScreen.MENU)
    val currentScreen: StateFlow<AppScreen> = _currentScreen

    // Lens facing state (Front vs Back)
    private val _lensFacing = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val lensFacing: StateFlow<Int> = _lensFacing

    // Local configuration (Remote UI selection)
    private val _captureConfig = MutableStateFlow(CaptureConfig())
    val captureConfig: StateFlow<CaptureConfig> = _captureConfig

    // Active execution state (On Camera Phone)
    private val _countdownValue = MutableStateFlow<Int?>(null)
    val countdownValue: StateFlow<Int?> = _countdownValue

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing

    private val _activeShotIndex = MutableStateFlow(0)
    val activeShotIndex: StateFlow<Int> = _activeShotIndex

    private val _totalShotsToTake = MutableStateFlow(0)
    val totalShotsToTake: StateFlow<Int> = _totalShotsToTake

    // Gallery of recent captured photos (Uris)
    private val _capturedUris = MutableStateFlow<List<Uri>>(emptyList())
    val capturedUris: StateFlow<List<Uri>> = _capturedUris

    // Shared Flow to signal CameraX Composable to execute physical shutter capture
    private val _shutterTriggerSignal = MutableSharedFlow<Unit>(replay = 0)
    val shutterTriggerSignal: SharedFlow<Unit> = _shutterTriggerSignal.asSharedFlow()

    private var activeJob: Job? = null

    init {
        // Set up Bluetooth triggers
        btController.onTriggerReceived = { timer, burst, interval ->
            Log.d(TAG, "Trigger command received! timer=$timer, burst=$burst, interval=$interval")
            // Run on UI / ViewModel coroutine
            viewModelScope.launch {
                startCaptureSequence(timer, burst, interval)
            }
        }

        btController.onCancelReceived = {
            Log.d(TAG, "Cancel received from Remote")
            cancelCaptureSequence()
        }
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
        
        // Logical cleanup when switching screens
        if (screen == AppScreen.CAMERA) {
            btController.startTriggerServer()
        } else if (screen == AppScreen.REMOTE) {
            btController.loadPairedDevices()
            btController.startScanning()
        } else {
            btController.stopTriggerServer()
            btController.stopAllConnections()
        }
    }

    fun toggleLens() {
        _lensFacing.value = if (_lensFacing.value == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
    }

    fun updateConfig(timer: Int? = null, burst: Int? = null, interval: Int? = null) {
        val current = _captureConfig.value
        _captureConfig.value = current.copy(
            timerSecs = timer ?: current.timerSecs,
            burstCount = burst ?: current.burstCount,
            intervalSecs = interval ?: current.intervalSecs
        )
    }

    // --- Shutter triggering flow ---
    fun initiateCapture() {
        // If we are in Remote Mode, we transmit the command over Bluetooth
        if (_currentScreen.value == AppScreen.REMOTE) {
            val config = _captureConfig.value
            btController.sendTrigger(config.timerSecs, config.burstCount, config.intervalSecs)
        } else {
            // Camera Mode triggers locally
            val config = _captureConfig.value
            viewModelScope.launch {
                startCaptureSequence(config.timerSecs, config.burstCount, config.intervalSecs)
            }
        }
    }

    fun cancelCapture() {
        if (_currentScreen.value == AppScreen.REMOTE) {
            btController.sendCancel()
        } else {
            cancelCaptureSequence()
        }
    }

    private fun startCaptureSequence(timer: Int, burst: Int, interval: Int) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _isCapturing.value = true
            _totalShotsToTake.value = burst
            _activeShotIndex.value = 0

            // 1. Run Countdown if active
            if (timer > 0) {
                var localCounter = timer
                while (localCounter > 0) {
                    _countdownValue.value = localCounter
                    delay(1000L)
                    localCounter--
                }
            }
            _countdownValue.value = 0 // Show "GO!" or take immediately
            delay(200)                 // brief pause
            _countdownValue.value = null

            // 2. Perform Bursts
            for (i in 0 until burst) {
                _activeShotIndex.value = i + 1
                Log.d(TAG, "Requesting photo capture ${i + 1} of $burst")
                _shutterTriggerSignal.emit(Unit)
                
                if (i < burst - 1) {
                    delay(interval * 1000L)
                }
            }

            // Cleanup
            delay(1000L) // Wait slightly so UI transition looks smooth
            _isCapturing.value = false
            _activeShotIndex.value = 0
            _totalShotsToTake.value = 0
        }
    }

    fun cancelCaptureSequence() {
        activeJob?.cancel()
        activeJob = null
        _countdownValue.value = null
        _isCapturing.value = false
        _activeShotIndex.value = 0
        _totalShotsToTake.value = 0
    }

    fun addCapturedPhoto(uri: Uri) {
        _capturedUris.value = (_capturedUris.value + uri).takeLast(10) // Keep last 10 photos
    }

    fun clearSavedPhotos() {
        _capturedUris.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        btController.release()
    }
}
