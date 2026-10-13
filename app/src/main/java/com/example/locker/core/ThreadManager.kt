package com.example.locker.core

import android.os.Process
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/**
 * Quan ly va phan bo Luong Thread Pool, CPU Cores cho OpenCV va ONNX Runtime.
 */
object ThreadManager {
    // LOCAL availableProcessors: So loi CPU hien co tren thiet bi | Dung de tinh toan chia thread CPU | file: ThreadManager.kt > init > khoi tao
    val availableProcessors: Int = Runtime.getRuntime().availableProcessors()

    /** So thread phan bo cho OpenCV (max(1, N/4)) */
    val openCvThreads: Int = max(1, availableProcessors / 4)

    /** So thread phan bo cho ONNX Runtime intra-op (max(1, N/2)) */
    val onnxIntraOpThreads: Int = max(1, availableProcessors / 2)

    /** Executor cho luong CAMERA (1 thread high priority) */
    val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor(
        NamedThreadFactory("Locker-Camera-Thread", Process.THREAD_PRIORITY_MORE_FAVORABLE)
    )

    /** Executor cho luong BOUNDING BOX / YUNET DETECT (1 thread high priority) */
    val detectExecutor: ExecutorService = Executors.newSingleThreadExecutor(
        NamedThreadFactory("Locker-Detect-Thread", Process.THREAD_PRIORITY_MORE_FAVORABLE)
    )

    /** Executor cho luong NHAN DIEN / MOBILEFACENET (1 thread normal priority) */
    val recogExecutor: ExecutorService = Executors.newSingleThreadExecutor(
        NamedThreadFactory("Locker-Recog-Thread", Process.THREAD_PRIORITY_DEFAULT)
    )

    /**
     * NamedThreadFactory giup dat ten va priority cho Thread Pool.
     */
    private class NamedThreadFactory(
        private val namePrefix: String,
        private val threadPriority: Int
    ) : ThreadFactory {
        // LOCAL threadNumber: Bo dem so thu tu thread duoc tao | Phuc vu dat ten thread | file: ThreadManager.kt > NamedThreadFactory > newThread
        private val threadNumber = AtomicInteger(1)

        override fun newThread(r: Runnable): Thread {
            // LOCAL t: Thread moi duoc tao ra | Gan ten va priority cho thread | file: ThreadManager.kt > NamedThreadFactory > newThread
            val t = Thread(r, "$namePrefix-${threadNumber.getAndIncrement()}")
            t.isDaemon = true
            t.priority = if (threadPriority == Process.THREAD_PRIORITY_MORE_FAVORABLE) Thread.MAX_PRIORITY - 1 else Thread.NORM_PRIORITY
            return t
        }
    }
}
