package com.example.locker.recog

import com.example.locker.core.AppConfig
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Can chinh khuon mat (Face Alignment) tu 5 landmarks YuNet sang mau ArcFace 112x112 bang thuat toan Umeyama.
 */
object FaceAligner {

    /** Standard ArcFace 112x112 target landmark coordinates */
    private val TARGET_LANDMARKS = floatArrayOf(
        38.2946f, 51.6963f, // Right eye
        73.5318f, 51.5014f, // Left eye
        56.0252f, 71.7366f, // Nose
        41.5493f, 92.3655f, // Right mouth corner
        70.7299f, 92.2041f  // Left mouth corner
    )

    // Pre-calculated target mean
    private const val TARGET_MEAN_X = 56.02616f
    private const val TARGET_MEAN_Y = 71.90078f

    // Pre-allocated reusable Mat for 2x3 Affine matrix (zero heap allocation in hot path)
    private val affineMat = Mat(2, 3, CvType.CV_32F)
    private val affineData = FloatArray(6)

    /**
     * Thuc hien Căn chỉnh mặt va Crop ra anh 112x112 RGB.
     *
     * @param srcFrameMat Mat BGR hoac RGBA cua frame anh goc
     * @param landmarks Mang 10 float 5 landmarks phat hien tu YuNet
     * @param dstAlignedMat Mat ket qua 112x112 da duoc warpAffine
     */
    fun alignFace(srcFrameMat: Mat, landmarks: FloatArray, dstAlignedMat: Mat) {
        if (landmarks.size < 10) return

        // 1. Calculate source mean
        // LOCAL srcMeanX: Trung diem X cua 5 landmark nguon | file: FaceAligner.kt > alignFace > mean x
        var srcMeanX = 0.0f
        // LOCAL srcMeanY: Trung diem Y cua 5 landmark nguon | file: FaceAligner.kt > alignFace > mean y
        var srcMeanY = 0.0f

        for (i in 0 until 5) {
            srcMeanX += landmarks[i * 2]
            srcMeanY += landmarks[i * 2 + 1]
        }
        srcMeanX /= 5.0f
        srcMeanY /= 5.0f

        // 2. Compute covariance components and variance
        // LOCAL h00: Covariance h00 | file: FaceAligner.kt > alignFace > cov
        var h00 = 0.0f
        // LOCAL h01: Covariance h01 | file: FaceAligner.kt > alignFace > cov
        var h01 = 0.0f
        // LOCAL h10: Covariance h10 | file: FaceAligner.kt > alignFace > cov
        var h10 = 0.0f
        // LOCAL h11: Covariance h11 | file: FaceAligner.kt > alignFace > cov
        var h11 = 0.0f
        // LOCAL srcVar: Variance cua landmarks nguon | file: FaceAligner.kt > alignFace > var
        var srcVar = 0.0f

        for (i in 0 until 5) {
            // LOCAL dxS: Do lech X so voi srcMean | file: FaceAligner.kt > alignFace > diff
            val dxS = landmarks[i * 2] - srcMeanX
            // LOCAL dyS: Do lech Y so voi srcMean | file: FaceAligner.kt > alignFace > diff
            val dyS = landmarks[i * 2 + 1] - srcMeanY

            // LOCAL dxT: Do lech X so voi targetMean | file: FaceAligner.kt > alignFace > diff
            val dxT = TARGET_LANDMARKS[i * 2] - TARGET_MEAN_X
            // LOCAL dyT: Do lech Y so voi targetMean | file: FaceAligner.kt > alignFace > diff
            val dyT = TARGET_LANDMARKS[i * 2 + 1] - TARGET_MEAN_Y

            srcVar += dxS * dxS + dyS * dyS

            h00 += dxS * dxT
            h01 += dxS * dyT
            h10 += dyS * dxT
            h11 += dyS * dyT
        }

        if (srcVar <= 0.0001f) return

        // 3. Compute rotation/scale matrix parameters (a, b)
        // LOCAL a: Transformation parameter a | file: FaceAligner.kt > alignFace > param a
        val a = (h00 + h11) / srcVar
        // LOCAL b: Transformation parameter b | file: FaceAligner.kt > alignFace > param b
        val b = (h10 - h01) / srcVar

        // LOCAL tx: Translation X | file: FaceAligner.kt > alignFace > tx
        val tx = TARGET_MEAN_X - a * srcMeanX - b * srcMeanY
        // LOCAL ty: Translation Y | file: FaceAligner.kt > alignFace > ty
        val ty = TARGET_MEAN_Y + b * srcMeanX - a * srcMeanY

        // Populate 2x3 affine matrix data
        affineData[0] = a
        affineData[1] = b
        affineData[2] = tx
        affineData[3] = -b
        affineData[4] = a
        affineData[5] = ty

        affineMat.put(0, 0, affineData)

        // 4. Warp Affine to 112x112
        // LOCAL targetSize: Size 112x112 ArcFace | file: FaceAligner.kt > alignFace > warp
        val targetSize = Size(AppConfig.RECOG_INPUT_SIZE.toDouble(), AppConfig.RECOG_INPUT_SIZE.toDouble())
        Imgproc.warpAffine(srcFrameMat, dstAlignedMat, affineMat, targetSize)
    }
}
