package com.familypoint.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familypoint.app.model.AvatarCatalog

@Composable
fun OnboardingScreen(
    initialNickname: String,
    initialAvatarCode: String,
    initialInviteCode: String,
    initialPhoneNumber: String,
    isSaving: Boolean,
    errorMessage: String?,
    onSaveProfile: (nickname: String, avatarCode: String, inviteCode: String, phoneNumber: String) -> Unit
) {
    var inviteCode by remember(initialInviteCode) { mutableStateOf(initialInviteCode) }
    var nickname by remember(initialNickname) { mutableStateOf(initialNickname) }
    var selectedAvatarCode by remember(initialAvatarCode) {
        val base = initialAvatarCode.substringBefore("|")
        mutableStateOf(base.ifBlank { AvatarCatalog.avatars.first().code })
    }
    var phoneNumber by remember(initialPhoneNumber) { mutableStateOf(initialPhoneNumber) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF8FAFC)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = "우리 가족 어디 있나?",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "우리 가족만의 가족코드와 별칭, 대표 캐릭터를 선택해 주세요.",
                fontSize = 13.sp,
                color = Color(0xFF475569)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 선택된 프로필 미리보기 카드
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = AvatarCatalog.getDrawableRes(selectedAvatarCode)),
                        contentDescription = "선택된 아바타",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color(0xFF2563EB), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (nickname.isBlank()) "별칭을 입력해 주세요" else nickname,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (nickname.isBlank()) Color(0xFF94A3B8) else Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = buildString {
                                append("가족코드: ")
                                append(if (inviteCode.isBlank()) "미입력" else inviteCode.uppercase())
                                if (phoneNumber.isNotBlank()) {
                                    append(" · ")
                                    append(phoneNumber)
                                }
                            },
                            fontSize = 12.sp,
                            color = Color(0xFF2563EB)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 1행: 가족코드 & 내 별칭 입력란
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { if (it.length <= 12) inviteCode = it.uppercase() },
                    label = { Text("가족코드 등록") },
                    placeholder = { Text("예: FAMILY01") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { if (it.length <= 12) nickname = it },
                    label = { Text("내 별칭 입력") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2행: 전화번호 입력란 (선택)
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { if (it.length <= 15) phoneNumber = it.filter { c -> c.isDigit() || c == '-' } },
                label = { Text("내 전화번호 (선택, 전화·문자 연동용)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "대표 이미지 선택",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(6.dp))

            // 20종 아바타 그리드
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(AvatarCatalog.avatars, key = { it.code }) { avatar ->
                    val isSelected = selectedAvatarCode.startsWith(avatar.code.take(9))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) Color(0xFFEFF6FF) else Color.White
                            )
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) Color(0xFF2563EB) else Color(0xFFE2E8F0),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { selectedAvatarCode = avatar.code }
                            .padding(vertical = 8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = avatar.drawableRes),
                            contentDescription = null,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    onSaveProfile(
                        nickname.trim(),
                        selectedAvatarCode.take(9),
                        inviteCode.trim().uppercase(),
                        phoneNumber.trim()
                    )
                },
                enabled = inviteCode.trim().length >= 2 && nickname.trim().length >= 2 && !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("가족 그룹 연결 중...")
                } else {
                    Text(
                        text = "시작하기",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
