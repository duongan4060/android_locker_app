package com.example.locker.detect

import android.graphics.Rect
import android.graphics.RectF
import com.example.locker.core.AppConfig

/**
 * Tinh toan quy doi Vung SCAN_ROI_NORM sang Toa do Pixel va Chuyen doi Lay guong (Mirror) Camera Truoc.
 */
object RoiMapper {

    /**
     * Tinh toan vung ROI Pixel tren Anh Frame camera nguyen ban (ImageAnalysis).
     *
     * @param frameWidth Chieu rong frame (vd: 640)
     * @param frameHeight Chieu cao frame (vd: 480)
     * @return Rect pixel cua vùng ROI tren frame
     */
    fun getFramePixelRoi(frameWidth: Int, frameHeight: Int): Rect {
        // LOCAL normRoi: Ty le SCAN_ROI_NORM khai bao hiet thiet | file: RoiMapper.kt > getFramePixelRoi > step 1
        val normRoi = AppConfig.SCAN_ROI_NORM

        // LOCAL left: Toa do pixel trai | file: RoiMapper.kt > getFramePixelRoi > step 2
        val left = (normRoi.left * frameWidth).toInt().coerceIn(0, frameWidth)
        // LOCAL top: Toa do pixel tren | file: RoiMapper.kt > getFramePixelRoi > step 3
        val top = (normRoi.top * frameHeight).toInt().coerceIn(0, frameHeight)
        // LOCAL right: Toa do pixel phai | file: RoiMapper.kt > getFramePixelRoi > step 4
        val right = (normRoi.right * frameWidth).toInt().coerceIn(left, frameWidth)
        // LOCAL bottom: Toa do pixel duoi | file: RoiMapper.kt > getFramePixelRoi > step 5
        val bottom = (normRoi.bottom * frameHeight).toInt().coerceIn(top, frameHeight)

        return Rect(left, top, right, bottom)
    }

    /**
     * Tinh toan vung ROI Pixel duoc mo rong (crop co le nho) phuc vu YuNet phat hien mat khong bi mat cam/tran.
     */
    fun getExpandedFramePixelRoi(frameWidth: Int, frameHeight: Int): Rect {
        // LOCAL normRoi: Ty le SCAN_ROI_NORM chuẩn hóa | file: RoiMapper.kt > getExpandedFramePixelRoi > step 1
        val normRoi = AppConfig.SCAN_ROI_NORM
        // LOCAL marginX: Le mo rong hoanh do (15%) | file: RoiMapper.kt > getExpandedFramePixelRoi > margin
        val marginX = normRoi.width() * 0.15f
        // LOCAL marginY: Le mo rong tung do (15%) | file: RoiMapper.kt > getExpandedFramePixelRoi > margin
        val marginY = normRoi.height() * 0.15f

        // LOCAL expLeft: Trai mo rong | file: RoiMapper.kt > getExpandedFramePixelRoi > coords
        val expLeft = ((normRoi.left - marginX) * frameWidth).toInt().coerceIn(0, frameWidth)
        // LOCAL expTop: Tren mo rong | file: RoiMapper.kt > getExpandedFramePixelRoi > coords
        val expTop = ((normRoi.top - marginY) * frameHeight).toInt().coerceIn(0, frameHeight)
        // LOCAL expRight: Phai mo rong | file: RoiMapper.kt > getExpandedFramePixelRoi > coords
        val expRight = ((normRoi.right + marginX) * frameWidth).toInt().coerceIn(expLeft, frameWidth)
        // LOCAL expBottom: Duoi mo rong | file: RoiMapper.kt > getExpandedFramePixelRoi > coords
        val expBottom = ((normRoi.bottom + marginY) * frameHeight).toInt().coerceIn(expTop, frameHeight)

        return Rect(expLeft, expTop, expRight, expBottom)
    }

    /**
     * Chuyen doi Toa do BoundingBox tu Frame (khong lath guong) sang PreviewView (lat guong camera truoc).
     *
     * @param frameBbox Bounding box nguyen ban tren frame
     * @param frameWidth Chieu rong frame
     * @return BrectF da duoc flip ngang
     */
    fun mirrorBboxForPreview(frameBbox: RectF, frameWidth: Int): RectF {
        // LOCAL mirroredLeft: Toa do trai sau khi lat guong | file: RoiMapper.kt > mirrorBboxForPreview > step 1
        val mirroredLeft = frameWidth - frameBbox.right
        // LOCAL mirroredRight: Toa do phai sau khi lat guong | file: RoiMapper.kt > mirrorBboxForPreview > step 2
        val mirroredRight = frameWidth - frameBbox.left

        return RectF(mirroredLeft, frameBbox.top, mirroredRight, frameBbox.bottom)
    }

    /**
     * Chuyen doi Toa do 5 Landmarks tu Frame (khong lat guong) sang PreviewView (lat guong camera truoc).
     *
     * @param landmarks Mang 10 phan tu (x0, y0, x1, y1, ...)
     * @param frameWidth Chieu rong frame
     * @param outMirroredLandmarks Mang chua ket qua lat guong
     */
    fun mirrorLandmarksForPreview(
        landmarks: FloatArray,
        frameWidth: Int,
        outMirroredLandmarks: FloatArray
    ) {
        if (landmarks.size < 10 || outMirroredLandmarks.size < 10) return

        // YuNet landmarks order: right_eye(0,1), left_eye(2,3), nose(4,5), right_mouth(6,7), left_mouth(8,9)
        // When mirrored horizontally: right eye in frame becomes left eye in mirrored preview.
        for (i in 0 until 5) {
            // LOCAL xIdx: Chi so x trong mang | file: RoiMapper.kt > mirrorLandmarksForPreview > loop
            val xIdx = i * 2
            // LOCAL yIdx: Chi so y trong mang | file: RoiMapper.kt > mirrorLandmarksForPreview > loop
            val yIdx = i * 2 + 1

            outMirroredLandmarks[xIdx] = frameWidth - landmarks[xIdx]
            outMirroredLandmarks[yIdx] = landmarks[yIdx]
        }
    }
}
