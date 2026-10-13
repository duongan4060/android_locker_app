package com.example.locker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Man hinh luoc chon phuong thuc Mo tu - Layout chieu doc (Portrait).
 */
@Composable
fun OpenMenuScreen(
    onOpenTempClick: () -> Unit,
    onReturnLockerClick: () -> Unit,
    onBackClick: () -> Unit
) {
    // LOCAL backgroundColor: Mau nen man hinh Mo tu | file: OpenMenuScreen.kt > OpenMenuScreen > UI layout
    val backgroundColor = Color(0xFF121826)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "CHỌN THAO TÁC MỞ TỦ",
                color = Color(0xFF4D92FF),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Vui lòng chọn mục đích trước khi quét khuôn mặt",
                color = Color.LightGray,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // LOCAL tempButtonColor: Mau vang cam cho Mo tam | file: OpenMenuScreen.kt > OpenMenuScreen > Temp Button
            val tempButtonColor = Color(0xFFF59E0B)

            Button(
                onClick = onOpenTempClick,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = tempButtonColor),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(100.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "MỞ TẠM",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Lấy đồ & Giữ lại tủ",
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // LOCAL returnButtonColor: Mau do hong cho Tra tu | file: OpenMenuScreen.kt > OpenMenuScreen > Return Button
            val returnButtonColor = Color(0xFFEF4444)

            Button(
                onClick = onReturnLockerClick,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = returnButtonColor),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(100.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "TRẢ TỦ",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Lấy hết đồ & Xóa thông tin",
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            OutlinedButton(
                onClick = onBackClick,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(56.dp)
            ) {
                Text(
                    text = "QUAY LẠI",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
