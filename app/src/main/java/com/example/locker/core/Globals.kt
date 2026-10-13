package com.example.locker.core

import android.graphics.RectF
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Trạng thái vòng đời phiên quét khuôn mặt và nhận diện.
 */
enum class ScanState {
    IDLE,          // Sẵn sàng quét
    DETECTING,     // Đang phát hiện khuôn mặt
    STABILIZING,   // Đang xác nhận khuôn mặt ổn định
    RECOGNIZING,   // Đang trích xuất vector & so khớp danh tính
    REGISTERING,   // Đang giao dịch đăng ký tủ (chống ghi trùng)
    SUCCESS,       // Hoàn tất thành công (Đăng ký/Mở tủ/Trả tủ)
    TIMEOUT,       // Hết thời gian quét
    ERROR          // Xảy ra lỗi
}

/**
 * Tap hop cac bien Global dung chung trong toan bo chuong trinh.
 * Tat ca bien Deu duoc khai bao thread-safe va ghi chu KDoc chi tiet theo quy dinh.
 */
object Globals {

    /**
     * Tên: scanState
     * Kiểu: AtomicReference<ScanState>
     * Mục đích: Quản lý trạng thái vòng đời phiên quét thread-safe
     */
    val scanState: AtomicReference<ScanState> = AtomicReference(ScanState.IDLE)

    /**
     * Tên: currentRawBbox
     * Kiểu: AtomicReference<RectF?>
     * Mục đích: Luu bounding box hien tai cua khuon mat tren toan frame camera
     * Module ghi: detect (YuNetDetector)
     * Module đọc: ui (FaceOverlay), flow (ScanController)
     * Luồng truy cập: Luong DETECT -> Luong UI / RECOG
     * Giá trị mặc định: null
     */
    val currentRawBbox: AtomicReference<RectF?> = AtomicReference(null)

    /**
     * Tên: currentLandmarks
     * Kiểu: AtomicReference<FloatArray?>
     * Mục đích: Luu mảng 10 phan tu x,y cua 5 landmark khuon mat (mat phai, mat trai, mui, mieng phai, mieng trai)
     * Module ghi: detect (YuNetDetector)
     * Module đọc: ui (FaceOverlay), recog (FaceAligner)
     * Luồng truy cập: Luong DETECT -> Luong UI / RECOG
     * Giá trị mặc định: null
     */
    val currentLandmarks: AtomicReference<FloatArray?> = AtomicReference(null)

    /**
     * Tên: isFaceValid
     * Kiểu: @Volatile var Boolean
     * Mục đích: Co bao hieu khuon mat hien tai co hop le va nam hoan toan trong ROI hay khong
     * Module ghi: detect (FaceQualityChecker)
     * Module đọc: ui (FaceOverlay), flow (ScanController), recog
     * Luồng truy cập: Luong DETECT -> Luong UI / RECOG / FLOW
     * Giá trị mặc định: false
     */
    @Volatile
    var isFaceValid: Boolean = false

    /**
     * Tên: faceQualityScore
     * Kiểu: @Volatile var Float
     * Mục đích: Diem so phat hien mat tu YuNet phuc vu kiem tra
     * Module ghi: detect (YuNetDetector)
     * Module đọc: detect (FaceQualityChecker), ui (FaceOverlay)
     * Luồng truy cập: Luong DETECT -> Luong UI
     * Giá trị mặc định: 0.0f
     */
    @Volatile
    var faceQualityScore: Float = 0.0f

    /**
     * Tên: stableFrameCounter
     * Kiểu: AtomicInteger
     * Mục đích: Đem so frame lien tiep ma khuon mat hop le va nam trong ROI
     * Module ghi: detect (FaceQualityChecker)
     * Module đọc: flow (ScanController), ui (FaceOverlay)
     * Luồng truy cập: Luong DETECT -> Luong FLOW / UI
     * Giá trị mặc định: 0
     */
    val stableFrameCounter: AtomicInteger = AtomicInteger(0)

    /**
     * Tên: currentGuideText
     * Kiểu: @Volatile var String
     * Mục đích: Chuoi huong dan nguoi dung ("Dua mat vao khung", "Lai gan hon", "Giu yen", ...)
     * Module ghi: detect (FaceQualityChecker), flow (ScanController)
     * Module đọc: ui (ScanScreen, FaceOverlay)
     * Luồng truy cập: Luong DETECT / FLOW -> Luong UI
     * Giá trị mặc định: "Đưa mặt vào khung"
     */
    @Volatile
    var currentGuideText: String = "Đưa mặt vào khung"

