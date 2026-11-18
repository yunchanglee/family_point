package com.familypoint.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.familypoint.app.data.UserPreferences

/**
 * 단말 재부팅(BOOT_COMPLETED) 또는 앱 업데이트(MY_PACKAGE_REPLACED) 후에도
 * 자동으로 위치 공유 서비스와 WorkManager 백그라운드 작업을 재개합니다.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == ACTION_RESTART_SERVICE
        ) {
            val prefs = UserPreferences(context)
            if (prefs.isProfileConfigured && prefs.backgroundSharingEnabled) {
                LocationSyncWorker.schedulePeriodic(context)
                LocationSyncService.start(context)
            }
        }
    }

    companion object {
        const val ACTION_RESTART_SERVICE = "com.familypoint.app.action.RESTART_LOCATION_SERVICE"
    }
}
