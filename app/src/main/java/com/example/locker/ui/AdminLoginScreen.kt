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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.locker.data.LockerRepository
import kotlinx.coroutines.launch

/**
 * Man hinh Dang nhap Quan tri Admin nhap PIN xac thuc.
 */
@Composable
fun AdminLoginScreen(
    repository: LockerRepository,
    onLoginSuccess: () -> Unit,
    onBackClick: () -> Unit
) {
    val backgroundColor = Color(0xFF121826)
    var inputPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

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
                text = "XÁC THỰC QUẢN TRỊ ADMIN",
                color = Color(0xFF4D92FF),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Vui lòng nhập mã PIN Admin (Mặc định: 888888)",
                color = Color.LightGray,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // PIN Display Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(64.dp)
                    .border(1.5.dp, if (errorMessage.isNotEmpty()) Color(0xFFEF4444) else Color(0xFF334155), RoundedCornerShape(16.dp))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (inputPin.isEmpty()) "• • • • • •" else "• ".repeat(inputPin.length),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (inputPin.isEmpty()) Color.Gray else Color(0xFF10B981),
                        letterSpacing = 4.sp
                    )
                }
            }

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    color = Color(0xFFEF4444),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Numeric Keypad
            val buttons = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("XÓA", "0", "OK")
            )

            for (row in buttons) {
                Row(
                    modifier = Modifier.fillMaxWidth(0.8f),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (btn in row) {
                        Button(
                            onClick = {
                                errorMessage = ""
                                when (btn) {
                                    "XÓA" -> {
                                        if (inputPin.isNotEmpty()) {
                                            inputPin = inputPin.substring(0, inputPin.length - 1)
                                        }
                                    }
                                    "OK" -> {
                                        scope.launch {
                                            val validPin = repository.getAdminPin()
                                            if (inputPin == validPin) {
                                                onLoginSuccess()
                                            } else {
                                                errorMessage = "Mã PIN không chính xác!"
                                                inputPin = ""
                                            }
                                        }
                                    }
                                    else -> {
                                        if (inputPin.length < 8) {
                                            inputPin += btn
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when (btn) {
                                    "OK" -> Color(0xFF10B981)
                                    "XÓA" -> Color(0xFFDC2626)
                                    else -> Color(0xFF1E293B)
                                }
                            ),
                            modifier = Modifier
                                .width(80.dp)
                                .height(60.dp)
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = btn,
                                fontSize = if (btn.length > 1) 14.sp else 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            OutlinedButton(
                onClick = onBackClick,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(50.dp)
            ) {
                Text(
                    text = "QUAY LẠI",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
