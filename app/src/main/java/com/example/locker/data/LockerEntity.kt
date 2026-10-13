package com.example.locker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity Room Database luu tru thong tin tu va vector embedding da ma hoa phuc vu Kiosk va Website integration.
 */
@Entity(
    tableName = "lockers",
    indices = [Index(value = ["lockerCode"], unique = true)]
)
data class LockerEntity(
    @PrimaryKey
    val lockerId: Int,
    val lockerCode: String = "LOCKER-%03d".format(lockerId),
    val status: String = "AVAILABLE", // AVAILABLE, OCCUPIED, MAINTENANCE, DISABLED
    val encryptedEmbedding: ByteArray? = null,
    val iv: ByteArray? = null,
    val currentUserId: String? = null,
    val sessionStartAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncState: Int = 0 // 0: UN_SYNCED, 1: SYNCED
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LockerEntity

        if (lockerId != other.lockerId) return false
        if (lockerCode != other.lockerCode) return false
        if (status != other.status) return false
        if (encryptedEmbedding != null) {
            if (other.encryptedEmbedding == null) return false
            if (!encryptedEmbedding.contentEquals(other.encryptedEmbedding)) return false
        } else if (other.encryptedEmbedding != null) return false
        if (iv != null) {
            if (other.iv == null) return false
            if (!iv.contentEquals(other.iv)) return false
        } else if (other.iv != null) return false
        if (currentUserId != other.currentUserId) return false
        if (sessionStartAt != other.sessionStartAt) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false
        if (syncState != other.syncState) return false

        return true
    }

    override fun hashCode(): Int {
        var result = lockerId
        result = 31 * result + lockerCode.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + (encryptedEmbedding?.contentHashCode() ?: 0)
        result = 31 * result + (iv?.contentHashCode() ?: 0)
        result = 31 * result + (currentUserId?.hashCode() ?: 0)
        result = 31 * result + (sessionStartAt?.hashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + syncState.hashCode()
        return result
    }
}

