package com.example.locker.core

import android.graphics.RectF

/**
 * Tap hop hang so cau hinh toan he thong Tu Khoa Locker Face Recognition.
 */
object AppConfig {
    /** Gioi han phan cung thuc te toi da dieu khien duoc (ESP32/PCF8575 relay board: 60 tu) */
    const val MAX_HARDWARE_LOCKERS: Int = 60

    /** Tong so tu khoa mac dinh khoi tao khi chua co cau hinh */
    const val TOTAL_LOCKERS: Int = 60

    /** Ma PIN mac dinh cho Admin */
    const val DEFAULT_ADMIN_PIN: String = "888888"

    /** Ma dinh danh thiet bi Kiosk */
    const val DEFAULT_DEVICE_ID: String = "LOCKER-KIOSK-01"

    /** So frame lien tiếp can mat nam hoan toan trong ROI va dat chat luong de xac nhan on dinh */
    const val STABLE_FRAMES: Int = 8

    /** So luong sample embedding chat luong tot thu thuat de lay trung binh khi dang ky mat */
    const val REGISTER_SAMPLES: Int = 5

    /** Nguong Cosine Similarity de xac nhan khop mat (MobileFaceNet ArcFace 512D) */
    const val MATCH_THRESHOLD: Float = 0.40f

    /** Nguong chech lech Cosine giua hang 1 va hang 2 de tranh nham lan */
    const val MATCH_MARGIN: Float = 0.02f

    /** So frame lien tiep khop cung 1 tu de xac nhan mo tu */
    const val MATCH_CONSECUTIVE: Int = 2

    /** Nguong diem so phat hien mat tu YuNet (chuan OpenCV Zoo: 0.6f) */
    const val DETECT_SCORE_MIN: Float = 0.6f

    /** Ty le chieu rong mat nho nhat so voi chieu rong ROI */
    const val FACE_MIN_RATIO: Float = 0.25f

    /** Ty le chieu rong mat lon nhat so voi chieu rong ROI */
    const val FACE_MAX_RATIO: Float = 0.95f

    /** Nguong goc ngoai yaw toi da (x_nose - x_midpoint_eyes) / dist_eyes */
    const val YAW_MAX: Float = 0.35f

    /** Nguong do net nho nhat (phuong sai Laplacian cua anh face aligned) */
    const val BLUR_MIN: Double = 20.0

    /** Khoang thoi gian toi thieu giua 2 lan chay Nhan dien (ms) */
    const val RECOG_INTERVAL_MS: Long = 120L

    /** Thoi gian timeout khi khong co mat trong man hinh quet (ms) */
    const val NO_FACE_TIMEOUT_MS: Long = 15000L

    /** Thoi gian timeout khi dang quet xac minh mo/tra tu (ms) */
    const val VERIFY_TIMEOUT_MS: Long = 10000L

    /** Thoi gian hien thi man hinh ket qua truoc khi ve Trang chu (0ms: dieu huong ngay khi hoan tat) */
    const val RESULT_SHOW_MS: Long = 0L

    /** Chieu rong input cho model YuNet Detection (boi so cua 32) */
    const val DETECT_INPUT_W: Int = 256

    /** Chieu cao input cho model YuNet Detection (boi so cua 32) */
    const val DETECT_INPUT_H: Int = 320

    /** Kich thuoc input cho model MobileFaceNet (112x112) */
    const val RECOG_INPUT_SIZE: Int = 112

    /** Kich thuoc embedding vector san ra tu MobileFaceNet */
    const val EMBEDDING_SIZE: Int = 512

    /** Tỷ lệ chuẩn hóa vùng ROI quét vẽ trên overlay chiều dọc (left, top, right, bottom) */
    val SCAN_ROI_NORM: RectF = RectF(0.12f, 0.18f, 0.88f, 0.65f)

    /** Ten file model YuNet trong assets */
    const val YUNET_MODEL_FILE: String = "models/face_detection_yunet_2023mar.onnx"

    /** Ten file model MobileFaceNet trong assets */
    const val MOBILEFACENET_MODEL_FILE: String = "models/w600k_mbf.onnx"
}
