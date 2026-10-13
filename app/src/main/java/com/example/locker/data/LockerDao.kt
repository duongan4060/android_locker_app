package com.example.locker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) thao tac Room Database voi tu khoa, lich su va cau hinh.
 */
@Dao
interface LockerDao {

    // --- LOCKER ENTITY QUERIES ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(locker: LockerEntity)

    @Query("DELETE FROM lockers WHERE lockerId = :lockerId")
    suspend fun deleteByLockerId(lockerId: Int)

    @Query("SELECT * FROM lockers WHERE lockerId = :lockerId LIMIT 1")
    suspend fun getLockerById(lockerId: Int): LockerEntity?

    @Query("SELECT * FROM lockers WHERE lockerCode = :lockerCode LIMIT 1")
    suspend fun getLockerByCode(lockerCode: String): LockerEntity?

    @Query("SELECT * FROM lockers ORDER BY lockerId ASC")
    suspend fun getAllLockers(): List<LockerEntity>

    @Query("SELECT * FROM lockers ORDER BY lockerId ASC")
    fun getAllLockersFlow(): Flow<List<LockerEntity>>

    @Query("SELECT COUNT(*) FROM lockers WHERE status = 'OCCUPIED' OR encryptedEmbedding IS NOT NULL")
    fun getOccupiedCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM lockers WHERE status = :status")
    suspend fun getLockerCountByStatus(status: String): Int

    @Query("UPDATE lockers SET status = :status, updatedAt = :updatedAt WHERE lockerId = :lockerId")
    suspend fun updateLockerStatus(lockerId: Int, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM lockers")
    suspend fun deleteAllLockers()

    // --- LOCKER SESSION HISTORY QUERIES ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: LockerSessionEntity): Long

    @Query("SELECT * FROM locker_sessions ORDER BY startTime DESC LIMIT :limit")
    suspend fun getAllSessions(limit: Int = 500): List<LockerSessionEntity>

    @Query("SELECT * FROM locker_sessions ORDER BY startTime DESC")
    fun getAllSessionsFlow(): Flow<List<LockerSessionEntity>>

    @Query("SELECT * FROM locker_sessions WHERE lockerCode LIKE '%' || :query || '%' OR userId LIKE '%' || :query || '%' ORDER BY startTime DESC")
    suspend fun searchSessions(query: String): List<LockerSessionEntity>

    // --- SYSTEM CONFIG QUERIES ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setConfig(config: SystemConfigEntity)

    @Query("SELECT configValue FROM system_config WHERE configKey = :key LIMIT 1")
    suspend fun getConfigValue(key: String): String?

    @Query("SELECT * FROM system_config")
    suspend fun getAllConfig(): List<SystemConfigEntity>
}


