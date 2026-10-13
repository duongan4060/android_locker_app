package com.example.locker.ui

import android.graphics.RectF
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.locker.BuildConfig
import com.example.locker.core.AppConfig
import com.example.locker.core.Globals
import com.example.locker.detect.RoiMapper

/**
 * Canvas ve Khung ROI, Bounding Box theo dung khuon mat di chuyen, Landmark va Debug FPS.
 */
@Composable
fun FaceOverlay(
    frameTrigger: Long = 0L,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        // Suppress unused parameter warning since frameTrigger forces recomposition
        @Suppress("UNUSED_EXPRESSION")
        frameTrigger

        val canvasW = size.width
        val canvasH = size.height

        val frameW = Globals.activeFrameWidth.toFloat()
        val frameH = Globals.activeFrameHeight.toFloat()

        if (frameW <= 0f || frameH <= 0f) {
            Log.d(TAG, "[FaceOverlay] Frame dimensions invalid ($frameW x $frameH), skipping draw")
            return@Canvas
        }

        // Fill_Center (Center Crop) scale & offset mapping to align overlay with PreviewView
        val scale = maxOf(canvasW / frameW, canvasH / frameH)
        val scaledW = frameW * scale
        val scaledH = frameH * scale
        val offsetX = (canvasW - scaledW) / 2f
        val offsetY = (canvasH - scaledH) / 2f

        // 1. Draw Target Scan ROI Frame in Center
        val normRoi = AppConfig.SCAN_ROI_NORM
        val roiLeft = normRoi.left * canvasW
        val roiTop = normRoi.top * canvasH
        val roiRight = normRoi.right * canvasW
        val roiBottom = normRoi.bottom * canvasH

        val roiPixelRect = RectF(
            normRoi.left * frameW,
            normRoi.top * frameH,
            normRoi.right * frameW,
            normRoi.bottom * frameH
        )

        val roiColor = if (Globals.isFaceValid) Color(0xFF10B981) else Color(0xFFEF4444)

        // Draw Center Target ROI Zone
        drawRoundRect(
            color = roiColor.copy(alpha = 0.6f),
            topLeft = Offset(roiLeft, roiTop),
            size = Size(roiRight - roiLeft, roiBottom - roiTop),
            cornerRadius = CornerRadius(36.dp.toPx(), 36.dp.toPx()),
            style = Stroke(width = 4.dp.toPx())
        )

        // 2. Draw Tracking Face Box fitted to Face position (follows face as it moves)
        val rawBbox = Globals.currentRawBbox.get()
        val rawLandmarks = Globals.currentLandmarks.get()

        if (rawBbox != null) {
            val isInsideRoi = roiPixelRect.contains(rawBbox)

            // Mirror coordinates for front camera preview
            val mirroredBbox = RoiMapper.mirrorBboxForPreview(rawBbox, frameW.toInt())

            // Map to View Canvas coordinates considering FILL_CENTER scale and offset
            val bboxLeft = mirroredBbox.left * scale + offsetX
            val bboxTop = mirroredBbox.top * scale + offsetY
            val bboxWidth = mirroredBbox.width() * scale
            val bboxHeight = mirroredBbox.height() * scale

            // Green color when face is detected successfully
            val faceBoxColor = if (Globals.isFaceValid) {
                Color(0xFF10B981) // Vibrant Green when valid in ROI
            } else if (isInsideRoi) {
                Color(0xFF22C55E) // Green when detected inside ROI
            } else {
                Color(0xFF86EFAC) // Light Green when detected outside ROI
            }

            // Draw green bounding box around the detected face
            drawRoundRect(
                color = faceBoxColor,
                topLeft = Offset(bboxLeft, bboxTop),
                size = Size(bboxWidth, bboxHeight),
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                style = Stroke(width = 3.5f.dp.toPx())
            )

            // Draw 4 Corner Accents around the green face box
            val cornerLen = minOf(bboxWidth, bboxHeight) * 0.22f

            // Top-Left Corner
            val topLeftPath = Path().apply {
                moveTo(bboxLeft, bboxTop + cornerLen)
                lineTo(bboxLeft, bboxTop)
                lineTo(bboxLeft + cornerLen, bboxTop)
            }
            drawPath(topLeftPath, color = faceBoxColor, style = Stroke(width = 5.dp.toPx()))

            // Top-Right Corner
            val topRightPath = Path().apply {
                moveTo(bboxLeft + bboxWidth - cornerLen, bboxTop)
                lineTo(bboxLeft + bboxWidth, bboxTop)
                lineTo(bboxLeft + bboxWidth, bboxTop + cornerLen)
            }
            drawPath(topRightPath, color = faceBoxColor, style = Stroke(width = 5.dp.toPx()))

            // Bottom-Left Corner
            val bottomLeftPath = Path().apply {
                moveTo(bboxLeft, bboxTop + bboxHeight - cornerLen)
                lineTo(bboxLeft, bboxTop + bboxHeight)
                lineTo(bboxLeft + cornerLen, bboxTop + bboxHeight)
            }
            drawPath(bottomLeftPath, color = faceBoxColor, style = Stroke(width = 5.dp.toPx()))

            // Bottom-Right Corner
            val bottomRightPath = Path().apply {
                moveTo(bboxLeft + bboxWidth - cornerLen, bboxTop + bboxHeight)
                lineTo(bboxLeft + bboxWidth, bboxTop + bboxHeight)
                lineTo(bboxLeft + bboxWidth, bboxTop + bboxHeight - cornerLen)
            }
            drawPath(bottomRightPath, color = faceBoxColor, style = Stroke(width = 5.dp.toPx()))

            // Draw 5 Landmarks tracking face features (Eyes, Nose, Mouth)
            if (rawLandmarks != null && rawLandmarks.size >= 10) {
                val mirroredLandmarks = FloatArray(10)
                RoiMapper.mirrorLandmarksForPreview(rawLandmarks, frameW.toInt(), mirroredLandmarks)

                // Distinct colors for each feature to verify landmark orientation:
                // 0: Right Eye (Yellow), 1: Left Eye (Green), 2: Nose (Cyan), 3: Right Mouth (Orange), 4: Left Mouth (Magenta)
                val landmarkColors = listOf(
                    Color.Yellow,
                    Color.Green,
                    Color.Cyan,
                    Color(0xFFF97316), // Orange
                    Color.Magenta
                )

                for (i in 0 until 5) {
                    val lmX = mirroredLandmarks[i * 2] * scale + offsetX
                    val lmY = mirroredLandmarks[i * 2 + 1] * scale + offsetY

                    drawCircle(
                        color = landmarkColors[i % landmarkColors.size],
                        radius = 4.5f.dp.toPx(),
                        center = Offset(lmX, lmY)
                    )
                }
            }
            Log.d(TAG, "[FaceOverlay] Drew green face box at ($bboxLeft, $bboxTop, $bboxWidth x $bboxHeight) on canvas ($canvasW x $canvasH)")
        } else {
            Log.d(TAG, "[FaceOverlay] No face bbox to draw, cleared overlay")
        }

        // 3. Draw Debug Metrics overlay if BuildConfig.DEBUG is true
        if (BuildConfig.DEBUG) {
            val debugText = "CamFPS: %.1f | DetFPS: %.1f | Recog: %d ms | Stable: %d/%d".format(
                Globals.cameraFps,
                Globals.detectFps,
                Globals.recogTimeMs,
                Globals.stableFrameCounter.get(),
                AppConfig.STABLE_FRAMES
            )

            drawText(
                textMeasurer = textMeasurer,
                text = debugText,
                topLeft = Offset(16.dp.toPx(), 16.dp.toPx()),
                style = TextStyle(
                    color = Color.Green,
                    fontSize = 14.sp
                )
            )
        }
    }
}

private const val TAG = "FaceOverlay"

