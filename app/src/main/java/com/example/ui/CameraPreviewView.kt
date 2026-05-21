package com.example.ui

import android.content.ContentValues
import android.content.Context
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "CameraPreviewView"

@Composable
fun CameraPreviewView(
    lensFacing: Int,
    shutterTriggerFlow: SharedFlow<Unit>,
    onPhotoCaptured: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Maintain a single executor for image saving operations
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    val previewView = remember { PreviewView(context) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    // Camera Selector (Front or Back)
    val cameraSelector = remember(lensFacing) {
        CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()
    }

    // Capture Sound Player
    val actionSound = remember { MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) } }

    // Re-bind camera provider whenever lens changes
    LaunchedEffect(lensFacing) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()

                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind CameraX use cases", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Shutter Trigger Receiver
    LaunchedEffect(shutterTriggerFlow) {
        shutterTriggerFlow.collectLatest {
            Log.d(TAG, "Received physical capture invocation!")
            
            // 1. Prepare Storage Values
            val filename = "ClickRemoto_${System.currentTimeMillis()}"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ClickRemoto")
                }
            }

            val outputOptions = ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()

            // 2. Perform Shutter Action
            imageCapture.takePicture(
                outputOptions,
                cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val savedUri = outputFileResults.savedUri ?: Uri.EMPTY
                        Log.d(TAG, "Photo successfully saved to: $savedUri")

                        // Trigger visual and auditory shutter feedback on Main thread
                        ContextCompat.getMainExecutor(context).execute {
                            playFeedback(context, actionSound)
                            onPhotoCaptured(savedUri)
                            Toast.makeText(context, "¡Foto guardada!", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Error capturing/saving photo: ${exception.message}", exception)
                        ContextCompat.getMainExecutor(context).execute {
                            Toast.makeText(context, "Error al tomar foto", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.fillMaxSize()
    )
}

// Play audio click & run mechanical device vibration
private fun playFeedback(context: Context, actionSound: MediaActionSound) {
    try {
        actionSound.play(MediaActionSound.SHUTTER_CLICK)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to play feedback sound", e)
    }

    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        vibrator?.let {
            if (it.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(120)
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to vibrate feedback", e)
    }
}
