package com.example.locker.detect

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions

enum class BlinkState {
    WAIT_OPEN,
    WAIT_CLOSE,
    WAIT_REOPEN,
    PASSED
}

/**
 * Phat hien Chop mat (Blink Liveness Detection) dung Google ML Kit Eye Classification.
 * Chuoi xac minh bat buoc: EYE_OPEN -> EYE_CLOSED -> EYE_REOPEN.
 */
class BlinkLivenessDetector {

    private val detector: FaceDetector
    @Volatile
    private var currentState = BlinkState.WAIT_OPEN
    @Volatile
    private var isProcessing = false

    init {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
        detector = FaceDetection.getClient(options)
    }

    fun reset() {
        currentState = BlinkState.WAIT_OPEN
        isProcessing = false
    }

    fun getState(): BlinkState = currentState

    fun processFrameBitmap(
        bitmap: Bitmap,
        onStateChanged: (BlinkState, String) -> Unit,
        onPassed: () -> Unit
    ) {
        if (currentState == BlinkState.PASSED) {
            onPassed()
            return
        }

        if (isProcessing) return
        isProcessing = true

        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    isProcessing = false
                    if (faces.isNotEmpty()) {
                        val face = faces[0]
                        val leftOpenProb = face.leftEyeOpenProbability
                        val rightOpenProb = face.rightEyeOpenProbability

                        if (leftOpenProb != null && rightOpenProb != null) {
                            evaluateEyeState(leftOpenProb, rightOpenProb, onStateChanged, onPassed)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    isProcessing = false
                    Log.e("SMART_LOCKER_DEBUG", "[BlinkDetector] Error in ML Kit face processing: ${e.message}")
                }
        } catch (e: Exception) {
            isProcessing = false
            Log.e("SMART_LOCKER_DEBUG", "[BlinkDetector] Exception processing bitmap: ${e.message}")
        }
    }

    private fun evaluateEyeState(
        leftProb: Float,
        rightProb: Float,
        onStateChanged: (BlinkState, String) -> Unit,
        onPassed: () -> Unit
    ) {
        val isBothOpen = leftProb > 0.6f && rightProb > 0.6f
        val isBothClosed = leftProb < 0.35f && rightProb < 0.35f

        when (currentState) {
            BlinkState.WAIT_OPEN -> {
                if (isBothOpen) {
                    Log.i("SMART_LOCKER_DEBUG", "[EYE_OPEN_DETECTED] Left eye open: %.2f, Right eye open: %.2f".format(leftProb, rightProb))
                    currentState = BlinkState.WAIT_CLOSE
                    onStateChanged(currentState, "Vui lòng chớp mắt...")
                } else {
                    onStateChanged(currentState, "Vui lòng chớp mắt một lần")
                }
            }
            BlinkState.WAIT_CLOSE -> {
                if (isBothClosed) {
                    Log.i("SMART_LOCKER_DEBUG", "[EYE_CLOSED_DETECTED] Left eye closed: %.2f, Right eye closed: %.2f".format(leftProb, rightProb))
                    currentState = BlinkState.WAIT_REOPEN
                    onStateChanged(currentState, "Vui lòng mở mắt trở lại...")
                } else {
                    onStateChanged(currentState, "Vui lòng chớp mắt một lần")
                }
            }
            BlinkState.WAIT_REOPEN -> {
                if (isBothOpen) {
                    Log.i("SMART_LOCKER_DEBUG", "[EYE_REOPEN_DETECTED] Left eye reopened: %.2f, Right eye reopened: %.2f".format(leftProb, rightProb))
                    currentState = BlinkState.PASSED
                    Log.i("SMART_LOCKER_DEBUG", "[LIVENESS_PASS] Valid blink sequence OPEN -> CLOSE -> REOPEN confirmed!")
                    onStateChanged(currentState, "Đã xác nhận chớp mắt người thật!")
                    onPassed()
                } else {
                    onStateChanged(currentState, "Vui lòng mở mắt trở lại...")
                }
            }
            BlinkState.PASSED -> {
                onPassed()
            }
        }
    }
}
