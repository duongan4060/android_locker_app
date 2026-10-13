package com.example.locker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity luu tru Lich su tung luot su dung tu (Locker Session History) phuc vu Admin va Dong bo Website.
 */
@Entity(
    tableName = "locker_sessions",
    indices = [
        Index(value = ["lockerId"]),
        Index(value = ["lockerCode"]),
        Index(value = ["startTime"])
    ]
)
data class LockerSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val sessionId: Long = 0,
    val lockerId: Int,
    val lockerCode: String,
    val userId: String,
    val actionType: String, // REGISTER, OPEN_TEMP, RETURN
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val status: String = "COMPLETED", // ACTIVE, COMPLETED, CANCELLED
    val deviceId: String = "LOCKER-KIOSK-01",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncState: Int = 0 // 0: UN_SYNCED, 1: SYNCED
)
