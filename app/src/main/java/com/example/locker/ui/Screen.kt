package com.example.locker.ui

/**
 * Che do quet khuon mat trong he thong.
 */
enum class ScanMode {
    /** Dang ky tu moi cho khach hàng */
    REGISTER,

    /** Mo tu tam thoi (giu lai du lieu va tu) */
    OPEN_TEMP,

    /** Tra tu (mo tu va xoa du lieu giai phong tu) */
    RETURN
}

/**
 * Sealed class dinh nghia cac man hinh dieu huong trong ung dung Kiosk va Admin.
 */
sealed class Screen {
    /** Man hinh chinh (Chon Dang ky tu / Mo tu) */
    object Home : Screen()

    /** Man hinh chon che do Mo tu (Mo tam / Tra tu) */
    object OpenMenu : Screen()

    /** Man hinh quet khuon mat va phan tich */
    data class Scan(val mode: ScanMode) : Screen()

    /** Man hinh dang nhap Admin bang PIN */
    object AdminLogin : Screen()

    /** Man hinh Dashboard Quan tri Admin */
    object AdminDashboard : Screen()
}