    /**
     * Tên: cameraFps
     * Kiểu: @Volatile var Float
     * Mục đích: Toc do frame nhan duoc tu CameraX (FPS)
     * Module ghi: camera (CameraModule)
     * Module đọc: ui (FaceOverlay debug)
     * Luồng truy cập: Luong CAMERA -> Luong UI
     * Giá trị mặc định: 0.0f
     */
    @Volatile
    var cameraFps: Float = 0.0f

    /**
     * Tên: detectFps
     * Kiểu: @Volatile var Float
     * Mục đích: Toc do phat hien va kiem tra mat tu YuNet (FPS)
     * Module ghi: detect (YuNetDetector)
     * Module đọc: ui (FaceOverlay debug)
     * Luồng truy cập: Luong DETECT -> Luong UI
     * Giá trị mặc định: 0.0f
     */
    @Volatile
    var detectFps: Float = 0.0f

    /**
     * Tên: recogTimeMs
     * Kiểu: @Volatile var Long
     * Mục đích: Thoi gian thuc hien 1 lan align + extract embedding MobileFaceNet (ms)
     * Module ghi: recog (MobileFaceNetEmbedder)
     * Module đọc: ui (FaceOverlay debug)
     * Luồng truy cập: Luong RECOG -> Luong UI
     * Giá trị mặc định: 0L
     */
    @Volatile
    var recogTimeMs: Long = 0L

    /**
     * Tên: embeddingsMemoryCache
     * Kiểu: ConcurrentHashMap<Int, FloatArray>
     * Mục đích: Bo dem RAM luu tru embedding 512 chieu cua cac tu da dang ky (lockerId -> FloatArray)
     * Module ghi: data (LockerRepository), flow (ScanController)
     * Module đọc: recog (FaceMatcher)
     * Luồng truy cập: Luong IO / RECOG / FLOW
     * Giá trị mặc định: Map rong
     */
    val embeddingsMemoryCache: ConcurrentHashMap<Int, FloatArray> = ConcurrentHashMap()

    /**
     * Tên: activeFrameWidth
     * Kiểu: @Volatile var Int
     * Mục đích: Chieu rong hien tai cua frame anh phan tich (camera ImageAnalysis)
     * Module ghi: camera (CameraModule)
     * Module đọc: detect (RoiMapper), ui (FaceOverlay)
     * Luồng truy cập: Luong CAMERA -> Luong DETECT / UI
     * Giá trị mặc định: 640
     */
    @Volatile
    var activeFrameWidth: Int = 640

    /**
     * Tên: activeFrameHeight
     * Kiểu: @Volatile var Int
     * Mục đích: Chieu cao hien tai cua frame anh phan tich (camera ImageAnalysis)
     * Module ghi: camera (CameraModule)
     * Module đọc: detect (RoiMapper), ui (FaceOverlay)
     * Luồng truy cập: Luong CAMERA -> Luong DETECT / UI
     * Giá trị mặc định: 480
     */
    @Volatile
    var activeFrameHeight: Int = 480

    /**
     * Tên: cameraRotationDegrees
     * Kiểu: @Volatile var Int
     * Mục đích: Goc xoay camera tu ImageProxy (0, 90, 180, 270)
     * Module ghi: camera (CameraModule)
     * Module đọc: detect (YuNetDetector)
     * Luồng truy cập: Luong CAMERA -> Luong DETECT
     * Giá trị mặc định: 0
     */
    @Volatile
    var cameraRotationDegrees: Int = 0

    /**
     * Tên: isFlipVertical
     * Kiểu: @Volatile var Boolean
     * Mục đích: Co dao nguoc anh 180 do khi camera webcam bi nguoc dau
     * Module ghi: ui (ScanScreen / CameraModule)
     * Module đọc: detect (YuNetDetector), ui (FaceOverlay)
     * Luồng truy cập: Luong CAMERA / DETECT / UI
     * Giá trị mặc định: false
     */
    @Volatile
    var isFlipVertical: Boolean = false
}
