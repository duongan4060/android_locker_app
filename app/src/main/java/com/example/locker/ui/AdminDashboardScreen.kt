package com.example.locker.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.locker.core.AppConfig
import com.example.locker.data.LockerEntity
import com.example.locker.data.LockerRepository
import com.example.locker.data.LockerSessionEntity
import com.example.locker.hardware.FakeLockerController
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Man hinh Dashboard Quan tri Admin - Layout Kiosk chieu doc (Portrait).
 */
@Composable
fun AdminDashboardScreen(
    repository: LockerRepository,
    onRequestExportJson: () -> Unit,
    onRequestExportCsv: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backgroundColor = Color(0xFF121826)

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Tổng quan", "Cấu hình số tủ", "Danh sách tủ", "Lịch sử", "Database & SAF")

    // Real-time states
    val lockersList by repository.allLockersFlow.collectAsState(initial = emptyList())
    val sessionsList by repository.allSessionsFlow.collectAsState(initial = emptyList())

    var configuredCountState by remember { mutableIntStateOf(30) }
    var adminPinState by remember { mutableStateOf("888888") }
    var lastBackupTimeState by remember { mutableLongStateOf(0L) }

    // Dialog states
    var showPinDialog by remember { mutableStateOf(false) }
    var newPinInput by remember { mutableStateOf("") }
    var showConfigConfirmDialog by remember { mutableStateOf(false) }
    var pendingConfigCount by remember { mutableIntStateOf(30) }

    // Search and filter states
    var lockerSearchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("TẤT CẢ") }
    var sessionSearchQuery by remember { mutableStateOf("") }

    // Hardware Locker Controller for emergency open
    val hardwareController = remember { FakeLockerController() }

    // Initial load
    remember(Unit) {
        scope.launch {
            configuredCountState = repository.getConfiguredLockerCount()
            adminPinState = repository.getAdminPin()
            lastBackupTimeState = repository.getLastBackupTime()
        }
    }

    val occupiedCount = lockersList.count { it.status == "OCCUPIED" }
    val availableCount = (configuredCountState - occupiedCount).coerceAtLeast(0)
    val maintenanceCount = lockersList.count { it.status == "MAINTENANCE" }
    val disabledCount = lockersList.count { it.status == "DISABLED" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBackClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("← Trang chủ", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "BẢNG QUẢN TRỊ ADMIN - SMART LOCKER",
                    color = Color(0xFF4D92FF),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Thiết bị: ${AppConfig.DEFAULT_DEVICE_ID} | Giới hạn phần cứng: ${AppConfig.MAX_HARDWARE_LOCKERS} tủ",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }
        }

        // Navigation Tab Row
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color(0xFF0F172A),
            contentColor = Color(0xFF3B82F6),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = Color(0xFF3B82F6)
                )
            }
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTabIndex == index) Color(0xFF3B82F6) else Color.LightGray
                        )
                    }
                )
            }
        }

        // Content Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when (selectedTabIndex) {
                // TAB 0: TỔNG QUAN
                0 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "TỔNG QUAN HỆ THỐNG",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatCard("Tủ cấu hình", "$configuredCountState", Color(0xFF3B82F6), Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            StatCard("Đang trống", "$availableCount", Color(0xFF10B981), Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            StatCard("Đang sử dụng", "$occupiedCount", Color(0xFFF59E0B), Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatCard("Bảo trì", "$maintenanceCount", Color(0xFFEC4899), Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            StatCard("Vô hiệu hóa", "$disabledCount", Color(0xFF6B7280), Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            StatCard("Giới hạn rơ-le", "${AppConfig.MAX_HARDWARE_LOCKERS}", Color(0xFF8B5CF6), Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        val (dbPath, dbSize) = repository.getDatabaseInfo()

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("THÔNG TIN DATABASE VÀ THIẾT BỊ", fontWeight = FontWeight.Bold, color = Color.Yellow, fontSize = 15.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Tên Database: locker_db", color = Color.White, fontSize = 13.sp)
                                Text("Đường dẫn thực tế: $dbPath", color = Color.LightGray, fontSize = 12.sp)
                                Text("Dung lượng File DB: ${dbSize / 1024} KB", color = Color.LightGray, fontSize = 12.sp)
                                Text("Mã thiết bị: ${AppConfig.DEFAULT_DEVICE_ID}", color = Color.LightGray, fontSize = 12.sp)
                                Text("Tích hợp Website: Sẵn sàng (Offline-first / Repository API abstraction)", color = Color(0xFF10B981), fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { showPinDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("ĐỔI MÃ PIN ADMIN", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // TAB 1: CẤU HÌNH SỐ TỦ
                1 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "QUẢN LÝ SỐ LƯỢNG TỦ HỆ THỐNG",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Thay đổi số lượng tủ đang vận hành trên Kiosk. Giới hạn phần cứng điều khiển thực tế (ESP32/PCF8575): ${AppConfig.MAX_HARDWARE_LOCKERS} tủ.",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("SỐ TỦ ĐANG CẤU HÌNH", color = Color.LightGray, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("$configuredCountState / ${AppConfig.MAX_HARDWARE_LOCKERS}", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF3B82F6))

                                Spacer(modifier = Modifier.height(20.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Button(
                                        onClick = {
                                            if (pendingConfigCount - 10 >= occupiedCount && pendingConfigCount - 10 >= 1) {
                                                pendingConfigCount -= 10
                                            }
                                        },
                                        enabled = pendingConfigCount - 10 >= occupiedCount && pendingConfigCount - 10 >= 1,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("-10", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            if (pendingConfigCount > 1 && pendingConfigCount > occupiedCount) {
                                                pendingConfigCount--
                                            }
                                        },
                                        enabled = pendingConfigCount > 1 && pendingConfigCount > occupiedCount,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("-1", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Text("$pendingConfigCount TỦ", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Button(
                                        onClick = {
                                            if (pendingConfigCount < AppConfig.MAX_HARDWARE_LOCKERS) {
                                                pendingConfigCount++
                                            }
                                        },
                                        enabled = pendingConfigCount < AppConfig.MAX_HARDWARE_LOCKERS,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("+1", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            if (pendingConfigCount + 10 <= AppConfig.MAX_HARDWARE_LOCKERS) {
                                                pendingConfigCount += 10
                                            }
                                        },
                                        enabled = pendingConfigCount + 10 <= AppConfig.MAX_HARDWARE_LOCKERS,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("+10", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = { showConfigConfirmDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(0.7f)
                                ) {
                                    Text("ÁP DỤNG CẤU HÌNH MỚI", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }

                // TAB 2: DANH SÁCH TỦ & THÔNG TIN ĐĂNG KÝ
                2 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = lockerSearchQuery,
                                onValueChange = { lockerSearchQuery = it },
                                placeholder = { Text("Tìm theo mã tủ (e.g. LOCKER-001)") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF3B82F6),
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Status Filter Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val filterOptions = listOf("TẤT CẢ", "TRỐNG", "ĐANG SỬ DỤNG", "BẢO TRÌ", "VÔ HIỆU HÓA")
                            filterOptions.forEach { filter ->
                                val isSelected = selectedStatusFilter == filter
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isSelected) Color(0xFF3B82F6) else Color(0xFF1E293B),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedStatusFilter = filter }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = filter,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val filteredLockers = (1..configuredCountState).map { id ->
                            val code = "LOCKER-%03d".format(id)
                            lockersList.find { it.lockerId == id } ?: LockerEntity(lockerId = id, lockerCode = code, status = "AVAILABLE")
                        }.filter { item ->
                            val matchQuery = lockerSearchQuery.isEmpty() || item.lockerCode.contains(lockerSearchQuery, ignoreCase = true)
                            val matchStatus = when (selectedStatusFilter) {
                                "TRỐNG" -> item.status == "AVAILABLE"
                                "ĐANG SỬ DỤNG" -> item.status == "OCCUPIED"
                                "BẢO TRÌ" -> item.status == "MAINTENANCE"
                                "VÔ HIỆU HÓA" -> item.status == "DISABLED"
                                else -> true
                            }
                            matchQuery && matchStatus
                        }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filteredLockers) { locker ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(locker.lockerCode, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = when (locker.status) {
                                                    "OCCUPIED" -> "Đang sử dụng | User: ${locker.currentUserId ?: "Unknown"}"
                                                    "MAINTENANCE" -> "Bảo trì"
                                                    "DISABLED" -> "Vô hiệu hóa"
                                                    else -> "Còn trống"
                                                },
                                                fontSize = 12.sp,
                                                color = when (locker.status) {
                                                    "OCCUPIED" -> Color(0xFFF59E0B)
                                                    "MAINTENANCE" -> Color(0xFFEC4899)
                                                    "DISABLED" -> Color.Gray
                                                    else -> Color(0xFF10B981)
                                                }
                                            )
                                            if (locker.sessionStartAt != null) {
                                                Text("Bắt đầu: ${formatDate(locker.sessionStartAt)}", fontSize = 11.sp, color = Color.LightGray)
                                            }
                                        }

                                        Row {
                                            // Emergency Open Button
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        hardwareController.open(locker.lockerId)
                                                        Toast.makeText(context, "Đã phát lệnh mở rơ-le ${locker.lockerCode}", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(36.dp)
                                            ) {
                                                Text("Mở Rơ-le", fontSize = 11.sp)
                                            }

                                            Spacer(modifier = Modifier.width(6.dp))

                                            if (locker.status == "OCCUPIED") {
                                                Button(
                                                    onClick = {
                                                        scope.launch {
                                                            repository.releaseLocker(locker.lockerId)
                                                            Toast.makeText(context, "Đã giải phóng ${locker.lockerCode}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.height(36.dp)
                                                ) {
                                                    Text("Giải phóng", fontSize = 11.sp)
                                                }
                                            } else {
                                                val isMaint = locker.status == "MAINTENANCE"
                                                Button(
                                                    onClick = {
                                                        scope.launch {
                                                            val targetStatus = if (isMaint) "AVAILABLE" else "MAINTENANCE"
                                                            repository.setLockerStatus(locker.lockerId, targetStatus)
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = if (isMaint) Color(0xFF10B981) else Color(0xFFEC4899)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.height(36.dp)
                                                ) {
                                                    Text(if (isMaint) "Mở lại" else "Bảo trì", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 3: LỊCH SỬ SỬ DỤNG TỦ
                3 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = sessionSearchQuery,
                            onValueChange = { sessionSearchQuery = it },
                            placeholder = { Text("Tìm lịch sử theo mã tủ/User ID...") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val filteredSessions = sessionsList.filter {
                            sessionSearchQuery.isEmpty() ||
                                    it.lockerCode.contains(sessionSearchQuery, ignoreCase = true) ||
                                    it.userId.contains(sessionSearchQuery, ignoreCase = true)
                        }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filteredSessions) { session ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("${session.lockerCode} | User: ${session.userId}", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                            Text(
                                                text = when (session.actionType) {
                                                    "REGISTER" -> "ĐĂNG KÝ MỚI"
                                                    "OPEN_TEMP" -> "MỞ TẠM"
                                                    "RETURN" -> "TRẢ TỦ"
                                                    else -> session.actionType
                                                },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = when (session.actionType) {
                                                    "REGISTER" -> Color(0xFF10B981)
                                                    "OPEN_TEMP" -> Color(0xFFF59E0B)
                                                    "RETURN" -> Color(0xFF3B82F6)
                                                    else -> Color.White
                                                }
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Thời điểm bắt đầu: ${formatDate(session.startTime)}", fontSize = 12.sp, color = Color.LightGray)
                                        if (session.endTime != null) {
                                            Text("Thời điểm kết thúc: ${formatDate(session.endTime)}", fontSize = 12.sp, color = Color.LightGray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 4: DATABASE & SAF EXPORT
                4 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        Text("QUẢN LÝ DATABASE VÀ XUẤT DỮ LIỆU", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))

                        val (dbPath, dbSize) = repository.getDatabaseInfo()

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("THÔNG TIN LƯU TRỮ DATABASE NỘI BỘ", fontWeight = FontWeight.Bold, color = Color(0xFF3B82F6), fontSize = 15.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Tên Database: locker_db", color = Color.White, fontSize = 13.sp)
                                Text("Đường dẫn Runtime trên thiết bị:\n$dbPath", color = Color.LightGray, fontSize = 12.sp)
                                Text("Kích thước File DB: ${dbSize / 1024} KB", color = Color.LightGray, fontSize = 12.sp)
                                Text("Sao lưu gần nhất: ${if (lastBackupTimeState > 0) formatDate(lastBackupTimeState) else "Chưa sao lưu"}", color = Color.LightGray, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onRequestExportJson,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text("XUẤT DỮ LIỆU JSON (CHO WEBSITE)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onRequestExportCsv,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text("XUẤT LỊCH SỬ SỬ DỤNG TỦ (CSV)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }

    // Change Admin PIN Dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Đổi Mã PIN Admin") },
            text = {
                OutlinedTextField(
                    value = newPinInput,
                    onValueChange = { newPinInput = it },
                    label = { Text("Mã PIN mới (Ít nhất 4 chữ số)") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPinInput.length >= 4) {
                        scope.launch {
                            repository.updateAdminPin(newPinInput)
                            adminPinState = newPinInput
                            Toast.makeText(context, "Đã cập nhật mã PIN Admin mới!", Toast.LENGTH_SHORT).show()
                            showPinDialog = false
                            newPinInput = ""
                        }
                    }
                }) {
                    Text("Lưu")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    // Config Lockers Confirmation Dialog
    if (showConfigConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfigConfirmDialog = false },
            title = { Text("Xác nhận thay đổi số tủ") },
            text = {
                Text("Bạn có chắc muốn đổi số lượng tủ cấu hình từ $configuredCountState thành $pendingConfigCount tủ không?\n\nGiới hạn phần cứng rơ-le thực tế: 30 tủ.")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val ok = repository.updateConfiguredLockerCount(pendingConfigCount)
                        if (ok) {
                            configuredCountState = pendingConfigCount
                            Toast.makeText(context, "Đã cập nhật cấu hình thành $pendingConfigCount tủ!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Lỗi: Không thể giảm số tủ thấp hơn số tủ đang được dùng!", Toast.LENGTH_LONG).show()
                        }
                        showConfigConfirmDialog = false
                    }
                }) {
                    Text("Xác nhận")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfigConfirmDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
private fun StatCard(title: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = valueColor)
            Spacer(modifier = Modifier.height(2.dp))
            Text(title, fontSize = 11.sp, color = Color.LightGray)
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    return sdf.format(Date(timestamp))
}
