package com.example.locker.recog

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.example.locker.core.AppConfig
import com.example.locker.core.Globals
import com.example.locker.core.ThreadManager
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * Tric trich xuat Dac trung Khuon mat (Feature Embedding 512D) tu anh aligned 112x112 dung MobileFaceNet bang ONNX Runtime.
 */
class MobileFaceNetEmbedder(private val context: Context) {

    // LOCAL ortEnv: OrtEnvironment instance | Engine ONNX Runtime | file: MobileFaceNetEmbedder.kt > MobileFaceNetEmbedder > field
    private var ortEnv: OrtEnvironment? = null

    // LOCAL ortSession: OrtSession instance | Session chay model ONNX | file: MobileFaceNetEmbedder.kt > MobileFaceNetEmbedder > field
    private var ortSession: OrtSession? = null

    // Input shape: 1 x 3 x 112 x 112
    private val inputSize = AppConfig.RECOG_INPUT_SIZE
    private val totalPixels = inputSize * inputSize
    private val totalInputs = 1 * 3 * totalPixels

    // Pre-allocated FloatBuffer and FloatArray to avoid memory allocation per inference
    // LOCAL inputFloatBuffer: FloatBuffer phuc vu tao OnnxTensor | file: MobileFaceNetEmbedder.kt > MobileFaceNetEmbedder > preallocated buffers
    private val inputFloatBuffer: FloatBuffer = FloatBuffer.allocate(totalInputs)

    // LOCAL inputFloatArray: FloatArray truyen du lieu | file: MobileFaceNetEmbedder.kt > MobileFaceNetEmbedder > preallocated buffers
    private val inputFloatArray: FloatArray = FloatArray(totalInputs)

    // LOCAL rgbMat: Mat 112x112 RGB phuc vu trich xuat pixel | file: MobileFaceNetEmbedder.kt > MobileFaceNetEmbedder > preallocated buffers
    private val rgbMat: Mat = Mat()

    // LOCAL rawPixels: ByteArray chua pixel anh 112x112 RGB | file: MobileFaceNetEmbedder.kt > MobileFaceNetEmbedder > preallocated buffers
    private val rawPixels: ByteArray = ByteArray(totalPixels * 3)

    init {
        initOnnxModel()
    }

