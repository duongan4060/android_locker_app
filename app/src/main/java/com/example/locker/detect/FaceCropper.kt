package com.example.locker.detect

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import java.nio.ByteBuffer

/**
 * Utility cat vung khuon mat tu Frame RGBA_8888 ra Bitmap phuc vu hien thi UI Preview.
 */
object FaceCropper {
    private const val TAG = "FaceCropper"

    /**
     * Crop anh khuon mat tu frameBytes RGBA_8888 theo rawBbox voi padding an toan.
     *
     * @param frameBytes ByteArray dung luong frameW * frameH * 4 (RGBA_8888)
     * @param frameW Chieu rong frame camera
     * @param frameH Chieu cao frame camera
     * @param rawBbox RectF bounding box khuon mat tren frame
     * @param isMirror True neu muon lat guong anh preview (phu hop camera truoc)
     * @param isFlip180 True neu camera bi xoay nguoc 180 do
     * @return Bitmap anh khuon mat da crop, hoac null neu loi / invalid coords
     */
    fun cropFace(
        frameBytes: ByteArray,
        frameW: Int,
        frameH: Int,
        rawBbox: RectF?,
        isMirror: Boolean = true,
        isFlip180: Boolean = false
    ): Bitmap? {
        if (rawBbox == null || frameBytes.isEmpty() || frameW <= 0 || frameH <= 0) {
            return null
        }

        try {
            val expectedSize = frameW * frameH * 4
            if (frameBytes.size < expectedSize) {
                Log.w(TAG, "[FaceCrop] frameBytes size ${frameBytes.size} < expected $expectedSize")
                return null
            }

            // Expand margin around face (20% horizontal, 25% vertical)
            val marginX = rawBbox.width() * 0.20f
            val marginY = rawBbox.height() * 0.25f

            val cropLeft = (rawBbox.left - marginX).toInt().coerceIn(0, frameW - 1)
            val cropTop = (rawBbox.top - marginY).toInt().coerceIn(0, frameH - 1)
            val cropRight = (rawBbox.right + marginX).toInt().coerceIn(cropLeft + 1, frameW)
            val cropBottom = (rawBbox.bottom + marginY).toInt().coerceIn(cropTop + 1, frameH)

            val cropW = cropRight - cropLeft
            val cropH = cropBottom - cropTop

            if (cropW <= 2 || cropH <= 2) return null

            val cropBytes = ByteArray(cropW * cropH * 4)

            // Copy row by row from RGBA frameBytes
            for (row in 0 until cropH) {
                val srcOffset = ((cropTop + row) * frameW + cropLeft) * 4
                val destOffset = row * cropW * 4
                System.arraycopy(frameBytes, srcOffset, cropBytes, destOffset, cropW * 4)
            }

            val baseBitmap = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
            baseBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(cropBytes))

            // Apply mirror / rotation matrix if needed
            val matrix = Matrix()
            var needMatrix = false

            if (isMirror) {
                matrix.postScale(-1f, 1f)
                needMatrix = true
            }
            if (isFlip180) {
                matrix.postRotate(180f)
                needMatrix = true
            }

            return if (needMatrix) {
                val transformed = Bitmap.createBitmap(baseBitmap, 0, 0, cropW, cropH, matrix, true)
                if (transformed != baseBitmap) {
                    baseBitmap.recycle()
                }
                transformed
            } else {
                baseBitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "[FaceCrop] Error cropping face: ${e.message}", e)
            return null
        }
    }
}
