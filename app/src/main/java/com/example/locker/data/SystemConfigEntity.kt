package com.example.locker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity luu tru Cau hinh He thong Admin va Kiosk trong Room Database.
 */
@Entity(tableName = "system_config")
data class SystemConfigEntity(
    @PrimaryKey
    val configKey: String,
    val configValue: String,
    val updatedAt: Long = System.currentTimeMillis()
)
