package com.familypoint.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

object LocationHelper {

    fun getBatteryLevel(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        if (level in 1..100) return level

        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val rawLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (rawLevel >= 0 && scale > 0) (rawLevel * 100) / scale else 100
    }

    fun isBatteryCriticalAndUnplugged(context: Context): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val level = getBatteryLevel(context)
        return !isCharging && level <= 15
    }

    fun classifyActivityFromSpeed(location: Location): String {
        if (!location.hasSpeed()) return "STILL"
        val speedKmh = location.speed * 3.6f
        return when {
            speedKmh < 1.5f -> "STILL"
            speedKmh < 12.0f -> "WALKING"
            else -> "IN_VEHICLE"
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocationBalanced(context: Context): Location? {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)

        // 1) 저전력 Balanced 위치 시도
        val balanced = runCatching {
            withTimeoutOrNull(3500L) {
                val cts = CancellationTokenSource()
                fusedClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cts.token
                ).await()
            }
        }.getOrNull()
        if (balanced != null) return balanced

        // 2) Fused lastLocation 또는 High Accuracy 폴백 (실내/에뮬레이터 대응)
        val highOrLast = runCatching {
            fusedClient.lastLocation.await() ?: withTimeoutOrNull(3500L) {
                val cts = CancellationTokenSource()
                fusedClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cts.token
                ).await()
            }
        }.getOrNull()
        if (highOrLast != null) return highOrLast

        // 3) Android LocationManager GPS/Network Provider 폴백
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm != null) {
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            for (provider in providers) {
                val loc = runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
                if (loc != null) return loc
            }
        }
        return null
    }
}
