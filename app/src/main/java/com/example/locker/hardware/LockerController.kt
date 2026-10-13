package com.example.locker.hardware

/**
 * Interface dieu khien phan cung ror-le mo tu khoa.
 */
interface LockerController {
    /**
     * Gui lenh mo tu theo lockerId (1..TOTAL_LOCKERS).
     *
     * @param lockerId So tu can mo
     * @return Boolean true neu lenh gui thanh cong
     */
    fun open(lockerId: Int): Boolean
}
