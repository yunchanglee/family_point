package com.familypoint.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.familypoint.app.data.SupabaseRepository
import com.familypoint.app.data.UserPreferences
import com.familypoint.app.model.FamilyMember
import com.familypoint.app.model.FamilyNotificationMessage
import com.familypoint.app.model.LocationHistoryPoint
import com.familypoint.app.service.LocationHelper
import com.familypoint.app.service.LocationSyncService
import com.familypoint.app.service.LocationSyncWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MainUiState(
    val isOnboarding: Boolean = true,
    val isLoading: Boolean = false,
    val myMemberId: String? = null,
    val groupId: String? = null,
    val inviteCode: String = "FAMILY",
    val nickname: String = "",
    val avatarCode: String = "avatar_01",
    val phoneNumber: String = "",
    val familyMembers: List<FamilyMember> = emptyList(),
    val selectedMember: FamilyMember? = null,
    val historyPoints24h: List<LocationHistoryPoint> = emptyList(),
    val show24hTrail: Boolean = true,
    val backgroundSharingEnabled: Boolean = true,
    val incomingNotification: FamilyNotificationMessage? = null,
    val errorMessage: String? = null,
    val statusMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = UserPreferences(application)
    private val repository = SupabaseRepository()

    private val _uiState = MutableStateFlow(
        MainUiState(
            isOnboarding = !prefs.isProfileConfigured,
            myMemberId = prefs.memberId,
            groupId = prefs.groupId,
            inviteCode = prefs.inviteCode,
            nickname = prefs.nickname,
            avatarCode = prefs.avatarCode,
            phoneNumber = prefs.phoneNumber,
            backgroundSharingEnabled = prefs.backgroundSharingEnabled
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var autoRefreshJob: Job? = null

    init {
        if (prefs.isProfileConfigured) {
            if (prefs.backgroundSharingEnabled) {
                LocationSyncWorker.schedulePeriodic(application)
                LocationSyncService.start(application)
            }
            viewModelScope.launch {
                val groupId = prefs.groupId
                if (!groupId.isNullOrBlank()) {
                    val combinedAvatar = combineAvatarAndPhone(prefs.avatarCode, prefs.phoneNumber)
                    repository.registerOrUpdateMember(
                        groupId = groupId,
                        deviceUid = prefs.deviceUid,
                        nickname = prefs.nickname,
                        avatarCode = combinedAvatar,
                        batteryLevel = LocationHelper.getBatteryLevel(getApplication())
                    )
                }
                refreshFamilyLocations()
            }
            startForegroundAutoRefreshLoop()
        }
    }

    private fun combineAvatarAndPhone(avatarCode: String, phoneNumber: String): String {
        val base = avatarCode.substringBefore("|").take(9).ifBlank { "avatar_01" }
        val cleanPhone = phoneNumber.trim()
        return if (cleanPhone.isNotBlank()) "$base|$cleanPhone" else base
    }

    private fun startForegroundAutoRefreshLoop() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000L)
                if (!_uiState.value.isOnboarding) {
                    refreshFamilyLocations(silent = true)
                    checkIncomingNotifications()
                }
            }
        }
    }

    private suspend fun checkIncomingNotifications() {
        val myId = prefs.memberId ?: return
        val res = repository.consumePendingNotifications(myId)
        val list = res.getOrNull().orEmpty()
        if (list.isNotEmpty()) {
            _uiState.update { it.copy(incomingNotification = list.last()) }
        }
    }

    fun dismissIncomingNotification() {
        _uiState.update { it.copy(incomingNotification = null) }
    }

    fun saveProfile(nickname: String, avatarCode: String, phoneNumber: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val context = getApplication<Application>()
            val battery = LocationHelper.getBatteryLevel(context)

            val groupRes = repository.getOrCreateFamilyGroup("FAMILY")
            val group = groupRes.getOrElse { err ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = err.message)
                }
                return@launch
            }

            val combinedAvatar = combineAvatarAndPhone(avatarCode, phoneNumber)
            val memberRes = repository.registerOrUpdateMember(
                groupId = group.id,
                deviceUid = prefs.deviceUid,
                nickname = nickname,
                avatarCode = combinedAvatar,
                batteryLevel = battery
            )
            val member = memberRes.getOrElse { err ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = err.message)
                }
                return@launch
            }

            prefs.groupId = group.id
            prefs.memberId = member.id
            prefs.inviteCode = group.inviteCode
            prefs.nickname = member.nickname
            prefs.avatarCode = member.cleanAvatarCode
            prefs.phoneNumber = phoneNumber.trim()
            prefs.backgroundSharingEnabled = true

            LocationSyncWorker.schedulePeriodic(context)
            LocationSyncService.start(context)

            _uiState.update {
                it.copy(
                    isOnboarding = false,
                    isLoading = false,
                    myMemberId = member.id,
                    groupId = group.id,
                    inviteCode = group.inviteCode,
                    nickname = member.nickname,
                    avatarCode = member.cleanAvatarCode,
                    phoneNumber = phoneNumber.trim(),
                    backgroundSharingEnabled = true,
                    selectedMember = member,
                    errorMessage = null,
                    statusMessage = "프로필이 저장되었으며 백그라운드 위치 공유가 활성화되었습니다."
                )
            }

            sendMyLocationNow()
            refreshFamilyLocations()
            startForegroundAutoRefreshLoop()
        }
    }

    fun openProfileEditor() {
        _uiState.update { it.copy(isOnboarding = true, errorMessage = null) }
    }

    fun sendNotificationToMember(targetMember: FamilyMember, message: String) {
        viewModelScope.launch {
            val senderName = prefs.nickname.ifBlank { "가족" }
            val res = repository.sendFamilyNotification(
                targetMemberId = targetMember.id,
                senderNickname = senderName,
                message = message
            )
            res.onSuccess {
                _uiState.update {
                    it.copy(statusMessage = "'${targetMember.nickname}'님에게 알림을 전송했습니다.")
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(statusMessage = "알림 전송 실패: ${err.message}")
                }
            }
        }
    }

    fun deleteFamilyMember(member: FamilyMember) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val res = repository.deleteFamilyMember(member.id)
            res.onSuccess {
                if (member.id == prefs.memberId) {
                    prefs.memberId = null
                    LocationSyncWorker.cancelAll(getApplication())
                    LocationSyncService.stop(getApplication())
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isOnboarding = true,
                            myMemberId = null,
                            statusMessage = "내 프로필이 삭제되었습니다."
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "'${member.nickname}' 가족이 삭제되었습니다."
                        )
                    }
                    refreshFamilyLocations(silent = true)
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isLoading = false, statusMessage = "삭제 실패: ${err.message}")
                }
            }
        }
    }

    fun selectMember(member: FamilyMember) {
        _uiState.update { it.copy(selectedMember = member) }
        loadSelectedMember24hHistory(member.id)
    }

    fun toggle24hTrail(enabled: Boolean) {
        _uiState.update { it.copy(show24hTrail = enabled) }
        if (enabled) {
            _uiState.value.selectedMember?.id?.let { loadSelectedMember24hHistory(it) }
        }
    }

    fun toggleBackgroundSharing(enabled: Boolean) {
        val context = getApplication<Application>()
        prefs.backgroundSharingEnabled = enabled
        _uiState.update {
            it.copy(
                backgroundSharingEnabled = enabled,
                statusMessage = if (enabled) {
                    "저전력 백그라운드 위치 공유가 시작되었습니다."
                } else {
                    "백그라운드 위치 공유가 중지되었습니다."
                }
            )
        }
        if (enabled) {
            LocationSyncWorker.schedulePeriodic(context)
            LocationSyncService.start(context)
        } else {
            LocationSyncWorker.cancelAll(context)
            LocationSyncService.stop(context)
        }
    }

    fun sendMyLocationNow() {
        val memberId = prefs.memberId ?: return
        val groupId = prefs.groupId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "현재 위치 측정 및 전송 중...") }
            val context = getApplication<Application>()
            val loc = LocationHelper.getCurrentLocationBalanced(context)
            if (loc == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "위치 권한을 확인하거나 GPS가 켜져 있는지 확인해 주세요."
                    )
                }
                return@launch
            }

            val battery = LocationHelper.getBatteryLevel(context)
            val activityType = LocationHelper.classifyActivityFromSpeed(loc)

            val res = repository.reportLocation(
                memberId = memberId,
                groupId = groupId,
                latitude = loc.latitude,
                longitude = loc.longitude,
                accuracy = loc.accuracy,
                batteryLevel = battery,
                activityType = activityType
            )

            res.onSuccess {
                prefs.recordSentLocation(loc.latitude, loc.longitude)
                _uiState.update {
                    it.copy(isLoading = false, statusMessage = "내 최신 위치를 가족 지도에 업데이트했습니다.")
                }
                refreshFamilyLocations(silent = true)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isLoading = false, statusMessage = err.message)
                }
            }
        }
    }

    fun refreshFamilyLocations(silent: Boolean = false) {
        val groupId = prefs.groupId ?: return
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(isLoading = true) }
            }
            val res = repository.fetchFamilyMembers(groupId)
            res.onSuccess { members ->
                val currentSelectedId = _uiState.value.selectedMember?.id ?: prefs.memberId
                val nextSelected = members.firstOrNull { it.id == currentSelectedId }
                    ?: members.firstOrNull()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        familyMembers = members,
                        selectedMember = nextSelected,
                        statusMessage = if (silent) it.statusMessage else "가족 ${members.size}명의 위치를 불러왔습니다."
                    )
                }
                if (nextSelected != null && _uiState.value.show24hTrail) {
                    loadSelectedMember24hHistory(nextSelected.id)
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isLoading = false, statusMessage = err.message)
                }
            }
        }
    }

    private fun loadSelectedMember24hHistory(memberId: String) {
        viewModelScope.launch {
            val res = repository.fetchMemberHistory24h(memberId)
            res.onSuccess { points ->
                _uiState.update { it.copy(historyPoints24h = points) }
            }
        }
    }
}
