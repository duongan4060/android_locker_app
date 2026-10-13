package com.example.locker.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.locker.core.AppConfig
import com.example.locker.core.Globals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Repository quan ly Luu tru, System Config, Session History va Cache Embedding.
 */
class LockerRepository(private val context: Context) {

    private val lockerDao: LockerDao = LockerDatabase.getInstance(context).lockerDao()
    private val random = SecureRandom()
    private val allocationMutex = Mutex()

    /** Flow quan sat so luong tu dang bi chiem dung real-time tu Room DB */
    val occupiedCountFlow: Flow<Int> = lockerDao.getOccupiedCountFlow()

    /** Flow quan sat danh sach tat ca cac tu */
    val allLockersFlow: Flow<List<LockerEntity>> = lockerDao.getAllLockersFlow()

    /** Flow quan sat lich su su dung */
    val allSessionsFlow: Flow<List<LockerSessionEntity>> = lockerDao.getAllSessionsFlow()

    /**
     * Nạp toàn bộ embedding và khởi tạo cấu hình hệ thống từ Room DB khi ứng dụng khởi chạy.
     */
    suspend fun loadAllLockersIntoCache() = withContext(Dispatchers.IO) {
        try {
            initDefaultConfigIfMissing()

            val lockers = lockerDao.getAllLockers()
            Globals.embeddingsMemoryCache.clear()

            for (locker in lockers) {
                if (locker.status == "OCCUPIED" && locker.encryptedEmbedding != null && locker.iv != null) {
                    val decryptedEmbedding = SecureCrypto.decryptEmbedding(locker.encryptedEmbedding, locker.iv)
                    if (decryptedEmbedding != null) {
                        Globals.embeddingsMemoryCache[locker.lockerId] = decryptedEmbedding
                    }
                }
            }
            Log.d(TAG, "[Repository] Loaded ${Globals.embeddingsMemoryCache.size} active embeddings into RAM cache.")
        } catch (e: Exception) {
            Log.e(TAG, "[Repository] Failed to load lockers into cache: ${e.message}", e)
        }
    }

    private suspend fun initDefaultConfigIfMissing() {
        val totalConfigured = lockerDao.getConfigValue("TOTAL_CONFIGURED_LOCKERS")
        if (totalConfigured == null) {
            lockerDao.setConfig(SystemConfigEntity("TOTAL_CONFIGURED_LOCKERS", AppConfig.TOTAL_LOCKERS.toString()))
        }
        val maxHardware = lockerDao.getConfigValue("MAX_HARDWARE_LOCKERS")
        if (maxHardware == null) {
            lockerDao.setConfig(SystemConfigEntity("MAX_HARDWARE_LOCKERS", AppConfig.MAX_HARDWARE_LOCKERS.toString()))
        }
        val adminPin = lockerDao.getConfigValue("ADMIN_PIN")
        if (adminPin == null) {
            lockerDao.setConfig(SystemConfigEntity("ADMIN_PIN", AppConfig.DEFAULT_ADMIN_PIN))
        }
        val deviceId = lockerDao.getConfigValue("DEVICE_ID")
        if (deviceId == null) {
            lockerDao.setConfig(SystemConfigEntity("DEVICE_ID", AppConfig.DEFAULT_DEVICE_ID))
        }
    }

    // --- SYSTEM CONFIG MANAGEMENT ---

    suspend fun getConfiguredLockerCount(): Int = withContext(Dispatchers.IO) {
        val valStr = lockerDao.getConfigValue("TOTAL_CONFIGURED_LOCKERS")
        valStr?.toIntOrNull() ?: AppConfig.TOTAL_LOCKERS
    }

