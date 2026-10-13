package com.example.locker.hardware

import android.util.Log
import com.example.locker.core.AppConfig

/**
 * Lop gia lap va phan bo dia chi phan cung ror-le mo tu (1..60) cho ESP32 / PCF8575 / Relay board.
 */
class FakeLockerController : LockerController {

    override fun open(lockerId: Int): Boolean {
        if (lockerId < 1 || lockerId > AppConfig.MAX_HARDWARE_LOCKERS) {
            Log.e(TAG, "[HARDWARE ERROR] Locker #$lockerId is out of hardware range (1..${AppConfig.MAX_HARDWARE_LOCKERS})")
            return false
        }

        Log.d(TAG, "[HARDWARE RELAY] Sending open command for Locker #$lockerId (Locker Code: LOCKER-%03d)...".format(lockerId))

        // Giao thức điều khiển ESP32 / PCF8575 (Mở rộng hỗ trợ tối đa 60 tủ):
        // - Mạch 1 (Board PCF8575 #1): Tủ 1..16 (Địa chỉ I2C 0x20)
        // - Mạch 2 (Board PCF8575 #2): Tủ 17..32 (Địa chỉ I2C 0x21)
        // - Mạch 3 (Board PCF8575 #3): Tủ 33..48 (Địa chỉ I2C 0x22)
        // - Mạch 4 (Board PCF8575 #4): Tủ 49..60 (Địa chỉ I2C 0x23)
        val boardIndex = (lockerId - 1) / 16 + 1
        val relayPin = (lockerId - 1) % 16
        Log.d(TAG, "[HARDWARE RELAY] Target Board: PCF8575 #$boardIndex, Relay Pin: $relayPin")

        Log.d(TAG, "[HARDWARE RELAY] Locker #$lockerId unlocked successfully!")
        return true
    }

    companion object {
        private const val TAG = "FakeLockerController"
    }
}