    private fun initOnnxModel() {
        try {
            // LOCAL modelFile: File model w600k_mbf.onnx trong filesDir | file: MobileFaceNetEmbedder.kt > initOnnxModel > step 1
            val modelFile = copyAssetToFilesDir(context, AppConfig.MOBILEFACENET_MODEL_FILE)
            if (!modelFile.exists()) {
                Log.e(TAG, "MobileFaceNet model file not found at ${modelFile.absolutePath}")
                Globals.currentGuideText = "Thiếu model MobileFaceNet (${AppConfig.MOBILEFACENET_MODEL_FILE})"
                return
            }

            ortEnv = OrtEnvironment.getEnvironment()

            // LOCAL sessionOptions: Options cau hinh numThreads | file: MobileFaceNetEmbedder.kt > initOnnxModel > step 2
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(ThreadManager.onnxIntraOpThreads)
                setInterOpNumThreads(1)
            }

            ortSession = ortEnv?.createSession(modelFile.absolutePath, sessionOptions)
            Log.d(TAG, "MobileFaceNet OrtSession initialized successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MobileFaceNet Embedder: ${e.message}", e)
            Globals.currentGuideText = "Lỗi nạp model MobileFaceNet"
        }
    }

    /**
     * Warm-up model ONNX Runtime voi input gia 112x112.
     */
    fun warmUp() {
        val session = ortSession ?: return
        val env = ortEnv ?: return

        try {
            inputFloatBuffer.clear()
            for (i in 0 until totalInputs) {
                inputFloatArray[i] = 0.0f
            }
            inputFloatBuffer.put(inputFloatArray)
            inputFloatBuffer.rewind()

            // LOCAL shape: LongArray shape 1x3x112x112 | file: MobileFaceNetEmbedder.kt > warmUp > step 1
            val shape = longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
            // LOCAL inputTensor: OnnxTensor input gia | file: MobileFaceNetEmbedder.kt > warmUp > step 2
            val inputTensor = OnnxTensor.createTensor(env, inputFloatBuffer, shape)

            // LOCAL inputName: Ten input node ONNX | file: MobileFaceNetEmbedder.kt > warmUp > step 3
            val inputName = session.inputNames.iterator().next()
            session.run(mapOf(inputName to inputTensor)).use {
                Log.d(TAG, "MobileFaceNet warm-up completed.")
            }
            inputTensor.close()
        } catch (e: Exception) {
            Log.e(TAG, "MobileFaceNet warmUp failed: ${e.message}", e)
        }
    }

    /**
     * Trích xuất Embedding Vector 512 chiều từ ảnh 112x112 Aligned.
     *
     * @param alignedMat Mat 112x112 BGR hoac RGBA
     * @param outEmbedding FloatArray 512 phan tu nhan ket qua L2-normalized
     * @return Boolean true neu trich xuat thanh cong
     */
    fun extractEmbedding(alignedMat: Mat, outEmbedding: FloatArray): Boolean {
        val session = ortSession ?: return false
        val env = ortEnv ?: return false

        if (outEmbedding.size < AppConfig.EMBEDDING_SIZE) return false

        val startTime = System.currentTimeMillis()
        Log.d(TAG, "[EMBEDDING_START] Extracting 512D feature embedding...")

        try {
            // Convert to 112x112 RGB
            Imgproc.cvtColor(alignedMat, rgbMat, Imgproc.COLOR_BGR2RGB)
            rgbMat.get(0, 0, rawPixels)

            // Normalize pixels: (x - 127.5) / 127.5 into NCHW format (Channel R, G, B separate)
            for (i in 0 until totalPixels) {
                // LOCAL r: Pixel Red (0..255) | file: MobileFaceNetEmbedder.kt > extractEmbedding > pixel norm
                val r = (rawPixels[i * 3].toInt() and 0xFF)
                // LOCAL g: Pixel Green (0..255) | file: MobileFaceNetEmbedder.kt > extractEmbedding > pixel norm
                val g = (rawPixels[i * 3 + 1].toInt() and 0xFF)
                // LOCAL b: Pixel Blue (0..255) | file: MobileFaceNetEmbedder.kt > extractEmbedding > pixel norm
                val b = (rawPixels[i * 3 + 2].toInt() and 0xFF)

                inputFloatArray[i] = (r - 127.5f) / 127.5f                   // Channel R
                inputFloatArray[totalPixels + i] = (g - 127.5f) / 127.5f     // Channel G
                inputFloatArray[totalPixels * 2 + i] = (b - 127.5f) / 127.5f // Channel B
            }

            inputFloatBuffer.clear()
            inputFloatBuffer.put(inputFloatArray)
            inputFloatBuffer.rewind()

            // LOCAL shape: LongArray shape 1x3x112x112 | file: MobileFaceNetEmbedder.kt > extractEmbedding > tensor
            val shape = longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
            // LOCAL inputTensor: Tensor ONNX input | file: MobileFaceNetEmbedder.kt > extractEmbedding > tensor
            val inputTensor = OnnxTensor.createTensor(env, inputFloatBuffer, shape)

            // LOCAL inputName: Ten node input ONNX | file: MobileFaceNetEmbedder.kt > extractEmbedding > run
            val inputName = session.inputNames.iterator().next()

            session.run(mapOf(inputName to inputTensor)).use { result ->
                // LOCAL outputTensor: Tensor ket qua truyen ve | file: MobileFaceNetEmbedder.kt > extractEmbedding > output
                val outputTensor = result.get(0) as OnnxTensor
                // LOCAL rawOutput: Mảng float 2 chiều 1x512 | file: MobileFaceNetEmbedder.kt > extractEmbedding > output
                val rawOutput = outputTensor.floatBuffer

                rawOutput.get(outEmbedding, 0, AppConfig.EMBEDDING_SIZE)
            }

            inputTensor.close()

            // L2 Normalize embedding vector
            // LOCAL normSq: Tong binh phuong cac phan tu | file: MobileFaceNetEmbedder.kt > extractEmbedding > l2 norm
            var normSq = 0.0f
            for (i in 0 until AppConfig.EMBEDDING_SIZE) {
                normSq += outEmbedding[i] * outEmbedding[i]
            }

            // LOCAL norm: Can bac hai tong binh phuong | file: MobileFaceNetEmbedder.kt > extractEmbedding > l2 norm
            val norm = sqrt(normSq.toDouble()).toFloat()
            if (norm > 0.00001f) {
                for (i in 0 until AppConfig.EMBEDDING_SIZE) {
                    outEmbedding[i] /= norm
                }
            }

            // Record execution time in ms
            Globals.recogTimeMs = System.currentTimeMillis() - startTime
            Log.i(TAG, "[EMBEDDING_END] Feature embedding extracted in ${Globals.recogTimeMs} ms")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error in extractEmbedding: ${e.message}", e)
            return false
        }
    }

    private fun copyAssetToFilesDir(context: Context, assetPath: String): File {
        // LOCAL fileName: Ten file asset model | file: MobileFaceNetEmbedder.kt > copyAssetToFilesDir > step 1
        val fileName = assetPath.substringAfterLast("/")
        // LOCAL outFile: File xuat ra filesDir | file: MobileFaceNetEmbedder.kt > copyAssetToFilesDir > step 2
        val outFile = File(context.filesDir, fileName)

        if (!outFile.exists()) {
            try {
                context.assets.open(assetPath).use { inputStream ->
                    FileOutputStream(outFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Log.d(TAG, "Copied asset $assetPath to ${outFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy asset $assetPath: ${e.message}", e)
            }
        }
        return outFile
    }

    companion object {
        private const val TAG = "MobileFaceNetEmbedder"
    }
}
