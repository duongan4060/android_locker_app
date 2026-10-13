package com.example.locker.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.locker.camera.CameraModule
import com.example.locker.camera.FrameSlot
import com.example.locker.core.Globals
import com.example.locker.core.ScanState
import com.example.locker.core.ThreadManager
import com.example.locker.detect.FaceCropper
import com.example.locker.detect.FaceQualityChecker
import com.example.locker.detect.YuNetDetector
import com.example.locker.flow.ScanController

/**
 * Man hinh Quet khuon mat va hien thi camera preview + overlay + preview face crop + timeout/retry controls.
 */
@Composable
fun ScanScreen(
    scanMode: ScanMode,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val frameSlot = remember { FrameSlot() }
    val cameraModule = remember { CameraModule(context, frameSlot) }
    val yunetDetector = remember { YuNetDetector(context) }
    val scanController = remember { ScanController(context, scanMode, frameSlot, onBackClick) }

    var isFlipped180 by remember { mutableStateOf(Globals.isFlipVertical) }

    var guideTextState by remember { mutableStateOf(Globals.currentGuideText) }
    var scanStateState by remember { mutableStateOf(Globals.scanState.get()) }
    var frameTriggerState by remember { mutableLongStateOf(0L) }
    var croppedFaceState by remember { mutableStateOf<Bitmap?>(null) }

    val titleText = when (scanMode) {
        ScanMode.REGISTER -> "ĐĂNG KÍ TỦ MỚI (30 TỦ)"
        ScanMode.OPEN_TEMP -> "MỞ TỦ TẠM THỜI"
        ScanMode.RETURN -> "XÁC NHẬN TRẢ TỦ"
    }

    DisposableEffect(lifecycleOwner) {
        val isDetecting = java.util.concurrent.atomic.AtomicBoolean(true)

        scanController.startScan()

        ThreadManager.detectExecutor.execute {
            Log.d("SMART_LOCKER_DEBUG", "[DetectWorker] Worker loop started on detectExecutor")
            var lastCropTimestamp = 0L

            while (isDetecting.get()) {
                try {
                    frameSlot.frameSignal.acquire()
                    if (!isDetecting.get()) {
                        Log.d("SMART_LOCKER_DEBUG", "[DetectWorker] Worker waking up to exit because isDetecting is false")
                        break
                    }
                    frameSlot.frameSignal.drainPermits()

                    if (frameSlot.hasNewFrame.getAndSet(false)) {
                        val frameBytes = frameSlot.getReadBuffer()
                        val w = Globals.activeFrameWidth
                        val h = Globals.activeFrameHeight

                        if (w > 0 && h > 0) {
                            val currentScanState = Globals.scanState.get()
                            val isTerminalState = currentScanState == ScanState.REGISTERING ||
                                    currentScanState == ScanState.SUCCESS ||
                                    currentScanState == ScanState.TIMEOUT ||
                                    currentScanState == ScanState.ERROR

                            if (!isTerminalState) {
                                // Run YuNet face detection ONLY when scan session is active
                                yunetDetector.detectFaces(frameBytes, w, h)

                                // Run Quality Lọc Mặt
                                FaceQualityChecker.checkQuality(
                                    Globals.currentRawBbox.get(),
                                    Globals.currentLandmarks.get(),
                                    w,
                                    h
                                )
                            } else {
                                Globals.currentRawBbox.set(null)
                                Globals.currentLandmarks.set(null)
                            }

                            val rawBbox = Globals.currentRawBbox.get()
                            val nowMs = System.currentTimeMillis()

                            // Crop face bitmap at throttled ~100ms interval to eliminate UI lag & GC pressure
                            val croppedBitmap = if (rawBbox != null && !isTerminalState && (nowMs - lastCropTimestamp >= 100L)) {
                                lastCropTimestamp = nowMs
                                FaceCropper.cropFace(
                                    frameBytes = frameBytes,
                                    frameW = w,
                                    frameH = h,
                                    rawBbox = rawBbox,
                                    isMirror = true,
                                    isFlip180 = Globals.isFlipVertical
                                )
                            } else {
                                null
                            }

                            val currentText = Globals.currentGuideText

                            ContextCompat.getMainExecutor(context).execute {
                                guideTextState = currentText
                                scanStateState = currentScanState
                                frameTriggerState = nowMs

                                if (croppedBitmap != null) {
                                    val oldBitmap = croppedFaceState
                                    croppedFaceState = croppedBitmap

                                    if (Globals.isFaceValid) {
                                        scanController.processBlinkFrame(croppedBitmap, scanController.getActiveSessionId())
                                    }

                                    if (oldBitmap != null && oldBitmap != croppedBitmap && !oldBitmap.isRecycled) {
                                        oldBitmap.recycle()
                                    }
                                } else if (rawBbox == null || isTerminalState) {
                                    val oldBitmap = croppedFaceState
                                    croppedFaceState = null
                                    if (oldBitmap != null && !oldBitmap.isRecycled) {
                                        oldBitmap.recycle()
                                    }
                                }
                            }
                        }
                    }
                } catch (_: InterruptedException) {
                    Log.d("SMART_LOCKER_DEBUG", "[DetectWorker] Worker interrupted, exiting")
                    break
                } catch (e: Exception) {
                    Log.e("SMART_LOCKER_DEBUG", "[DetectWorker] Error in detect loop: ${e.message}", e)
                }
            }
            Log.d("SMART_LOCKER_DEBUG", "[DetectWorker] Worker loop EXITED cleanly on detectExecutor")
        }

        onDispose {
            Log.d("SMART_LOCKER_DEBUG", "[ScanScreen] onDispose called - releasing worker signal")
            isDetecting.set(false)
            frameSlot.frameSignal.release() // Unblocks acquire() so worker thread exits loop immediately!

            scanController.stopScan()
            cameraModule.stopCamera()

            Globals.currentRawBbox.set(null)
            Globals.currentLandmarks.set(null)
            Globals.isFaceValid = false
            Globals.stableFrameCounter.set(0)
            Globals.scanState.set(ScanState.IDLE)
            Globals.currentGuideText = "Đưa mặt vào khung"

            croppedFaceState?.let {
                if (!it.isRecycled) it.recycle()
            }
            croppedFaceState = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // CameraX Preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    cameraModule.startCamera(lifecycleOwner, this)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = if (isFlipped180) 180f else 0f
                }
        )

        // Face Overlay Canvas
        FaceOverlay(
            frameTrigger = frameTriggerState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = if (isFlipped180) 180f else 0f
                }
        )

        // Top Header Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = titleText,
                    color = Color.Yellow,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = guideTextState,
                    color = if (scanStateState == ScanState.TIMEOUT || scanStateState == ScanState.ERROR) Color(0xFFEF4444) else Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Top-Right Corner Small Face Preview
        FaceCropPreview(
            faceBitmap = croppedFaceState,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 90.dp, end = 16.dp)
        )

        // Bottom Bar / Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel / Back Button
            Button(
                onClick = onBackClick,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                modifier = Modifier
                    .width(150.dp)
                    .height(56.dp)
            ) {
                Text(
                    text = "HỦY / QUAY LẠI",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }

            // Retry Button when TIMEOUT or ERROR
            if (scanStateState == ScanState.TIMEOUT || scanStateState == ScanState.ERROR) {
                Button(
                    onClick = {
                        scanController.retryScan()
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    modifier = Modifier
                        .width(150.dp)
                        .height(56.dp)
                ) {
                    Text(
                        text = "THỬ LẠI",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Button to flip camera 180 degrees if webcam is upside down
                OutlinedButton(
                    onClick = {
                        isFlipped180 = !isFlipped180
                        Globals.isFlipVertical = isFlipped180
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .width(150.dp)
                        .height(56.dp)
                ) {
                    Text(
                        text = if (isFlipped180) "XOAY LẠI 0°" else "XOAY MẶT 180°",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Cyan,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private const val TAG = "ScanScreen"
