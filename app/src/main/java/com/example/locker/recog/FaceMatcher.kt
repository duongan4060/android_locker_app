package com.example.locker.recog

import android.util.Log
import com.example.locker.core.AppConfig
import com.example.locker.core.Globals

/**
 * Ket qua so khop Cosine Similarity giua khuon mat dang quet va cac tu da dang ky.
 */
data class MatchResult(
    val matchedLockerId: Int,
    val bestScore: Float,
    val secondBestScore: Float,
    val isMatch: Boolean
)

/**
 * Class So khop khuon mat dung Cosine Similarity (tich vo huong 2 vector L2-normalized).
 */
object FaceMatcher {

    private const val TAG = "FaceMatcher"

    /**
     * Tinh Cosine Similarity giua 2 vector da duoc L2 Normalize (dot product).
     *
     * @param vec1 Vector L2 normalized 512D
     * @param vec2 Vector L2 normalized 512D
     * @return Cosine similarity (dien [ -1.0, 1.0 ])
     */
    fun computeCosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        var dotProduct = 0.0f
        val size = minOf(vec1.size, vec2.size)

        for (i in 0 until size) {
            dotProduct += vec1[i] * vec2[i]
        }
        return dotProduct
    }

    /**
     * So khop targetEmbedding voi tat ca tu da dang ky trong Globals.embeddingsMemoryCache.
     *
     * @param targetEmbedding Vector 512D cua khuon mat dang quet
     * @return MatchResult ket qua so khop
     */
    fun findBestMatch(targetEmbedding: FloatArray): MatchResult {
        val cache = Globals.embeddingsMemoryCache

        if (cache.isEmpty()) {
            Log.d(TAG, "[FaceMatcher] Memory cache is empty. No registered lockers.")
            return MatchResult(
                matchedLockerId = -1,
                bestScore = 0.0f,
                secondBestScore = 0.0f,
                isMatch = false
            )
        }

        var bestLockerId = -1
        var bestScore = -1.0f
        var secondBestScore = -1.0f

        for ((lockerId, storedEmbedding) in cache) {
            val sim = computeCosineSimilarity(targetEmbedding, storedEmbedding)

            if (sim > bestScore) {
                secondBestScore = bestScore
                bestScore = sim
                bestLockerId = lockerId
            } else if (sim > secondBestScore) {
                secondBestScore = sim
            }
        }

        // Check matching threshold and margin
        val isMatched = (bestScore >= AppConfig.MATCH_THRESHOLD) &&
                ((bestScore - secondBestScore) >= AppConfig.MATCH_MARGIN)

        Log.i(TAG, "[FaceMatcher] Evaluated cache (${cache.size} entries). Best match: locker $bestLockerId, score: %.3f, 2nd score: %.3f, threshold: %.2f, margin: %.2f, isMatch: $isMatched".format(
            bestScore, secondBestScore, AppConfig.MATCH_THRESHOLD, AppConfig.MATCH_MARGIN
        ))

        return MatchResult(
            matchedLockerId = if (isMatched) bestLockerId else -1,
            bestScore = bestScore,
            secondBestScore = secondBestScore,
            isMatch = isMatched
        )
    }
}

