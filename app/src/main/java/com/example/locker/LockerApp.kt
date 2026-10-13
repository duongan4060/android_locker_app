package com.example.locker

import android.app.Application
import android.util.Log
import com.example.locker.core.ThreadManager
import com.example.locker.recog.MobileFaceNetEmbedder
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core

/**
 * Custom Application class khoi tao OpenCV, ThreadManager va warm-up model AI.
 */
class LockerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initOpenCV()
        warmUpAiModels()
    }

    private fun initOpenCV() {
        // LOCAL isOpenCvInit: Ket qua khoi tao OpenCV | Kiem tra OpenCV load duoc native library khong | file: LockerApp.kt > initOpenCV > step 1
        val isOpenCvInit = OpenCVLoader.initLocal()
        if (isOpenCvInit) {
            Log.d(TAG, "OpenCV initialized successfully.")
            // LOCAL openCvThreads: So luong thread cap cho OpenCV | Dat numThreads cho Core OpenCV | file: LockerApp.kt > initOpenCV > step 2
            val openCvThreads = ThreadManager.openCvThreads
            Core.setNumThreads(openCvThreads)
            Log.d(TAG, "OpenCV numThreads set to: $openCvThreads")
        } else {
            Log.e(TAG, "OpenCV initialization failed!")
        }
    }

    private fun warmUpAiModels() {
        ThreadManager.recogExecutor.execute {
            try {
                // LOCAL embedder: Instance MobileFaceNetEmbedder cho warm-up | file: LockerApp.kt > warmUpAiModels > step 1
                val embedder = MobileFaceNetEmbedder(applicationContext)
                embedder.warmUp()
                Log.d(TAG, "AI Models warmed up successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Error warming up AI models: ${e.message}", e)
            }
        }
    }
    companion object {
        private const val TAG = "LockerApp"
    }
}
