package com.example.locker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.locker.ui.AdminDashboardScreen
import com.example.locker.ui.AdminLoginScreen
import com.example.locker.ui.HomeScreen
import com.example.locker.ui.OpenMenuScreen
import com.example.locker.ui.ScanMode
import com.example.locker.ui.ScanScreen
import com.example.locker.ui.Screen
import kotlinx.coroutines.launch

/**
 * MainActivity duy nhat quan ly kiosk va chuyen doi man hinh qua state.
 */
class MainActivity : ComponentActivity() {

    private var pendingExportType: String? = null // "JSON" or "CSV"

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let {
            val repository = com.example.locker.data.LockerRepository(applicationContext)
            kotlinx.coroutines.GlobalScope.launch {
                val success = if (pendingExportType == "JSON") {
                    repository.exportDataToJson(it)
                } else {
                    repository.exportDataToCsv(it)
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(applicationContext, "Xuất dữ liệu thành công!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(applicationContext, "Lỗi khi xuất dữ liệu!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            // Permission denied
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen always ON for kiosk
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Request Camera Permission
        checkAndRequestCameraPermission()

        setContent {
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
            val repository = remember { com.example.locker.data.LockerRepository(applicationContext) }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                when (val screen = currentScreen) {
                    is Screen.Home -> {
                        HomeScreen(
                            repository = repository,
                            onRegisterClick = {
                                currentScreen = Screen.Scan(ScanMode.REGISTER)
                            },
                            onOpenMenuClick = {
                                currentScreen = Screen.OpenMenu
                            },
                            onAdminClick = {
                                currentScreen = Screen.AdminLogin
                            }
                        )
                    }
                    is Screen.OpenMenu -> {
                        OpenMenuScreen(
                            onOpenTempClick = {
                                currentScreen = Screen.Scan(ScanMode.OPEN_TEMP)
                            },
                            onReturnLockerClick = {
                                currentScreen = Screen.Scan(ScanMode.RETURN)
                            },
                            onBackClick = {
                                currentScreen = Screen.Home
                            }
                        )
                    }
                    is Screen.Scan -> {
                        ScanScreen(
                            scanMode = screen.mode,
                            onBackClick = {
                                currentScreen = Screen.Home
                            }
                        )
                    }
                    is Screen.AdminLogin -> {
                        AdminLoginScreen(
                            repository = repository,
                            onLoginSuccess = {
                                currentScreen = Screen.AdminDashboard
                            },
                            onBackClick = {
                                currentScreen = Screen.Home
                            }
                        )
                    }
                    is Screen.AdminDashboard -> {
                        AdminDashboardScreen(
                            repository = repository,
                            onRequestExportJson = {
                                pendingExportType = "JSON"
                                createDocumentLauncher.launch("smart_locker_export_${System.currentTimeMillis()}.json")
                            },
                            onRequestExportCsv = {
                                pendingExportType = "CSV"
                                createDocumentLauncher.launch("smart_locker_sessions_${System.currentTimeMillis()}.csv")
                            },
                            onBackClick = {
                                currentScreen = Screen.Home
                            }
                        )
                    }
                }
            }
        }

        // Enable Immersive Fullscreen Mode after window decorView is attached
        hideSystemUI()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun checkAndRequestCameraPermission() {
        val permission = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun hideSystemUI() {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } catch (_: Exception) {
            // Ignore if view not fully attached yet
        }
    }
}