    suspend fun updateConfiguredLockerCount(newCount: Int): Boolean = withContext(Dispatchers.IO) {
        if (newCount < 1 || newCount > AppConfig.MAX_HARDWARE_LOCKERS) {
            Log.w(TAG, "[Repository] New configured lockers count $newCount exceeds hardware max limit ${AppConfig.MAX_HARDWARE_LOCKERS}")
            return@withContext false
        }

        val occupiedCount = lockerDao.getLockerCountByStatus("OCCUPIED")
        if (newCount < occupiedCount) {
            Log.w(TAG, "[Repository] Cannot set total lockers to $newCount because $occupiedCount lockers are currently OCCUPIED")
            return@withContext false
        }

        lockerDao.setConfig(SystemConfigEntity("TOTAL_CONFIGURED_LOCKERS", newCount.toString()))
        Log.i(TAG, "[Repository] Updated configured lockers count to $newCount")
        true
    }

    suspend fun getAdminPin(): String = withContext(Dispatchers.IO) {
        lockerDao.getConfigValue("ADMIN_PIN") ?: AppConfig.DEFAULT_ADMIN_PIN
    }

    suspend fun updateAdminPin(newPin: String): Boolean = withContext(Dispatchers.IO) {
        if (newPin.length < 4) return@withContext false
        lockerDao.setConfig(SystemConfigEntity("ADMIN_PIN", newPin))
        true
    }

    suspend fun getDeviceId(): String = withContext(Dispatchers.IO) {
        lockerDao.getConfigValue("DEVICE_ID") ?: AppConfig.DEFAULT_DEVICE_ID
    }

    suspend fun getLastBackupTime(): Long = withContext(Dispatchers.IO) {
        lockerDao.getConfigValue("LAST_BACKUP_TIME")?.toLongOrNull() ?: 0L
    }

    suspend fun updateLastBackupTime(time: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        lockerDao.setConfig(SystemConfigEntity("LAST_BACKUP_TIME", time.toString()))
    }

    // --- LOCKER MANAGEMENT ---

    suspend fun getAllLockerEntities(): List<LockerEntity> = withContext(Dispatchers.IO) {
        lockerDao.getAllLockers()
    }

