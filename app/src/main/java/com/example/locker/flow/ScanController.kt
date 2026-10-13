package com.example.locker.flow

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.locker.camera.FrameSlot
import com.example.locker.core.AppConfig
import com.example.locker.core.Globals
import com.example.locker.core.ScanState
import com.example.locker.core.ThreadManager
import com.example.locker.data.LockerRepository
import com.example.locker.detect.BlinkLivenessDetector
import com.example.locker.detect.BlinkState
import com.example.locker.hardware.FakeLockerController
import com.example.locker.hardware.LockerController
import com.example.locker.recog.FaceAligner
import com.example.locker.recog.FaceMatcher
import com.example.locker.recog.MobileFaceNetEmbedder
import com.example.locker.ui.ScanMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.opencv.core.CvType
import org.opencv.core.Mat

/**
 * State Machine dieu phoi toan bo luong quet, nhan dien va mo tu khoa.
 */
class ScanController(
    private val context: Context,
    private val scanMode: ScanMode,
    private val frameSlot: FrameSlot,
    private val onFinish: () -> Unit
) {
    private val repository = LockerRepository(context)

    private val lockerController: LockerController = FakeLockerController()

    private val embedder = MobileFaceNetEmbedder(context)

    private val blinkDetector = BlinkLivenessDetector()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val mainHandler = Handler(Looper.getMainLooper())

    // Liveness tracking
    @Volatile
    private var isLivenessPassed = false

    // Active session tracking
    @Volatile
    private var activeSessionId = 0L

    @Volatile
    private var isRunning = false

    @Volatile
    private var isRegistrationProcessing = false

    @Volatile
    private var lastRecogTimestamp = 0L

    // Pre-allocated Mats for Alignment
    private val alignedMat = Mat(AppConfig.RECOG_INPUT_SIZE, AppConfig.RECOG_INPUT_SIZE, CvType.CV_8UC3)
    private val tempEmbedding = FloatArray(AppConfig.EMBEDDING_SIZE)

    // Registration samples accumulator
    private val collectedSamples = mutableListOf<FloatArray>()

    // Matching consecutive counter
    private var lastCandidateLockerId = -1
    private var consecutiveMatchCount = 0
    private var unmatchedCount = 0

    // Timeout tracker
    private var scanStartTime = 0L

    /**
     * Bat dau phien quet moi.
     */
    fun startScan() {
        activeSessionId = System.currentTimeMillis()
        isRunning = true
        isRegistrationProcessing = false
        scanStartTime = System.currentTimeMillis()
        collectedSamples.clear()
        consecutiveMatchCount = 0
        lastCandidateLockerId = -1
        unmatchedCount = 0

        blinkDetector.reset()
        isLivenessPassed = (scanMode == ScanMode.RETURN) // Return mode bypasses liveness blink check

        Globals.stableFrameCounter.set(0)
        Globals.isFaceValid = false
        Globals.currentRawBbox.set(null)
        Globals.currentLandmarks.set(null)
        Globals.scanState.set(ScanState.IDLE)

        Log.i("SMART_LOCKER_DEBUG", "[SCAN_SESSION_START] Session $activeSessionId started. Mode: $scanMode, State reset to IDLE")

        // Preload memory cache from Room DB
        scope.launch {
            repository.loadAllLockersIntoCache()
        }

        // Start background processing loop on recogExecutor
        val sessionId = activeSessionId
        ThreadManager.recogExecutor.execute {
            runScanLoop(sessionId)
        }
    }

    /**
     * Xu ly frame bitmap qua ML Kit cho chop mat.
     */
    fun processBlinkFrame(bitmap: Bitmap, sessionId: Long) {
        if (activeSessionId != sessionId || isLivenessPassed) return
        if (scanMode == ScanMode.RETURN) {
            isLivenessPassed = true
            return
        }

        blinkDetector.processFrameBitmap(
            bitmap = bitmap,
            onStateChanged = { _, guideMsg ->
                if (activeSessionId == sessionId && !isLivenessPassed) {
                    Globals.currentGuideText = guideMsg
                }
            },
            onPassed = {
                if (activeSessionId == sessionId && !isLivenessPassed) {
                    isLivenessPassed = true
                    Log.i("SMART_LOCKER_DEBUG", "[LIVENESS_PASS] Session $sessionId: Blink liveness passed successfully!")
                    Globals.currentGuideText = "Đã xác nhận chớp mắt người thật!"
                }
            }
        )
    }

    /**
     * Thu lai phien quet moi khi timeout hoac nguoi dung bam "THỬ LẠI".
     */
    fun retryScan() {
        Log.i("SMART_LOCKER_DEBUG", "[ScanFlow] [RETRY] Retrying scan session...")
        stopScan()
        startScan()
    }

    /**
     * Dung phien quet va giai phong tai nguyen.
     */
    fun stopScan() {
        Log.i("SMART_LOCKER_DEBUG", "[SCAN_SESSION_END] Session $activeSessionId ended")
        isRunning = false
        isRegistrationProcessing = false
        activeSessionId = 0L
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun runScanLoop(sessionId: Long) {
        while (isRunning && activeSessionId == sessionId) {
            try {
                val now = System.currentTimeMillis()
                val currentState = Globals.scanState.get()

                // If already completed or in terminal/processing state, pause loop
                if (currentState == ScanState.REGISTERING ||
                    currentState == ScanState.SUCCESS ||
                    currentState == ScanState.TIMEOUT ||
                    currentState == ScanState.ERROR) {
                    Thread.sleep(100)
                    continue
                }

                // Check Timeout when no face detected or no success
                if (!Globals.isFaceValid) {
                    val noFaceTimeout = if (scanMode == ScanMode.REGISTER) AppConfig.NO_FACE_TIMEOUT_MS else AppConfig.VERIFY_TIMEOUT_MS
                    if (now - scanStartTime >= noFaceTimeout) {
                        if (activeSessionId == sessionId) {
                            finishWithResult(sessionId, "Hết thời gian quét. Vui lòng bấm 'THỬ LẠI' để quét lại.", isSuccess = false, isTimeout = true)
                            Log.w(TAG, "[ScanFlow] Scan session timed out ($noFaceTimeout ms)")
                        }
                        break
                    }
                    Thread.sleep(50)
                    continue
                }

                // --- LIVENESS BLINK CHECK STEP (REGISTER & OPEN_TEMP) ---
                if (!isLivenessPassed && (scanMode == ScanMode.REGISTER || scanMode == ScanMode.OPEN_TEMP)) {
                    val verifyTimeout = AppConfig.VERIFY_TIMEOUT_MS
                    if (now - scanStartTime >= verifyTimeout) {
                        Log.w("SMART_LOCKER_DEBUG", "[LIVENESS_FAIL] Session $sessionId: Blink liveness check timed out")
                        finishWithResult(sessionId, "Xác minh chớp mắt thất bại (Hết thời gian).\nVui lòng bấm 'THỬ LẠI' để quét lại.", isSuccess = false, isTimeout = true)
                        break
                    }
                    Thread.sleep(50)
                    continue
                }

                // Face is valid, stable in >= 70% ROI, and Liveness Passed! Check interval
                if (now - lastRecogTimestamp < AppConfig.RECOG_INTERVAL_MS) {
                    Thread.sleep(20)
                    continue
                }

                Globals.currentGuideText = "Đã xác nhận người thật - Đang xử lý..."
                Log.i("SMART_LOCKER_DEBUG", "[IDENTITY_SCAN_START] Session $sessionId: Starting MobileFaceNet 512D feature extraction")

                // Atomic state transition: STABILIZING/DETECTING -> RECOGNIZING
                val transitioned = Globals.scanState.compareAndSet(ScanState.STABILIZING, ScanState.RECOGNIZING) ||
                        Globals.scanState.compareAndSet(ScanState.DETECTING, ScanState.RECOGNIZING) ||
                        Globals.scanState.compareAndSet(ScanState.IDLE, ScanState.RECOGNIZING)

                if (!transitioned && currentState != ScanState.RECOGNIZING) {
                    Thread.sleep(20)
                    continue
                }

                lastRecogTimestamp = now

                val landmarks = Globals.currentLandmarks.get() ?: run {
                    Globals.scanState.set(ScanState.DETECTING)
                    continue
                }

                val w = Globals.activeFrameWidth
                val h = Globals.activeFrameHeight

                if (w <= 0 || h <= 0) {
                    Globals.scanState.set(ScanState.DETECTING)
                    continue
                }

                // Copy actual RGBA frame bytes from FrameSlot into frameMat
                val frameBytes = frameSlot.getReadBuffer()
                if (frameBytes.isEmpty()) {
                    Globals.scanState.set(ScanState.DETECTING)
                    continue
                }

                val frameMat = Mat(h, w, CvType.CV_8UC4)
                frameMat.put(0, 0, frameBytes)

                // Perform Umeyama 5-landmark Face Alignment to 112x112 ArcFace format
                FaceAligner.alignFace(frameMat, landmarks, alignedMat)
                frameMat.release()

                // Extract 512D feature embedding vector using MobileFaceNet ONNX Runtime
                val isExtracted = embedder.extractEmbedding(alignedMat, tempEmbedding)
                if (!isExtracted) {
                    Log.w(TAG, "[ScanFlow] Failed to extract embedding from MobileFaceNet")
                    Globals.scanState.set(ScanState.DETECTING)
                    continue
                }

                Log.i(TAG, "[ScanFlow] Extracted 512D embedding. Session: $sessionId, Mode: $scanMode")

                // Process logic based on mode
                when (scanMode) {
                    ScanMode.REGISTER -> handleRegisterStep(sessionId, tempEmbedding.clone())
                    ScanMode.OPEN_TEMP -> handleVerifyStep(sessionId, tempEmbedding, isReturnMode = false)
                    ScanMode.RETURN -> handleVerifyStep(sessionId, tempEmbedding, isReturnMode = true)
                }

            } catch (e: Exception) {
                Log.e(TAG, "[ScanFlow] Error in runScanLoop: ${e.message}", e)
                Globals.scanState.set(ScanState.DETECTING)
            }
        }
    }

    private fun handleRegisterStep(sessionId: Long, sampleEmbedding: FloatArray) {
        if (activeSessionId != sessionId) return
        if (isRegistrationProcessing) {
            Log.i("SMART_LOCKER_DEBUG", "[REGISTRATION_DUPLICATE_BLOCKED] Session $sessionId: Registration already in progress, blocking duplicate frame trigger")
            return
        }

        collectedSamples.add(sampleEmbedding)
        Globals.currentGuideText = "Đang thu thập mẫu (${collectedSamples.size}/${AppConfig.REGISTER_SAMPLES})..."
        Log.i("SMART_LOCKER_DEBUG", "[ScanFlow] Collected sample ${collectedSamples.size}/${AppConfig.REGISTER_SAMPLES} for session $sessionId")

        if (collectedSamples.size >= AppConfig.REGISTER_SAMPLES) {
            // ATOMICALLY GUARD AGAINST DUPLICATE REGISTRATIONS
            if (isRegistrationProcessing) {
                Log.i("SMART_LOCKER_DEBUG", "[REGISTRATION_DUPLICATE_BLOCKED] Session $sessionId: Blocked duplicate registration trigger")
                return
            }
            isRegistrationProcessing = true
            Globals.scanState.set(ScanState.REGISTERING)

            // Compute average embedding
            val avgEmbedding = FloatArray(AppConfig.EMBEDDING_SIZE)
            for (sample in collectedSamples) {
                for (i in 0 until AppConfig.EMBEDDING_SIZE) {
                    avgEmbedding[i] += sample[i]
                }
            }

            // L2 normalize average embedding
            var normSq = 0.0f
            for (i in 0 until AppConfig.EMBEDDING_SIZE) {
                avgEmbedding[i] /= collectedSamples.size.toFloat()
                normSq += avgEmbedding[i] * avgEmbedding[i]
            }
            val norm = Math.sqrt(normSq.toDouble()).toFloat()
            if (norm > 0.00001f) {
                for (i in 0 until AppConfig.EMBEDDING_SIZE) {
                    avgEmbedding[i] /= norm
                }
            }

            // Duplicate face check
            val matchResult = FaceMatcher.findBestMatch(avgEmbedding)
            if (matchResult.isMatch) {
                isRegistrationProcessing = false
                Log.w("SMART_LOCKER_DEBUG", "[REGISTRATION_FAILED] Session $sessionId: Face already registered to locker ${matchResult.matchedLockerId}")
                finishWithResult(sessionId, "Bạn đã có tủ số ${matchResult.matchedLockerId}", isSuccess = false)

                return
            }

            Log.i("SMART_LOCKER_DEBUG", "[REGISTRATION_REQUEST] Session $sessionId: Requesting atomic locker allocation")
            Log.i("SMART_LOCKER_DEBUG", "[REGISTRATION_START] Session $sessionId: Starting registration transaction")

            // Atomic reservation from 60 lockers in Room DB
            scope.launch {
                if (activeSessionId != sessionId) {
                    isRegistrationProcessing = false
                    return@launch
                }

                try {
                    val assignedLockerId = repository.registerNewLockerAtomic(avgEmbedding)

                    if (assignedLockerId != null) {
                        Log.i("SMART_LOCKER_DEBUG", "[LOCKER_ALLOCATED] Session $sessionId: Locker $assignedLockerId allocated in Room DB")
                        Log.i("SMART_LOCKER_DEBUG", "[DATABASE_COMMIT] Session $sessionId: Room DB & Session History committed")

                        Log.i("SMART_LOCKER_DEBUG", "[LOCKER_OPEN_START] Session $sessionId: Issuing hardware open command for locker $assignedLockerId")
                        val isOpenOk = lockerController.open(assignedLockerId)
                        if (isOpenOk) {
                            Log.i("SMART_LOCKER_DEBUG", "[REGISTRATION_SUCCESS] Session $sessionId: Locker $assignedLockerId registered & hardware relay opened")
                            val successMessage = "Đăng ký tủ thành công!\n\nBạn đã được cấp tủ số $assignedLockerId.\nVui lòng ghi nhớ số tủ của bạn."
                            finishWithResult(sessionId, successMessage, isSuccess = true)
                        } else {
                            repository.releaseLocker(assignedLockerId)
                            isRegistrationProcessing = false
                            Log.e("SMART_LOCKER_DEBUG", "[REGISTRATION_FAILED] Session $sessionId: Hardware relay error for locker $assignedLockerId")
                            finishWithResult(sessionId, "Lỗi kết nối tủ khóa số $assignedLockerId. Vui lòng thử lại.", isSuccess = false)
                        }
                    } else {
                        isRegistrationProcessing = false
                        Log.e("SMART_LOCKER_DEBUG", "[REGISTRATION_FAILED] Session $sessionId: System full or DB failed")
                        finishWithResult(sessionId, "Hệ thống đã hết tủ trống. Vui lòng thử lại sau.", isSuccess = false)
                    }
                } catch (e: Exception) {
                    isRegistrationProcessing = false
                    Log.e("SMART_LOCKER_DEBUG", "[REGISTRATION_FAILED] Session $sessionId: Exception during registration: ${e.message}", e)
                    finishWithResult(sessionId, "Lỗi hệ thống khi đăng ký. Vui lòng thử lại.", isSuccess = false)
                }
            }
        } else {
            // Allow collecting next sample
            Globals.scanState.set(ScanState.DETECTING)
        }
    }

    private fun handleVerifyStep(sessionId: Long, currentEmbedding: FloatArray, isReturnMode: Boolean) {
        if (activeSessionId != sessionId) return

        val matchResult = FaceMatcher.findBestMatch(currentEmbedding)
        Log.i("SMART_LOCKER_DEBUG", "[IDENTITY_MATCH_RESULT] Session $sessionId: isMatch=${matchResult.isMatch}, matchedLockerId=${matchResult.matchedLockerId}, bestScore=%.3f".format(matchResult.bestScore))

        if (matchResult.isMatch) {
            val lockerId = matchResult.matchedLockerId
            unmatchedCount = 0
            Log.i("SMART_LOCKER_DEBUG", "[FACE_MATCH_SUCCESS] Session $sessionId: Face matched locker $lockerId with score %.3f".format(matchResult.bestScore))

            if (lockerId == lastCandidateLockerId) {
                consecutiveMatchCount++
            } else {
                lastCandidateLockerId = lockerId
                consecutiveMatchCount = 1
            }

            Globals.currentGuideText = "Xác nhận danh tính tủ số $lockerId ($consecutiveMatchCount/${AppConfig.MATCH_CONSECUTIVE})..."

            if (consecutiveMatchCount >= AppConfig.MATCH_CONSECUTIVE) {
                scope.launch {
                    if (activeSessionId != sessionId) return@launch

                    Log.i("SMART_LOCKER_DEBUG", "[LOCKER_OPEN_START] Session $sessionId: Issuing hardware open command for locker $lockerId")
                    val isOpenOk = lockerController.open(lockerId)

                    if (isOpenOk) {
                        if (isReturnMode) {
                            // Release locker ONLY after hardware open succeeds
                            repository.releaseLocker(lockerId)
                            Log.i("SMART_LOCKER_DEBUG", "[ScanFlow] Returned and released locker $lockerId successfully")
                            finishWithResult(sessionId, "Đã trả tủ số $lockerId thành công!\nTủ đã được giải phóng.", isSuccess = true)
                        } else {
                            Log.i("SMART_LOCKER_DEBUG", "[ScanFlow] Opened temp locker $lockerId successfully")
                            finishWithResult(sessionId, "Tủ số $lockerId đã mở!\nVui lòng lấy/gửi đồ.", isSuccess = true)
                        }
                    } else {
                        Log.e("SMART_LOCKER_DEBUG", "[ScanFlow] Hardware relay error opening locker $lockerId")
                        finishWithResult(sessionId, "Lỗi kết nối tủ khóa số $lockerId. Vui lòng thử lại.", isSuccess = false)
                    }
                }
            } else {
                Globals.scanState.set(ScanState.DETECTING)
            }
        } else {
            consecutiveMatchCount = 0
            lastCandidateLockerId = -1
            unmatchedCount++

            Log.d("SMART_LOCKER_DEBUG", "[ScanFlow] Unrecognized face attempt $unmatchedCount. Best score: %.3f < threshold %.2f".format(matchResult.bestScore, AppConfig.MATCH_THRESHOLD))

            if (Globals.embeddingsMemoryCache.isEmpty()) {
                finishWithResult(sessionId, "Hệ thống chưa có tủ nào được đăng ký.\n\nVui lòng đăng ký tủ trước.", isSuccess = false)
            } else {
                Globals.currentGuideText = "Đang xác nhận danh tính... (Hãy nhìn thẳng vào camera)"
                Globals.scanState.set(ScanState.DETECTING)
            }
        }
    }

    private fun finishWithResult(
        sessionId: Long,
        resultText: String,
        isSuccess: Boolean,
        isTimeout: Boolean = false
    ) {
        if (activeSessionId != sessionId) return

        isRunning = false
        Globals.currentGuideText = resultText

        if (isSuccess) {
            Globals.scanState.set(ScanState.SUCCESS)
        } else if (isTimeout) {
            Globals.scanState.set(ScanState.TIMEOUT)
        } else {
            Globals.scanState.set(ScanState.ERROR)
        }

        Log.i("SMART_LOCKER_DEBUG", "[ScanFlow] Session $sessionId finished. Success: $isSuccess, Timeout: $isTimeout. Message: $resultText")

        if (isSuccess) {
            Log.i("SMART_LOCKER_DEBUG", "[LOCKER_SUCCESS] Session $sessionId completed successfully. Navigating to Home immediately.")

            stopScan()
            onFinish()
        }
    }

    /**
     * Lay Session ID hien tai.
     */
    fun getActiveSessionId(): Long = activeSessionId

    companion object {
        private const val TAG = "ScanController"
    }
}



