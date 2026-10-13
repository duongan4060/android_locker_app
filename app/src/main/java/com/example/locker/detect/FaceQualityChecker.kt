package com.example.locker.detect

import android.graphics.RectF
import android.util.Log
import com.example.locker.core.AppConfig
import com.example.locker.core.Globals
import com.example.locker.core.ScanState
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Kiem tra va loc chat luong khuon mat (nam trong ROI, ti le, goc nhin thang, do net).
 */
object FaceQualityChecker {

    private const val TAG = "FaceQualityChecker"

    /**
     * Alpha filter va kiem tra chat luong khuon mat.
     *
     * @param bbox Bounding box nguyen ban trong frame
     * @param landmarks Mang 10 float 5 landmarks (x0,y0...x4,y4)
     * @param frameWidth Chieu rong frame
     * @param frameHeight Chieu cao frame
     * @return Boolean true neu mat thoa man tat ca dieu kien chat luong
     */
    fun checkQuality(
        bbox: RectF?,
        landmarks: FloatArray?,
        frameWidth: Int,
        frameHeight: Int
    ): Boolean {
        val currentState = Globals.scanState.get()

        // Do not update stable counter if already in terminal or recognition processing state
        if (currentState == ScanState.RECOGNIZING ||
            currentState == ScanState.REGISTERING ||
            currentState == ScanState.SUCCESS ||
            currentState == ScanState.TIMEOUT ||
            currentState == ScanState.ERROR) {
            Log.d("SMART_LOCKER_DEBUG", "[FaceQuality] Paused quality check in state $currentState")
            return currentState == ScanState.SUCCESS
        }

        if (bbox == null || landmarks == null || landmarks.size < 10) {
            resetQualityState("Vui lòng nhìn vào camera")
            return false
        }

        Log.d("SMART_LOCKER_DEBUG", "[FACE_DETECTED] Face detected at bbox: $bbox")

        val roiPixel = RectF(RoiMapper.getFramePixelRoi(frameWidth, frameHeight))

        // 1. Check if face bounding box is FULLY inside inner guide frame ROI
        if (!roiPixel.contains(bbox)) {
            resetQualityState("Vui lòng đưa mặt vào giữa khung")
            return false
        }

        // 2. Check face height ratio relative to inner guide frame height (>= 70%)
        val faceHeight = bbox.height()
        val guideFrameInnerHeight = roiPixel.height().toFloat()
        val faceRatio = if (guideFrameInnerHeight > 0f) faceHeight / guideFrameInnerHeight else 0f

        Log.d("SMART_LOCKER_DEBUG", "[FACE_RATIO_CHECK] Face H: %.1f, Guide H: %.1f, Ratio: %.3f".format(faceHeight, guideFrameInnerHeight, faceRatio))

        if (faceRatio < 0.60f) {
            resetQualityState("Vui lòng đưa mặt lại gần")
            return false
        }

        Log.i("SMART_LOCKER_DEBUG", "[FACE_POSITION_VALID] Face inside ROI and height ratio %.3f >= 0.70 is valid".format(faceRatio))

        // 3. Check looking straight / Yaw angle for geometry stability
        val rightEyeX = landmarks[0]
        val rightEyeY = landmarks[1]
        val leftEyeX = landmarks[2]
        val leftEyeY = landmarks[3]
        val noseX = landmarks[4]

        val eyeMidX = (rightEyeX + leftEyeX) / 2.0f
        val eyeDist = hypot((leftEyeX - rightEyeX).toDouble(), (leftEyeY - rightEyeY).toDouble()).toFloat()

        if (eyeDist <= 0.001f) {
            resetQualityState("Vui lòng nhìn vào camera")
            return false
        }

        val yawRatio = abs(noseX - eyeMidX) / eyeDist
        if (yawRatio > AppConfig.YAW_MAX) {
            resetQualityState("Vui lòng nhìn thẳng vào camera")
            return false
        }

        // Geometry and ratio checks passed! Update stable frame counter
        val currentCount = Globals.stableFrameCounter.incrementAndGet()
        Globals.scanState.compareAndSet(ScanState.DETECTING, ScanState.STABILIZING)
        Globals.scanState.compareAndSet(ScanState.IDLE, ScanState.STABILIZING)

        val isQualityOk = currentCount >= AppConfig.STABLE_FRAMES
        if (isQualityOk) {
            Globals.isFaceValid = true
            Globals.currentGuideText = "Vui lòng chớp mắt một lần"
            Log.i("SMART_LOCKER_DEBUG", "[FACE_STABLE] Face stable for $currentCount/${AppConfig.STABLE_FRAMES} frames")
        } else {
            Globals.isFaceValid = false
            Globals.currentGuideText = "Vui lòng giữ khuôn mặt ổn định"
        }

        Log.d("SMART_LOCKER_DEBUG", "[FaceQuality] Quality ok: $isQualityOk, count: $currentCount/${AppConfig.STABLE_FRAMES}, state: ${Globals.scanState.get()}")
        return isQualityOk
    }

    private fun resetQualityState(guideMessage: String) {
        Globals.stableFrameCounter.set(0)
        Globals.isFaceValid = false
        Globals.currentGuideText = guideMessage

        val currentState = Globals.scanState.get()
        if (currentState == ScanState.STABILIZING) {
            Globals.scanState.set(ScanState.DETECTING)
        }
    }
}



