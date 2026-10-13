package com.example.locker.detect

import android.content.Context
import android.graphics.RectF
import android.util.Log
import com.example.locker.core.AppConfig
import com.example.locker.core.Globals
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.FaceDetectorYN
import java.io.File
import java.io.FileOutputStream

/**
 * Phat hien khuon mat realtime bang OpenCV YuNet (FaceDetectorYN) voi Auto-Orientation va phat hien toan frame.
 */
class YuNetDetector(private val context: Context) {

    private var detectorYN: FaceDetectorYN? = null

    private var frameWidth: Int = 640
    private var frameHeight: Int = 480

    private var rgbaMat: Mat = Mat(frameHeight, frameWidth, CvType.CV_8UC4)
    private var bgrMat: Mat = Mat(frameHeight, frameWidth, CvType.CV_8UC3)
    private val resizedFrameMat: Mat = Mat(AppConfig.DETECT_INPUT_H, AppConfig.DETECT_INPUT_W, CvType.CV_8UC3)
    private val tempRotatedMat: Mat = Mat()
    private var facesMat: Mat = Mat()

    private val tempLandmarks = FloatArray(10)

    private var lastFpsTimestamp: Long = System.currentTimeMillis()
    private var detectCount: Int = 0

    init {
        initYuNetModel()
    }

    private fun initYuNetModel() {
        try {
            val modelFile = copyAssetToFilesDir(context, AppConfig.YUNET_MODEL_FILE)
            if (!modelFile.exists()) {
                Log.e(TAG, "[YuNetInit] YuNet model file NOT found at: ${modelFile.absolutePath}")
                Globals.currentGuideText = "Thiếu model YuNet (${AppConfig.YUNET_MODEL_FILE})"
                return
            }

            val inputSize = Size(AppConfig.DETECT_INPUT_W.toDouble(), AppConfig.DETECT_INPUT_H.toDouble())

            detectorYN = FaceDetectorYN.create(
                modelFile.absolutePath,
                "",
                inputSize,
                AppConfig.DETECT_SCORE_MIN,
                0.3f,
                5000
            )

            if (detectorYN != null) {
                Log.i(TAG, "[YuNetInit] YuNet FaceDetectorYN initialized successfully from ${modelFile.absolutePath}")
            } else {
                Log.e(TAG, "[YuNetInit] FaceDetectorYN.create returned null")
                Globals.currentGuideText = "Lỗi khởi tạo mô hình YuNet"
            }
        } catch (e: Exception) {
            Log.e(TAG, "[YuNetInit] Failed to initialize YuNet Detector: ${e.message}", e)
            Globals.currentGuideText = "Lỗi nạp model YuNet"
        }
    }

