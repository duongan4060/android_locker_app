package com.example.locker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.locker.core.AppConfig
import com.example.locker.core.Globals
import com.example.locker.data.LockerRepository

/**
 * Man hinh chinh cua Kiosk Tu Khoa Locker - Layout chieu doc (Portrait) hien thi thong ke tu khoa.
 */
@Composable
fun HomeScreen(
    repository: LockerRepository,
    onRegisterClick: () -> Unit,
    onOpenMenuClick: () -> Unit,
    onAdminClick: () -> Unit
) {
    val backgroundColor = Color(0xFF121826)

    // Observe occupied count real-time from Room DB Flow
    val occupiedCount by repository.occupiedCountFlow.collectAsState(initial = Globals.embeddingsMemoryCache.size)
    val lockersList by repository.allLockersFlow.collectAsState(initial = emptyList())

    var configuredTotal by remember { mutableIntStateOf(AppConfig.TOTAL_LOCKERS) }
    LaunchedEffect(Unit) {
        configuredTotal = repository.getConfiguredLockerCount()
    }

    val totalLockers = maxOf(configuredTotal, lockersList.size)
    val availableCount = (totalLockers - occupiedCount).coerceAtLeast(0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(24.dp)
    ) {
        // Top-Right Admin Settings Button
        Button(
            onClick = onAdminClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .height(42.dp)
        ) {
            Text("⚙ CÀI ĐẶT ADMIN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3B82F6))
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
        ) {
            Text(
                text = "HỆ THỐNG TỦ KHÓA THÔNG MINH",
                color = Color(0xFF4D92FF),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Nhận diện khuôn mặt - An toàn & Tiện lợi",
                color = Color.LightGray,
                fontSize = 17.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Real-time Locker Status Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .shadow(8.dp, shape = RoundedCornerShape(18.dp))
                    .border(1.5.dp, Color(0xFF334155), RoundedCornerShape(18.dp))
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Total Lockers
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$totalLockers",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tổng số tủ",
                            fontSize = 13.sp,
                            color = Color.LightGray
                        )
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(Color(0xFF475569))
                    )

                    // Occupied Lockers
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$occupiedCount",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF59E0B) // Amber
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Đang sử dụng",
                            fontSize = 13.sp,
                            color = Color.LightGray
                        )
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(Color(0xFF475569))
                    )

                    // Available Lockers
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$availableCount",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981) // Green
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Còn trống",
                            fontSize = 13.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            val registerButtonColor = Color(0xFF10B981)

            Button(
                onClick = onRegisterClick,
                enabled = availableCount > 0,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = registerButtonColor,
                    disabledContainerColor = Color.Gray
                ),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(98.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (availableCount > 0) "ĐĂNG KÍ TỦ" else "HẾT TỦ TRỐNG",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (availableCount > 0) "(Gửi đồ mới)" else "(Vui lòng chờ trả tủ)",
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val openButtonColor = Color(0xFF3B82F6)

            Button(
                onClick = onOpenMenuClick,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = openButtonColor),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(98.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "MỞ TỦ",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "(Lấy đồ / Trả tủ)",
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