    /**
     * Cấp phát tủ trống nguyên tử (Atomic) và ghi nhận nhật ký phiên.
     */
    suspend fun registerNewLockerAtomic(embedding: FloatArray): Int? = withContext(Dispatchers.IO) {
        allocationMutex.withLock {
            try {
                val configuredCount = getConfiguredLockerCount()
                val occupiedSet = Globals.embeddingsMemoryCache.keys

                val existingLockersMap = lockerDao.getAllLockers().associateBy { it.lockerId }

                val availableList = (1..configuredCount).filter { id ->
                    val entity = existingLockersMap[id]
                    val isStatusAvailable = (entity == null || entity.status == "AVAILABLE")
                    isStatusAvailable && !occupiedSet.contains(id)
                }

                if (availableList.isEmpty()) {
                    Log.w(TAG, "[Repository] All $configuredCount configured lockers are occupied or unavailable!")
                    return@withLock null
                }

                val selectedId = availableList[random.nextInt(availableList.size)]
                val lockerCode = "LOCKER-%03d".format(selectedId)
                val userId = "USR-%s-%03d".format(
                    SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()),
                    random.nextInt(1000)
                )

                val encryptedData = SecureCrypto.encryptEmbedding(embedding) ?: return@withLock null

                val now = System.currentTimeMillis()
                val entity = LockerEntity(
                    lockerId = selectedId,
                    lockerCode = lockerCode,
                    status = "OCCUPIED",
                    encryptedEmbedding = encryptedData.first,
                    iv = encryptedData.second,
                    currentUserId = userId,
                    sessionStartAt = now,
                    createdAt = existingLockersMap[selectedId]?.createdAt ?: now,
                    updatedAt = now,
                    syncState = 0
                )

                lockerDao.insertOrUpdate(entity)

                // Log Session History
                val session = LockerSessionEntity(
                    lockerId = selectedId,
                    lockerCode = lockerCode,
                    userId = userId,
                    actionType = "REGISTER",
                    startTime = now,
                    endTime = null,
                    status = "ACTIVE",
                    deviceId = getDeviceId()
                )
                lockerDao.insertSession(session)

                Globals.embeddingsMemoryCache[selectedId] = embedding
                Log.i(TAG, "[Repository] Registered locker $lockerCode ($selectedId) for user $userId in DB and History.")
                selectedId
            } catch (e: Exception) {
                Log.e(TAG, "[Repository] Failed atomic locker registration: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Giải phóng tủ: Đưa trạng thái về AVAILABLE, xóa embedding và hoàn tất lịch sử session.
     */
    suspend fun releaseLocker(lockerId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val existing = lockerDao.getLockerById(lockerId)
            val now = System.currentTimeMillis()
            val lockerCode = existing?.lockerCode ?: "LOCKER-%03d".format(lockerId)
            val userId = existing?.currentUserId ?: "UNKNOWN"

            val updatedEntity = LockerEntity(
                lockerId = lockerId,
                lockerCode = lockerCode,
                status = "AVAILABLE",
                encryptedEmbedding = null,
                iv = null,
                currentUserId = null,
                sessionStartAt = null,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                syncState = 0
            )

            lockerDao.insertOrUpdate(updatedEntity)
            Globals.embeddingsMemoryCache.remove(lockerId)

            // Log Session History Return
            val session = LockerSessionEntity(
                lockerId = lockerId,
                lockerCode = lockerCode,
                userId = userId,
                actionType = "RETURN",
                startTime = existing?.sessionStartAt ?: now,
                endTime = now,
                status = "COMPLETED",
                deviceId = getDeviceId()
            )
            lockerDao.insertSession(session)

            Log.i(TAG, "[Repository] Released locker $lockerCode ($lockerId).")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release locker $lockerId: ${e.message}", e)
            false
        }
    }

    /**
     * Đổi trạng thái tủ (Cài đặt Admin): AVAILABLE, MAINTENANCE, DISABLED. Không cho phép đổi nếu tủ đang OCCUPIED.
     */
    suspend fun setLockerStatus(lockerId: Int, newStatus: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val existing = lockerDao.getLockerById(lockerId)
            if (existing?.status == "OCCUPIED" && newStatus != "OCCUPIED" && newStatus != "AVAILABLE") {
                Log.w(TAG, "[Repository] Cannot set status $newStatus on OCCUPIED locker $lockerId!")
                return@withContext false
            }

            val now = System.currentTimeMillis()
            val lockerCode = "LOCKER-%03d".format(lockerId)

            val updatedEntity = LockerEntity(
                lockerId = lockerId,
                lockerCode = lockerCode,
                status = newStatus,
                encryptedEmbedding = if (newStatus == "AVAILABLE" || newStatus == "MAINTENANCE" || newStatus == "DISABLED") null else existing?.encryptedEmbedding,
                iv = if (newStatus == "AVAILABLE" || newStatus == "MAINTENANCE" || newStatus == "DISABLED") null else existing?.iv,
                currentUserId = if (newStatus == "AVAILABLE" || newStatus == "MAINTENANCE" || newStatus == "DISABLED") null else existing?.currentUserId,
                sessionStartAt = if (newStatus == "AVAILABLE" || newStatus == "MAINTENANCE" || newStatus == "DISABLED") null else existing?.sessionStartAt,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                syncState = 0
            )

            lockerDao.insertOrUpdate(updatedEntity)
            if (newStatus != "OCCUPIED") {
                Globals.embeddingsMemoryCache.remove(lockerId)
            }
            Log.i(TAG, "[Repository] Admin set locker $lockerCode status to $newStatus")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set locker status: ${e.message}", e)
            false
        }
    }

    // --- SESSION HISTORY & ADMIN SEARCH ---

    suspend fun getAllSessions(limit: Int = 500): List<LockerSessionEntity> = withContext(Dispatchers.IO) {
        lockerDao.getAllSessions(limit)
    }

    suspend fun searchSessions(query: String): List<LockerSessionEntity> = withContext(Dispatchers.IO) {
        lockerDao.searchSessions(query)
    }

    // --- DATABASE EXPORT & BACKUP (SAF) ---

    fun getDatabaseInfo(): Pair<String, Long> {
        val dbFile = context.getDatabasePath("locker_db")
        val path = dbFile.absolutePath
        val size = if (dbFile.exists()) dbFile.length() else 0L
        return Pair(path, size)
    }

    suspend fun exportDataToJson(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val lockers = lockerDao.getAllLockers()
            val sessions = lockerDao.getAllSessions(1000)
            val configs = lockerDao.getAllConfig()

            val rootJson = JSONObject()
            rootJson.put("exportedAt", formatIsoDate(System.currentTimeMillis()))
            rootJson.put("deviceId", getDeviceId())
            rootJson.put("appVersion", "1.0.0")

            // Lockers array (NO BIOMETRIC ENCRYPTED EMBEDDING EXPORTED)
            val lockersArray = JSONArray()
            for (l in lockers) {
                val item = JSONObject()
                item.put("lockerId", l.lockerId)
                item.put("lockerCode", l.lockerCode)
                item.put("status", l.status)
                item.put("currentUserId", l.currentUserId ?: "")
                item.put("sessionStartAt", l.sessionStartAt?.let { formatIsoDate(it) } ?: "")
                item.put("createdAt", formatIsoDate(l.createdAt))
                item.put("updatedAt", formatIsoDate(l.updatedAt))
                lockersArray.put(item)
            }
            rootJson.put("lockers", lockersArray)

            // Sessions history array
            val sessionsArray = JSONArray()
            for (s in sessions) {
                val item = JSONObject()
                item.put("sessionId", s.sessionId)
                item.put("lockerId", s.lockerId)
                item.put("lockerCode", s.lockerCode)
                item.put("userId", s.userId)
                item.put("actionType", s.actionType)
                item.put("startTime", formatIsoDate(s.startTime))
                item.put("endTime", s.endTime?.let { formatIsoDate(it) } ?: "")
                item.put("status", s.status)
                item.put("deviceId", s.deviceId)
                sessionsArray.put(item)
            }
            rootJson.put("sessions", sessionsArray)

            // Config array (NO MINE ADMIN PIN EXPORTED RAW)
            val configsArray = JSONArray()
            for (c in configs) {
                if (c.configKey != "ADMIN_PIN") {
                    val item = JSONObject()
                    item.put("key", c.configKey)
                    item.put("value", c.configValue)
                    configsArray.put(item)
                }
            }
            rootJson.put("configs", configsArray)

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(rootJson.toString(2))
                }
            }

            updateLastBackupTime()
            Log.i(TAG, "[Repository] Exported JSON data successfully to $uri")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting JSON data: ${e.message}", e)
            false
        }
    }

    suspend fun exportDataToCsv(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val sessions = lockerDao.getAllSessions(1000)

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write("SessionID,LockerID,LockerCode,UserID,ActionType,StartTime,EndTime,Status,DeviceID\n")
                    for (s in sessions) {
                        val line = "%d,%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n".format(
                            s.sessionId,
                            s.lockerId,
                            s.lockerCode,
                            s.userId,
                            s.actionType,
                            formatIsoDate(s.startTime),
                            s.endTime?.let { formatIsoDate(it) } ?: "",
                            s.status,
                            s.deviceId
                        )
                        writer.write(line)
                    }
                }
            }

            updateLastBackupTime()
            Log.i(TAG, "[Repository] Exported CSV session history successfully to $uri")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting CSV data: ${e.message}", e)
            false
        }
    }

    private fun formatIsoDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        return sdf.format(Date(timestamp))
    }

    companion object {
        private const val TAG = "LockerRepository"
    }
}



