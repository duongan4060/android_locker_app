package com.example.locker.camera

import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Double buffer slot luu tru Frame camera RGBA khoi lo cap phat bo nho trong chu ky xu ly.
 */
class FrameSlot(
    private val width: Int = 640,
    private val height: Int = 480
) {
    /** Dung luong byte cho 1 frame RGBA_8888 (width * height * 4) */
    val bufferSize: Int = width * height * 4

    // LOCAL bufferA: Bo dem frame thu 1 | Phuc vu double buffering | file: FrameSlot.kt > FrameSlot > init
    val bufferA: ByteArray = ByteArray(bufferSize)

    // LOCAL bufferB: Bo dem frame thu 2 | Phuc vu double buffering | file: FrameSlot.kt > FrameSlot > init
    val bufferB: ByteArray = ByteArray(bufferSize)

    /** Co cho biet bufferA dang duoc dung lam write buffer (true) hay read buffer (false) */
    @Volatile
    private var isWriteBufferA = true

    /** Co thong bao co frame moi chua duoc doc */
    val hasNewFrame = AtomicBoolean(false)

    /** Semaphore thong bao tin hieu sang luong Detect va Recog khong busy-wait */
    val frameSignal = Semaphore(0)

    /**
     * Lay ByteArray hien tai de ghi du lieu tu ImageAnalysis.
     */
    fun getWriteBuffer(): ByteArray {
        return if (isWriteBufferA) bufferA else bufferB
    }

    /**
     * Lay ByteArray hien tai de doc du lieu phan tich.
     */
    fun getReadBuffer(): ByteArray {
        return if (isWriteBufferA) bufferB else bufferA
    }

    /**
     * Hoan doi role Write/Read buffer khi co frame moi va phat tin hieu.
     */
    fun swapAndSignal() {
        isWriteBufferA = !isWriteBufferA
        hasNewFrame.set(true)
        if (frameSignal.availablePermits() < 1) {
            frameSignal.release()
        }
        android.util.Log.d("SMART_LOCKER_DEBUG", "[FrameSlot] Swapped buffer. WriteA: $isWriteBufferA, permits: ${frameSignal.availablePermits()}")
    }
}
