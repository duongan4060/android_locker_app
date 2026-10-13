package com.example.locker.camera

import android.content.Context
import android.util.Log
import android.util.Rational
import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.locker.core.Globals
import com.example.locker.core.ThreadManager
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Quan ly khoi dong va gan CameraX vao Viewport va Pipeline phan tich anh - Che do doc (Portrait).
 */
class CameraModule(
    private val context: Context,
    private val frameSlot: FrameSlot
) {
    private var cameraProvider: ProcessCameraProvider? = null

    private var lastFpsTimestamp: Long = System.currentTimeMillis()
    private var frameCount: Int = 0

    // Temporary reusable byte array for unrotated source frame bytes
    private var tempSrcBytes: ByteArray = ByteArray(640 * 480 * 4)

    /**
     * Bind CameraX vao LifecycleOwner va PreviewView.
     */
    fun startCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(lifecycleOwner, previewView)
            } catch (e: Exception) {
                Log.e(TAG, "[CameraModule] Error starting camera: ${e.message}", e)
                Globals.currentGuideText = "Lỗi khởi tạo Camera"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Unbind toan bo usecase camera khi roi khoi man hinh quet.
     */
    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            Log.d(TAG, "[CameraModule] Camera unbound successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "[CameraModule] Error stopping camera: ${e.message}", e)
        }
    }

    private fun bindCameraUseCases(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        // Configure 3:4 Aspect Ratio Strategy for Portrait mode
        val aspectStrategy = AspectRatioStrategy(
            AspectRatio.RATIO_4_3,
            AspectRatioStrategy.FALLBACK_RULE_AUTO
        )
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(aspectStrategy)
            .build()

        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalysis.setAnalyzer(ThreadManager.cameraExecutor) { imageProxy ->
            processImageFrame(imageProxy)
        }

        val viewPort = ViewPort.Builder(Rational(3, 4), previewView.display?.rotation ?: Surface.ROTATION_0).build()

        val useCaseGroup = UseCaseGroup.Builder()
            .setViewPort(viewPort)
            .addUseCase(preview)
            .addUseCase(imageAnalysis)
            .build()

        val selectorsToTry = listOf(
            CameraSelector.DEFAULT_FRONT_CAMERA,
            CameraSelector.DEFAULT_BACK_CAMERA,
            CameraSelector.Builder().build()
        )

        var isBoundSuccess = false
        for (selector in selectorsToTry) {
            try {
                if (provider.hasCamera(selector)) {
                    provider.bindToLifecycle(lifecycleOwner, selector, useCaseGroup)
                    Log.d(TAG, "[CameraModule] Camera UseCases bound successfully with selector: $selector")
                    isBoundSuccess = true
                    break
                }
            } catch (e: Exception) {
                Log.w(TAG, "[CameraModule] Failed binding camera with selector $selector: ${e.message}")
            }
        }

        if (!isBoundSuccess) {
            for (selector in selectorsToTry) {
                try {
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)
                    Log.d(TAG, "[CameraModule] Camera bound without ViewPort with selector: $selector")
                    isBoundSuccess = true
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "[CameraModule] Failed binding unconstrained camera: ${e.message}")
                }
            }
        }

        if (!isBoundSuccess) {
            Log.e(TAG, "[CameraModule] All camera selectors failed on this emulator/device!")
            Globals.currentGuideText = "Lỗi kết nối Camera máy ảo. Hãy bật VirtualScene/Webcam trong AVD"
        }
    }

    private fun processImageFrame(imageProxy: ImageProxy) {
        try {
            val srcW = imageProxy.width
            val srcH = imageProxy.height
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            Log.d("SMART_LOCKER_DEBUG", "[CameraX] Frame received (${srcW}x${srcH}, rot: $rotationDegrees)")

            // Calculate output dimensions after applying sensor rotation to make frame upright (Portrait)
            val outW = if (rotationDegrees == 90 || rotationDegrees == 270) srcH else srcW
            val outH = if (rotationDegrees == 90 || rotationDegrees == 270) srcW else srcH

            Globals.activeFrameWidth = outW
            Globals.activeFrameHeight = outH
            Globals.cameraRotationDegrees = rotationDegrees

            val plane = imageProxy.planes[0]
            val buffer: ByteBuffer = plane.buffer

            val destBuffer = frameSlot.getWriteBuffer()
            val totalBytesNeeded = srcW * srcH * 4

            if (tempSrcBytes.size < totalBytesNeeded) {
                tempSrcBytes = ByteArray(totalBytesNeeded)
            }

            val bytesToRead = minOf(buffer.remaining(), totalBytesNeeded)
            buffer.get(tempSrcBytes, 0, bytesToRead)

            if (rotationDegrees == 0) {
                System.arraycopy(tempSrcBytes, 0, destBuffer, 0, minOf(bytesToRead, destBuffer.size))
            } else {
                rotateRgbaBytes(
                    srcBytes = tempSrcBytes,
                    srcW = srcW,
                    srcH = srcH,
                    rotationDegrees = rotationDegrees,
                    destBytes = destBuffer
                )
            }

            // Swap buffer and notify detection loop
            frameSlot.swapAndSignal()

            // Calculate FPS
            frameCount++
            val now = System.currentTimeMillis()
            if (now - lastFpsTimestamp >= 1000) {
                Globals.cameraFps = (frameCount * 1000f) / (now - lastFpsTimestamp)
                frameCount = 0
                lastFpsTimestamp = now
            }
        } catch (e: Exception) {
            Log.e(TAG, "[CameraModule] Error in processImageFrame: ${e.message}", e)
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Rotate RGBA_8888 byte buffer by 90, 180, or 270 degrees clockwise to make frame upright.
     */
    private fun rotateRgbaBytes(
        srcBytes: ByteArray,
        srcW: Int,
        srcH: Int,
        rotationDegrees: Int,
        destBytes: ByteArray
    ) {
        val srcInts = ByteBuffer.wrap(srcBytes).order(ByteOrder.nativeOrder()).asIntBuffer()
        val destInts = ByteBuffer.wrap(destBytes).order(ByteOrder.nativeOrder()).asIntBuffer()

        when (rotationDegrees) {
            90 -> {
                // 90° CW: destW = srcH, destH = srcW
                // destX = srcH - 1 - y, destY = x
                for (y in 0 until srcH) {
                    val srcRowOffset = y * srcW
                    val destColOffset = srcH - 1 - y
                    for (x in 0 until srcW) {
                        val pixel = srcInts.get(srcRowOffset + x)
                        val destIdx = x * srcH + destColOffset
                        destInts.put(destIdx, pixel)
                    }
                }
            }
            270 -> {
                // 270° CW (90° CCW): destW = srcH, destH = srcW
                // destX = y, destY = srcW - 1 - x
                for (y in 0 until srcH) {
                    val srcRowOffset = y * srcW
                    for (x in 0 until srcW) {
                        val pixel = srcInts.get(srcRowOffset + x)
                        val destIdx = (srcW - 1 - x) * srcH + y
                        destInts.put(destIdx, pixel)
                    }
                }
            }
            180 -> {
                // 180° CW: destW = srcW, destH = srcH
                for (y in 0 until srcH) {
                    val srcRowOffset = y * srcW
                    val destRowOffset = (srcH - 1 - y) * srcW
                    for (x in 0 until srcW) {
                        val pixel = srcInts.get(srcRowOffset + x)
                        val destIdx = destRowOffset + (srcW - 1 - x)
                        destInts.put(destIdx, pixel)
                    }
                }
            }
            else -> {
                System.arraycopy(srcBytes, 0, destBytes, 0, minOf(srcBytes.size, destBytes.size))
            }
        }
    }

    companion object {
        private const val TAG = "CameraModule"
    }
}

