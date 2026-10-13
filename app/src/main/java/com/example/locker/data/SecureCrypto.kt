package com.example.locker.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.example.locker.core.AppConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Ma hoa va Giai ma vector FloatArray 512D bang thuat toan AES-GCM qua Android Keystore.
 */
object SecureCrypto {
    private const val KEY_ALIAS = "LockerFaceKeyAlias"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG = "SecureCrypto"

    /**
     * Lay hoac tao SecretKey trong Android Keystore.
     */
    private fun getSecretKey(): SecretKey {
        // LOCAL keyStore: Keystore instance | file: SecureCrypto.kt > getSecretKey > step 1
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            // LOCAL keyGenerator: KeyGenerator tao AES SecretKey | file: SecureCrypto.kt > getSecretKey > generate
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )

            // LOCAL keySpec: KeyGenParameterSpec AES GCM 256bit | file: SecureCrypto.kt > getSecretKey > generate
            val keySpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(keySpec)
            keyGenerator.generateKey()
            Log.d(TAG, "Generated new AES-GCM SecretKey in Android Keystore.")
        }

        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    /**
     * Ma hoa FloatArray 512D thanh Pair(EncryptedBytes, IV).
     */
    fun encryptEmbedding(embedding: FloatArray): Pair<ByteArray, ByteArray>? {
        try {
            // Convert FloatArray (512 floats) to ByteArray (2048 bytes)
            // LOCAL byteBuffer: ByteBuffer 2048 bytes | file: SecureCrypto.kt > encryptEmbedding > convert
            val byteBuffer = ByteBuffer.allocate(embedding.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (value in embedding) {
                byteBuffer.putFloat(value)
            }
            // LOCAL rawBytes: Mảng byte 2048 phan tu | file: SecureCrypto.kt > encryptEmbedding > convert
            val rawBytes = byteBuffer.array()

            // LOCAL cipher: Cipher AES/GCM/NoPadding | file: SecureCrypto.kt > encryptEmbedding > cipher
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())

            // LOCAL iv: Initialization Vector sinh ra tu Cipher | file: SecureCrypto.kt > encryptEmbedding > cipher
            val iv = cipher.iv
            // LOCAL encryptedBytes: Mảng byte da ma hoa AES-GCM | file: SecureCrypto.kt > encryptEmbedding > cipher
            val encryptedBytes = cipher.doFinal(rawBytes)

            return Pair(encryptedBytes, iv)
        } catch (e: Exception) {
            Log.e(TAG, "Error encrypting embedding: ${e.message}", e)
            return null
        }
    }

    /**
     * Giai ma EncryptedBytes va IV ve FloatArray 512D ban dau.
     */
    fun decryptEmbedding(encryptedBytes: ByteArray, iv: ByteArray): FloatArray? {
        try {
            // LOCAL cipher: Cipher AES/GCM/NoPadding | file: SecureCrypto.kt > decryptEmbedding > cipher
            val cipher = Cipher.getInstance(TRANSFORMATION)
            // LOCAL spec: GCMParameterSpec tLen 128 | file: SecureCrypto.kt > decryptEmbedding > cipher
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)

            // LOCAL decryptedBytes: Mảng byte 2048 phan tu sau khi giai ma | file: SecureCrypto.kt > decryptEmbedding > cipher
            val decryptedBytes = cipher.doFinal(encryptedBytes)

            // LOCAL byteBuffer: ByteBuffer nhan decryptedBytes | file: SecureCrypto.kt > decryptEmbedding > convert
            val byteBuffer = ByteBuffer.wrap(decryptedBytes).order(ByteOrder.LITTLE_ENDIAN)
            // LOCAL result: FloatArray 512 phan tu | file: SecureCrypto.kt > decryptEmbedding > convert
            val result = FloatArray(AppConfig.EMBEDDING_SIZE)
            for (i in 0 until AppConfig.EMBEDDING_SIZE) {
                result[i] = byteBuffer.getFloat()
            }

            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting embedding: ${e.message}", e)
            return null
        }
    }
}