    /**
     * Chay phat hien mat tren RGBA frame byte array voi pham vi toan frame camera.
     */
    fun detectFaces(rgbaBytes: ByteArray, width: Int, height: Int) {
        val detector = detectorYN
        if (detector == null) {
            Log.e("SMART_LOCKER_DEBUG", "[YuNet] Detector is null, skipping detection")
            Globals.currentRawBbox.set(null)
            Globals.currentLandmarks.set(null)
            return
        }

        try {
            Log.d("SMART_LOCKER_DEBUG", "[YuNet] Calling detectFaces on image ($width x $height)")

            // Update Mat dimensions if resolution changed
            if (width != frameWidth || height != frameHeight) {
                frameWidth = width
                frameHeight = height
                rgbaMat.release()
                bgrMat.release()
                rgbaMat = Mat(height, width, CvType.CV_8UC4)
                bgrMat = Mat(height, width, CvType.CV_8UC3)
            }

            // Copy bytes to Mat
            rgbaMat.put(0, 0, rgbaBytes)

            // Convert RGBA to BGR for OpenCV
            Imgproc.cvtColor(rgbaMat, bgrMat, Imgproc.COLOR_RGBA2BGR)

            // User manual flip toggle
            if (Globals.isFlipVertical) {
                Core.rotate(bgrMat, bgrMat, Core.ROTATE_180)
            }

            val matCols = bgrMat.cols()
            val matRows = bgrMat.rows()
            if (matCols <= 0 || matRows <= 0) {
                Log.w(TAG, "[YuNetProcess] Invalid frame dimensions: $matCols x $matRows")
                Globals.currentRawBbox.set(null)
                Globals.currentLandmarks.set(null)
                return
            }

            // Resize full frame to DETECT_INPUT_W x DETECT_INPUT_H (256x320)
            val inputW = AppConfig.DETECT_INPUT_W
            val inputH = AppConfig.DETECT_INPUT_H
            Imgproc.resize(bgrMat, resizedFrameMat, Size(inputW.toDouble(), inputH.toDouble()))

            // Clear previous faces output Mat before detection
            facesMat.release()
            facesMat = Mat()

            var currentRotation = 0

            // 1. Try default 0 degree orientation
            detector.detect(resizedFrameMat, facesMat)

            // 2. Try 180 degrees (keeps 256x320 shape intact)
            if (facesMat.rows() == 0) {
                Core.rotate(resizedFrameMat, tempRotatedMat, Core.ROTATE_180)
                detector.detect(tempRotatedMat, facesMat)
                if (facesMat.rows() > 0) {
                    currentRotation = 180
                }
            }

            val numFaces = facesMat.rows()

            if (numFaces > 0) {
                val faceData = FloatArray(15)
                facesMat.get(0, 0, faceData)

                val score = faceData[14]
                Globals.faceQualityScore = score

                if (score >= AppConfig.DETECT_SCORE_MIN) {
                    var fx = faceData[0]
                    var fy = faceData[1]
                    var fw = faceData[2]
                    var fh = faceData[3]

                    for (i in 0 until 5) {
                        tempLandmarks[i * 2] = faceData[4 + i * 2]
                        tempLandmarks[i * 2 + 1] = faceData[5 + i * 2]
                    }

                    // Map coordinates back if rotated 180° during detection
                    if (currentRotation == 180) {
                        fx = inputW - fx - fw
                        fy = inputH - fy - fh
                        for (i in 0 until 5) {
                            tempLandmarks[i * 2] = inputW - tempLandmarks[i * 2]
                            tempLandmarks[i * 2 + 1] = inputH - tempLandmarks[i * 2 + 1]
                        }
                    }

                    val scaleX = matCols.toFloat() / inputW.toFloat()
                    val scaleY = matRows.toFloat() / inputH.toFloat()

                    var faceX = fx * scaleX
                    var faceY = fy * scaleY
                    val faceW = fw * scaleX
                    val faceH = fh * scaleY

                    for (i in 0 until 5) {
                        tempLandmarks[i * 2] = tempLandmarks[i * 2] * scaleX
                        tempLandmarks[i * 2 + 1] = tempLandmarks[i * 2 + 1] * scaleY
                    }

                    if (Globals.isFlipVertical) {
                        faceX = matCols - faceX - faceW
                        faceY = matRows - faceY - faceH
                        for (i in 0 until 5) {
                            tempLandmarks[i * 2] = matCols - tempLandmarks[i * 2]
                            tempLandmarks[i * 2 + 1] = matRows - tempLandmarks[i * 2 + 1]
                        }
                    }

                    val bbox = RectF(faceX, faceY, faceX + faceW, faceY + faceH)
                    Globals.currentRawBbox.set(bbox)
                    Globals.currentLandmarks.set(tempLandmarks.clone())

                    Log.i("SMART_LOCKER_DEBUG", "[YuNet] Face detected! numFaces: $numFaces, score: %.2f, bbox: $bbox".format(score))
                } else {
                    Log.d("SMART_LOCKER_DEBUG", "[YuNet] Face detected but score low: %.2f < %.2f".format(score, AppConfig.DETECT_SCORE_MIN))
                    Globals.currentRawBbox.set(null)
                    Globals.currentLandmarks.set(null)
                    Globals.faceQualityScore = score
                }
            } else {
                Log.d("SMART_LOCKER_DEBUG", "[YuNet] No face detected in frame ($matCols x $matRows)")
                Globals.currentRawBbox.set(null)
                Globals.currentLandmarks.set(null)
                Globals.faceQualityScore = 0.0f
            }

            detectCount++
            val now = System.currentTimeMillis()
            if (now - lastFpsTimestamp >= 1000) {
                Globals.detectFps = (detectCount * 1000f) / (now - lastFpsTimestamp)
                detectCount = 0
                lastFpsTimestamp = now
            }
        } catch (e: Exception) {
            Log.e("SMART_LOCKER_DEBUG", "[YuNet] Error during detectFaces: ${e.message}", e)
            Globals.currentRawBbox.set(null)
            Globals.currentLandmarks.set(null)

            // Re-initialize detector if native state corrupted
            try {
                initYuNetModel()
            } catch (_: Exception) {}
        }
    }

    private fun copyAssetToFilesDir(context: Context, assetPath: String): File {
        val fileName = assetPath.substringAfterLast("/")
        val outFile = File(context.filesDir, fileName)

        if (!outFile.exists()) {
            try {
                context.assets.open(assetPath).use { inputStream ->
                    FileOutputStream(outFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Log.i(TAG, "[YuNetInit] Copied asset $assetPath to ${outFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "[YuNetInit] Failed to copy asset $assetPath: ${e.message}", e)
            }
        }
        return outFile
    }

    companion object {
        private const val TAG = "YuNetDetector"
    }
}


