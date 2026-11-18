package com.familypoint.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.familypoint.app.data.SupabaseRepository
import com.familypoint.app.data.UserPreferences
import com.google.android.gms.location.LocationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Google Play Services FusedLocationProviderClient가 PendingIntent 방식으로 호출하는 브로드캐스트 리시버.
 * 앱이 최근 실행 목록(Recents)에서 스와이프 종료되더라도 OS(Google Play Services)가
 * 이동 감지 시 이 리시버를 깨워 위치를 Supabase로 전송합니다.
 */
class LocationUpdatesReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val prefs = UserPreferences(context)
        if (!prefs.isProfileConfigured || !prefs.backgroundSharingEnabled) return

        val memberId = prefs.memberId ?: return
        val groupId = prefs.groupId ?: return

        if (!LocationResult.hasResult(intent)) return
        val result = LocationResult.extractResult(intent) ?: return
        val location = result.lastLocation ?: return

        if (LocationHelper.isBatteryCriticalAndUnplugged(context)) return
        if (!prefs.shouldSendLocation(location.latitude, location.longitude, forceSend = false)) {
            return
        }

        val pendingResult = goAsync()
        val battery = LocationHelper.getBatteryLevel(context)
        val activityType = LocationHelper.classifyActivityFromSpeed(location)
        val repository = SupabaseRepository()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val res = repository.reportLocation(
                    memberId = memberId,
                    groupId = groupId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    batteryLevel = battery,
                    activityType = activityType
                )
                if (res.isSuccess) {
                    prefs.recordSentLocation(location.latitude, location.longitude)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_PROCESS_UPDATES = "com.familypoint.app.action.PROCESS_LOCATION_UPDATES"
    }
}
