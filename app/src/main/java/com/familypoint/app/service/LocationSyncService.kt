package com.familypoint.app.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.familypoint.app.MainActivity
import com.familypoint.app.R
import com.familypoint.app.data.SupabaseRepository
import com.familypoint.app.data.UserPreferences
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 앱이 최근 실행 목록(Recents)에서 종료되더라도 계속해서 위치를 전송하는 영속형 위치 공유 서비스.
 * - AndroidManifest에 android:stopWithTask="false" 설정
 * - onTaskRemoved() 시 AlarmManager + WorkManager + PendingIntent 3중 복구 적용
 * - Google Play Services PendingIntent(LocationUpdatesReceiver)를 함께 등록하여 프로세스 종료 시에도 위치 수신 보장
 */
class LocationSyncService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var prefs: UserPreferences
    private val repository = SupabaseRepository()
    private var heartbeatJob: Job? = null
    private var notificationPollJob: Job? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            handleNewLocation(loc, forceSend = false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        prefs = UserPreferences(this)
        createLowDistractionNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            prefs.backgroundSharingEnabled = false
            stopLocationUpdates()
            LocationSyncWorker.cancelAll(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildLowDistractionNotification()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }

        LocationSyncWorker.schedulePeriodic(this)
        startBalancedLocationUpdates()
        startHeartbeatLoop()
        startFamilyNotificationPollLoop()
        return START_STICKY
    }

    /**
     * 사용자가 최근 앱 목록(Recents)에서 앱을 스와이프해 종료했을 때 호출됨.
     * 서비스가 중단되지 않도록 즉시 WorkManager 및 AlarmManager로 재기동 예약.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (prefs.isProfileConfigured && prefs.backgroundSharingEnabled) {
            LocationSyncWorker.schedulePeriodic(applicationContext)
            LocationSyncWorker.enqueueImmediate(applicationContext)
            scheduleRestartAlarm(applicationContext)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBalancedLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            5 * 60 * 1000L // 기본 5분 간격
        )
            .setMinUpdateIntervalMillis(2 * 60 * 1000L) // 최소 2분 간격
            .setMinUpdateDistanceMeters(30f)            // 30m 이상 이동 시 즉시 콜백
            .setWaitForAccurateLocation(false)
            .build()

        // 1) 인프로세스 콜백 등록
        runCatching {
            fusedClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
        }

        // 2) 앱 프로세스가 죽어도 Google Play Services가 깨워주는 PendingIntent 리시버 등록
        runCatching {
            fusedClient.requestLocationUpdates(
                request,
                getLocationReceiverPendingIntent(this)
            )
        }
    }

    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                if (prefs.isProfileConfigured && prefs.backgroundSharingEnabled) {
                    val loc = LocationHelper.getCurrentLocationBalanced(this@LocationSyncService)
                    if (loc != null) {
                        handleNewLocation(loc, forceSend = false)
                    }
                }
                delay(5 * 60 * 1000L) // 5분마다 체크 (30m 이동 또는 30분 생존신호 기준 자동 필터링)
            }
        }
    }

    private fun startFamilyNotificationPollLoop() {
        notificationPollJob?.cancel()
        notificationPollJob = serviceScope.launch {
            while (isActive) {
                val myMemberId = prefs.memberId
                if (!myMemberId.isNullOrBlank()) {
                    val res = repository.consumePendingNotifications(myMemberId)
                    res.getOrNull()?.forEach { msg ->
                        showIncomingFamilyMessageNotification(msg.senderNickname, msg.message)
                    }
                }
                delay(12_000L)
            }
        }
    }

    private fun stopLocationUpdates() {
        heartbeatJob?.cancel()
        notificationPollJob?.cancel()
        runCatching {
            fusedClient.removeLocationUpdates(locationCallback)
            fusedClient.removeLocationUpdates(getLocationReceiverPendingIntent(this))
        }
    }

    private fun handleNewLocation(location: Location, forceSend: Boolean) {
        val memberId = prefs.memberId ?: return
        val groupId = prefs.groupId ?: return

        if (LocationHelper.isBatteryCriticalAndUnplugged(this)) {
            return
        }

        if (!prefs.shouldSendLocation(location.latitude, location.longitude, forceSend = forceSend)) {
            return
        }

        val battery = LocationHelper.getBatteryLevel(this)
        val activityType = LocationHelper.classifyActivityFromSpeed(location)

        serviceScope.launch {
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
        }
    }

    private fun getLocationReceiverPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, LocationUpdatesReceiver::class.java).apply {
            action = LocationUpdatesReceiver.ACTION_PROCESS_UPDATES
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, 2001, intent, flags)
    }

    private fun scheduleRestartAlarm(context: Context) {
        runCatching {
            val restartIntent = Intent(context, BootCompletedReceiver::class.java).apply {
                action = BootCompletedReceiver.ACTION_RESTART_SERVICE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                3001,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 2000L,
                pendingIntent
            )
        }
    }

    private fun createLowDistractionNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "가족 위치 공유 상태 (조용한 알림)",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "가족과 위치를 공유하는 동안 방해 없이 상태를 표시합니다."
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)

        val messageChannel = NotificationChannel(
            MESSAGE_CHANNEL_ID,
            "가족 알림 메시지",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "가족 구성원이 보낸 알림 메시지를 수신합니다."
            enableVibration(true)
        }
        manager.createNotificationChannel(messageChannel)
    }

    private fun showIncomingFamilyMessageNotification(senderNickname: String, message: String) {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this,
            (System.currentTimeMillis() % 10000).toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val noti = NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.avatar_01)
            .setContentTitle("가족 알림 · ${senderNickname}님")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify((System.currentTimeMillis() % 100000).toInt() + 5000, noti)
    }

    private fun buildLowDistractionNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocationSyncService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.avatar_01)
            .setContentTitle("우리 가족 위치 공유 중 (${prefs.nickname})")
            .setContentText("앱을 닫아도 저전력 모드로 가족에게 위치를 전송합니다.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openPending)
            .addAction(0, "공유 중지", stopPending)
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        if (prefs.isProfileConfigured && prefs.backgroundSharingEnabled) {
            scheduleRestartAlarm(applicationContext)
        } else {
            stopLocationUpdates()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "family_point_low_channel"
        private const val MESSAGE_CHANNEL_ID = "family_point_message_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.familypoint.app.ACTION_STOP_SHARING"

        fun start(context: Context) {
            val intent = Intent(context, LocationSyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching { context.startForegroundService(intent) }
                    .onFailure { LocationSyncWorker.enqueueImmediate(context) }
            } else {
                runCatching { context.startService(intent) }
            }
        }

        fun stop(context: Context) {
            val stopIntent = Intent(context, LocationSyncService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(stopIntent) }
            runCatching { context.stopService(Intent(context, LocationSyncService::class.java)) }
        }
    }
}
