package com.familypoint.app.ui

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.familypoint.app.model.AvatarCatalog
import com.familypoint.app.model.FamilyMember
import com.familypoint.app.model.FamilyNotificationMessage
import com.familypoint.app.model.LocationHistoryPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.time.Duration
import java.time.Instant

@Composable
fun MapScreen(
    myMemberId: String?,
    inviteCode: String,
    familyMembers: List<FamilyMember>,
    selectedMember: FamilyMember?,
    historyPoints24h: List<LocationHistoryPoint>,
    show24hTrail: Boolean,
    backgroundSharingEnabled: Boolean,
    isLoading: Boolean,
    statusMessage: String?,
    incomingNotification: FamilyNotificationMessage?,
    onDismissIncomingNotification: () -> Unit,
    onSelectMember: (FamilyMember) -> Unit,
    onToggle24hTrail: (Boolean) -> Unit,
    onToggleBackgroundSharing: (Boolean) -> Unit,
    onSendMyLocationNow: () -> Unit,
    onRefreshFamilyLocations: () -> Unit,
    onEditProfile: () -> Unit,
    onDeleteFamilyMember: (FamilyMember) -> Unit,
    onSendNotificationToMember: (FamilyMember, String) -> Unit
) {
    val context = LocalContext.current

    // 우측 가족 목록 드로어 열림/닫힘 상태
    var isFamilyPanelOpen by remember { mutableStateOf(false) }
    // 하단 설정 시트 열림/닫힘 상태
    var isSettingsPanelOpen by remember { mutableStateOf(false) }

    // 지도에서 가족 클릭 시 열리는 액션 메뉴(전화하기/문자하기/알림보내기) 대상 구성원
    var actionMenuMember by remember { mutableStateOf<FamilyMember?>(null) }

    // 알림보내기 메시지 작성 다이얼로그 상태
    var notificationTargetMember by remember { mutableStateOf<FamilyMember?>(null) }
    var notificationMessageInput by remember { mutableStateOf("") }

    // 가족관리 비밀번호 인증 다이얼로그 및 가족관리 목록 다이얼로그 상태
    var isPasswordDialogOpen by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var isFamilyManageDialogOpen by remember { mutableStateOf(false) }

    val disconnectedMembers = remember(familyMembers) {
        familyMembers.filter { isMemberDisconnected(it) }
    }

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(15.5)
            controller.setCenter(GeoPoint(37.5665, 126.9780))
        }
    }

    var hasCenteredInitially by remember { mutableStateOf(false) }

    LaunchedEffect(selectedMember?.id, selectedMember?.lastLat, selectedMember?.lastLng) {
        val lat = selectedMember?.lastLat
        val lng = selectedMember?.lastLng
        if (lat != null && lng != null) {
            mapView.controller.animateTo(GeoPoint(lat, lng))
            hasCenteredInitially = true
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. 전체 화면 오픈스트리트맵 (OSMDroid)
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                map.overlays.clear()

                // (A) 1일치(24시간) 위치 이동 경로 폴리라인 표시
                if (show24hTrail && historyPoints24h.size >= 2) {
                    val polyline = Polyline(map).apply {
                        outlinePaint.color = AndroidColor.parseColor("#2563EB")
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.isAntiAlias = true
                        setPoints(historyPoints24h.map { GeoPoint(it.latitude, it.longitude) })
                    }
                    map.overlays.add(polyline)
                }

                // (B) 가족 구성원 아바타 + 별칭 커스텀 마커 렌더링
                familyMembers.sortedBy { it.id == selectedMember?.id }.forEach { member ->
                    val lat = member.lastLat ?: return@forEach
                    val lng = member.lastLng ?: return@forEach
                    val isSelected = selectedMember?.id == member.id
                    val isMe = myMemberId == member.id
                    val isOffline = isMemberDisconnected(member)
                    val relTime = formatRelativeTime(member.lastUpdatedAt)

                    val marker = Marker(map).apply {
                        position = GeoPoint(lat, lng)
                        title = member.nickname
                        snippet = if (isOffline) {
                            "연결 끊김 ($relTime) · 마지막 배터리 ${member.batteryLevel}%"
                        } else {
                            "연결됨 ($relTime) · 배터리 ${member.batteryLevel}%"
                        }
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = MarkerIconFactory.createMemberMarkerDrawable(
                            context = context,
                            avatarCode = member.avatarCode,
                            nickname = member.nickname,
                            batteryLevel = member.batteryLevel,
                            isSelected = isSelected,
                            isMe = isMe,
                            isOffline = isOffline,
                            offlineTimeText = relTime
                        )
                        setOnMarkerClickListener { _, _ ->
                            onSelectMember(member)
                            actionMenuMember = member
                            true
                        }
                    }
                    map.overlays.add(marker)
                }

                if (!hasCenteredInitially) {
                    val firstWithLoc = familyMembers.firstOrNull { it.lastLat != null && it.lastLng != null }
                    if (firstWithLoc?.lastLat != null && firstWithLoc.lastLng != null) {
                        map.controller.setCenter(GeoPoint(firstWithLoc.lastLat, firstWithLoc.lastLng))
                        hasCenteredInitially = true
                    }
                }

                map.invalidate()
            }
        )

        // 2. 좌측 상단: 연결 끊김 가족 감지 배너
        AnimatedVisibility(
            visible = disconnectedMembers.isNotEmpty() && !isFamilyPanelOpen,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it }),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 16.dp, start = 12.dp, end = 140.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFFEF2F2).copy(alpha = 0.96f),
                shadowElevation = 6.dp,
                modifier = Modifier.border(
                    width = 1.5.dp,
                    color = Color(0xFFDC2626),
                    shape = RoundedCornerShape(16.dp)
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "연결 끊김 경고",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "연결 끊김 (${disconnectedMembers.size}명)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF991B1B)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(disconnectedMembers, key = { it.id }) { offlineMember ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFEE2E2))
                                    .clickable {
                                        onSelectMember(offlineMember)
                                        actionMenuMember = offlineMember
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = AvatarCatalog.getDrawableRes(offlineMember.avatarCode)),
                                    contentDescription = offlineMember.nickname,
                                    colorFilter = ColorFilter.colorMatrix(
                                        ColorMatrix().apply { setToSaturation(0.2f) }
                                    ),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${offlineMember.nickname} (${formatRelativeTime(offlineMember.lastUpdatedAt)})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF7F1D1D)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. 우측 상단: 가족 목록 열기 손잡이 탭
        AnimatedVisibility(
            visible = !isFamilyPanelOpen,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.92f),
                shadowElevation = 6.dp,
                modifier = Modifier.clickable { isFamilyPanelOpen = true }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "가족 목록 열기",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "가족 (${familyMembers.size})",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (disconnectedMembers.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFFDC2626))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "! ${disconnectedMembers.size}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 4. 우측 슬라이드 가족 목록 패널
        AnimatedVisibility(
            visible = isFamilyPanelOpen,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 12.dp, bottom = 88.dp)
        ) {
            Card(
                shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .width(250.dp)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "우리 가족 (${familyMembers.size})",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "코드: $inviteCode",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onRefreshFamilyLocations,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "새로고침",
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { isFamilyPanelOpen = false },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "가족 목록 닫기",
                                    tint = Color(0xFF475569)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(familyMembers, key = { it.id }) { member ->
                            val isSelected = selectedMember?.id == member.id
                            val isOffline = isMemberDisconnected(member)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        when {
                                            isSelected -> Color(0xFFDBEAFE)
                                            isOffline -> Color(0xFFFEF2F2)
                                            else -> Color(0xFFF8FAFC)
                                        }
                                    )
                                    .border(
                                        width = if (isSelected || isOffline) 1.8.dp else 1.dp,
                                        color = when {
                                            isSelected -> Color(0xFF2563EB)
                                            isOffline -> Color(0xFFFCA5A5)
                                            else -> Color(0xFFE2E8F0)
                                        },
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        onSelectMember(member)
                                        actionMenuMember = member
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = AvatarCatalog.getDrawableRes(member.avatarCode)),
                                    contentDescription = member.nickname,
                                    colorFilter = if (isOffline) {
                                        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.2f) })
                                    } else null,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (member.id == myMemberId) "${member.nickname} (나)" else member.nickname,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E293B),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (isOffline) Color(0xFFDC2626) else Color(0xFF10B981)
                                                )
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = if (isOffline) "연결끊김" else "연결됨",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "🔋${member.batteryLevel}% · ${formatRelativeTime(member.lastUpdatedAt)}",
                                        fontSize = 11.sp,
                                        color = if (isOffline) Color(0xFFB91C1C) else Color(0xFF64748B),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. 지도 위 우측 하단 플로팅 설정 아이콘 버튼
        AnimatedVisibility(
            visible = !isSettingsPanelOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = onSendMyLocationNow,
                    containerColor = Color.White,
                    contentColor = Color(0xFF2563EB),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "공유"
                    )
                }

                FloatingActionButton(
                    onClick = {
                        actionMenuMember = null
                        isSettingsPanelOpen = true
                    },
                    containerColor = Color(0xFF2563EB),
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "설정 열기"
                    )
                }
            }
        }

        // 6. 지도에서 가족 클릭 시 나타나는 [전화하기 / 문자하기 / 알림보내기] 액션 메뉴 팝업 카드
        AnimatedVisibility(
            visible = actionMenuMember != null && !isSettingsPanelOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 14.dp, end = 84.dp, bottom = 16.dp)
        ) {
            val target = actionMenuMember
            if (target != null) {
                val isOffline = isMemberDisconnected(target)
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.98f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Image(
                                    painter = painterResource(id = AvatarCatalog.getDrawableRes(target.avatarCode)),
                                    contentDescription = target.nickname,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = 2.dp,
                                            color = if (isOffline) Color(0xFFDC2626) else Color(0xFF2563EB),
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (target.id == myMemberId) "${target.nickname} (나)" else target.nickname,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (isOffline) Color(0xFFDC2626) else Color(0xFF10B981)
                                                )
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = if (isOffline) "연결끊김" else "연결됨",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(
                                        text = buildString {
                                            append("🔋${target.batteryLevel}% · ${formatRelativeTime(target.lastUpdatedAt)}")
                                            if (target.phoneNumber.isNotBlank()) {
                                                append(" · ${target.phoneNumber}")
                                            }
                                        },
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { actionMenuMember = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "메뉴 닫기",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 3열 아이콘 전용 버튼: [전화하기] [문자하기] [알림보내기]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 1) 전화하기 아이콘 버튼 -> 전화 앱으로 이동
                            Button(
                                onClick = {
                                    val dialUri = if (target.phoneNumber.isNotBlank()) {
                                        Uri.parse("tel:${target.phoneNumber}")
                                    } else {
                                        Uri.parse("tel:")
                                    }
                                    val dialIntent = Intent(Intent.ACTION_DIAL, dialUri).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    runCatching { context.startActivity(dialIntent) }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(23.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "전화하기",
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // 2) 문자하기 아이콘 버튼 -> 문자 앱으로 이동
                            Button(
                                onClick = {
                                    val smsUri = if (target.phoneNumber.isNotBlank()) {
                                        Uri.parse("smsto:${target.phoneNumber}")
                                    } else {
                                        Uri.parse("smsto:")
                                    }
                                    val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    runCatching { context.startActivity(smsIntent) }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(23.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = "문자하기",
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // 3) 알림보내기 아이콘 버튼 -> 알림 메시지 작성 다이얼로그 오픈
                            Button(
                                onClick = {
                                    notificationMessageInput = ""
                                    notificationTargetMember = target
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(23.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "알림보내기",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 7. 하단 설정 및 위치 공유 시트 (설정 아이콘 클릭 시에만 열림)
        AnimatedVisibility(
            visible = isSettingsPanelOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            Card(
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.98f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (selectedMember != null) {
                            val isSelectedOffline = isMemberDisconnected(selectedMember)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Image(
                                    painter = painterResource(id = AvatarCatalog.getDrawableRes(selectedMember.avatarCode)),
                                    contentDescription = selectedMember.nickname,
                                    colorFilter = if (isSelectedOffline) {
                                        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.2f) })
                                    } else null,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = 2.dp,
                                            color = if (isSelectedOffline) Color(0xFFDC2626) else Color(0xFF2563EB),
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = selectedMember.nickname,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (isSelectedOffline) Color(0xFFDC2626) else Color(0xFF10B981)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (isSelectedOffline) "연결 끊김" else "연결됨",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    val coordText = if (selectedMember.lastLat != null && selectedMember.lastLng != null) {
                                        String.format(
                                            "%.5f, %.5f · %s (%s)",
                                            selectedMember.lastLat,
                                            selectedMember.lastLng,
                                            formatActivityLabel(selectedMember.activityType),
                                            formatRelativeTime(selectedMember.lastUpdatedAt)
                                        )
                                    } else {
                                        "위치 수신 기록 없음 (연결 끊김)"
                                    }
                                    Text(
                                        text = coordText,
                                        fontSize = 11.sp,
                                        color = if (isSelectedOffline) Color(0xFFB91C1C) else Color(0xFF475569)
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "위치 및 공유 설정",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onEditProfile) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "별칭 및 아바타 변경",
                                    tint = Color(0xFF475569)
                                )
                            }
                            IconButton(onClick = { isSettingsPanelOpen = false }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "설정 닫기",
                                    tint = Color(0xFF475569)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "최근 1일(24시간) 이동 경로 보기",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = "DB 보관 기록: ${historyPoints24h.size}개 지점 (24시간 경과 시 자동 삭제)",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        Switch(
                            checked = show24hTrail,
                            onCheckedChange = onToggle24hTrail
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "저전력 백그라운드 위치 공유 (무음 상태 알림)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = "50m 이상 이동 시에만 전송 · 배터리 15% 이하 자동 절전",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        Switch(
                            checked = backgroundSharingEnabled,
                            onCheckedChange = onToggleBackgroundSharing
                        )
                    }

                    if (!statusMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = statusMessage,
                            fontSize = 12.sp,
                            color = Color(0xFF2563EB)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 하단 버튼 영역: [공유] [새로고침] [가족관리]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSendMyLocationNow,
                            enabled = !isLoading,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("공유", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = onRefreshFamilyLocations,
                            enabled = !isLoading,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("새로고침", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                passwordInput = ""
                                passwordError = null
                                isPasswordDialogOpen = true
                            },
                            enabled = !isLoading,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ManageAccounts,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("가족관리", fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // 8. 알림보내기 메시지 작성 다이얼로그
        val notiTarget = notificationTargetMember
        if (notiTarget != null) {
            val presets = listOf("지금 어디야?", "집으로 오세요", "연락 부탁해요", "식사하러 오세요")
            AlertDialog(
                onDismissRequest = { notificationTargetMember = null },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = Color(0xFF7C3AED)
                    )
                },
                title = {
                    Text(
                        text = "'${notiTarget.nickname}'님에게 알림보내기",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "전송할 알림 메시지를 입력하거나 빠른 문구를 선택해 주세요.",
                            fontSize = 13.sp,
                            color = Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(presets) { preset ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFFF3E8FF),
                                    modifier = Modifier.clickable {
                                        notificationMessageInput = preset
                                    }
                                ) {
                                    Text(
                                        text = preset,
                                        fontSize = 12.sp,
                                        color = Color(0xFF6B21A8),
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = notificationMessageInput,
                            onValueChange = { if (it.length <= 28) notificationMessageInput = it },
                            label = { Text("알림 메시지 입력 (최대 28자)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val msg = notificationMessageInput.trim()
                            if (msg.isNotBlank()) {
                                onSendNotificationToMember(notiTarget, msg)
                                notificationTargetMember = null
                            }
                        },
                        enabled = notificationMessageInput.trim().isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                    ) {
                        Text("보내기")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { notificationTargetMember = null }) {
                        Text("취소")
                    }
                }
            )
        }

        // 9. 가족으로부터 도착한 알림 메시지 수신 팝업 다이얼로그
        if (incomingNotification != null) {
            AlertDialog(
                onDismissRequest = onDismissIncomingNotification,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = Color(0xFF2563EB)
                    )
                },
                title = {
                    Text(
                        text = "${incomingNotification.senderNickname}님의 가족 알림",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = incomingNotification.message,
                        fontSize = 16.sp,
                        color = Color(0xFF0F172A)
                    )
                },
                confirmButton = {
                    Button(onClick = onDismissIncomingNotification) {
                        Text("확인")
                    }
                }
            )
        }

        // 10. 가족관리 비밀번호 인증 다이얼로그 (고정 패스워드: "family")
        if (isPasswordDialogOpen) {
            AlertDialog(
                onDismissRequest = { isPasswordDialogOpen = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFF2563EB)
                    )
                },
                title = {
                    Text(
                        text = "가족관리 비밀번호 인증",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "등록된 가족을 관리 및 삭제하려면 비밀번호를 입력해 주세요.",
                            fontSize = 13.sp,
                            color = Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = {
                                passwordInput = it
                                passwordError = null
                            },
                            label = { Text("비밀번호 입력") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (!passwordError.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = passwordError!!,
                                color = Color(0xFFDC2626),
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (passwordInput == "family") {
                                isPasswordDialogOpen = false
                                isFamilyManageDialogOpen = true
                            } else {
                                passwordError = "비밀번호가 일치하지 않습니다."
                            }
                        }
                    ) {
                        Text("확인")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isPasswordDialogOpen = false }) {
                        Text("취소")
                    }
                }
            )
        }

        // 11. 인증 완료 후 열리는 가족관리(삭제) 다이얼로그
        if (isFamilyManageDialogOpen) {
            AlertDialog(
                onDismissRequest = { isFamilyManageDialogOpen = false },
                title = {
                    Text(
                        text = "가족관리 (등록된 가족 ${familyMembers.size}명)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "삭제할 가족 구성원의 [삭제] 버튼을 누르면 DB와 지도에서 즉시 제거됩니다.",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                        ) {
                            items(familyMembers, key = { it.id }) { member ->
                                val isOffline = isMemberDisconnected(member)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFF8FAFC))
                                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = AvatarCatalog.getDrawableRes(member.avatarCode)),
                                        contentDescription = member.nickname,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (member.id == myMemberId) "${member.nickname} (나)" else member.nickname,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E293B)
                                        )
                                        Text(
                                            text = if (isOffline) {
                                                "연결끊김 · ${formatRelativeTime(member.lastUpdatedAt)}"
                                            } else {
                                                "연결됨 · ${formatRelativeTime(member.lastUpdatedAt)}"
                                            },
                                            fontSize = 11.sp,
                                            color = if (isOffline) Color(0xFFDC2626) else Color(0xFF64748B)
                                        )
                                    }
                                    Button(
                                        onClick = { onDeleteFamilyMember(member) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFDC2626)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "삭제",
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("삭제", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { isFamilyManageDialogOpen = false }) {
                        Text("닫기")
                    }
                }
            )
        }
    }
}

private fun isMemberDisconnected(member: FamilyMember): Boolean {
    if (member.lastLat == null || member.lastLng == null) return true
    val iso = member.lastUpdatedAt ?: return true
    return runCatching {
        val updatedInstant = Instant.parse(iso)
        val elapsedMinutes = Duration.between(updatedInstant, Instant.now()).toMinutes()
        elapsedMinutes >= 35
    }.getOrDefault(true)
}

private fun formatActivityLabel(activityType: String?): String {
    return when (activityType) {
        "WALKING" -> "도보 이동 중"
        "IN_VEHICLE" -> "차량 이동 중"
        else -> "체류/정지 상태"
    }
}

private fun formatRelativeTime(isoString: String?): String {
    if (isoString.isNullOrBlank()) return "기록 없음"
    return runCatching {
        val instant = Instant.parse(isoString)
        val minutes = Duration.between(instant, Instant.now()).toMinutes()
        when {
            minutes < 1 -> "방금 전"
            minutes < 60 -> "${minutes}분 전"
            minutes < 1440 -> "${minutes / 60}시간 전"
            else -> "${minutes / 1440}일 전"
        }
    }.getOrDefault("방금 전")
}
