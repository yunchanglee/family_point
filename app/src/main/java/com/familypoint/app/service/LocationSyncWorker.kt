package com.familypoint.app.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.familypoint.app.data.SupabaseRepository
import com.familypoint.app.data.UserPreferences
import java.util.concurrent.TimeUnit

/**
 * 앱이 백그라운드에서 완전히 종료되더라도 OS(WorkManager)에 의해 주기적으로 실행되어
 * 최신 위치를 Supabase에 전송하고 포그라운드 위치 공유 서비스를 유지합니다.
 */
class LocationSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = UserPreferences(applicationContext)
        if (!prefs.isProfileConfigured || !prefs.backgroundSharingEnabled) {
            return Result.success()
        }

        val memberId = prefs.memberId ?: return Result.success()
        val groupId = prefs.groupId ?: return Result.success()

        // 포그라운드 서비스가 종료되어 있다면 다시 기동 시도
        runCatching {
            LocationSyncService.start(applicationContext)
        }

        if (LocationHelper.isBatteryCriticalAndUnplugged(applicationContext)) {
            return Result.success()
        }

        val loc = LocationHelper.getCurrentLocationBalanced(applicationContext)
        if (loc != null) {
            val battery = LocationHelper.getBatteryLevel(applicationContext)
            val activityType = LocationHelper.classifyActivityFromSpeed(loc)
            val repo = SupabaseRepository()
            val res = repo.reportLocation(
                memberId = memberId,
                groupId = groupId,
                latitude = loc.latitude,
                longitude = loc.longitude,
                accuracy = loc.accuracy,
                batteryLevel = battery,
                activityType = activityType
            )
            if (res.isSuccess) {
                prefs.recordSentLocation(loc.latitude, loc.longitude)
            }
        }
        return Result.success()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "family_point_periodic_location_sync"
        private const val IMMEDIATE_WORK_NAME = "family_point_immediate_location_sync"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<LocationSyncWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun enqueueImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<LocationSyncWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE_WORK_NAME)
        }
    }
}
