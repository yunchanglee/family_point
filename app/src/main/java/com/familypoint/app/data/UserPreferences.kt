package com.familypoint.app.data

import android.content.Context
import android.location.Location
import java.util.UUID

class UserPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("family_point_prefs", Context.MODE_PRIVATE)

    var deviceUid: String
        get() {
            var uid = prefs.getString(KEY_DEVICE_UID, null)
            if (uid.isNullOrBlank()) {
                uid = UUID.randomUUID().toString()
                prefs.edit().putString(KEY_DEVICE_UID, uid).apply()
            }
            return uid
        }
        set(value) = prefs.edit().putString(KEY_DEVICE_UID, value).apply()

    var memberId: String?
        get() = prefs.getString(KEY_MEMBER_ID, null)
        set(value) = prefs.edit().putString(KEY_MEMBER_ID, value).apply()

    var groupId: String?
        get() = prefs.getString(KEY_GROUP_ID, null)
        set(value) = prefs.edit().putString(KEY_GROUP_ID, value).apply()

    var inviteCode: String
        get() = prefs.getString(KEY_INVITE_CODE, "FAMILY") ?: "FAMILY"
        set(value) = prefs.edit().putString(KEY_INVITE_CODE, value.trim().uppercase()).apply()

    var nickname: String
        get() = prefs.getString(KEY_NICKNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NICKNAME, value.trim()).apply()

    var avatarCode: String
        get() = prefs.getString(KEY_AVATAR_CODE, "avatar_01") ?: "avatar_01"
        set(value) = prefs.edit().putString(KEY_AVATAR_CODE, value).apply()

    var phoneNumber: String
        get() = prefs.getString(KEY_PHONE_NUMBER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PHONE_NUMBER, value.trim()).apply()

    var backgroundSharingEnabled: Boolean
        get() = prefs.getBoolean(KEY_BG_SHARING, true)
        set(value) = prefs.edit().putBoolean(KEY_BG_SHARING, value).apply()

    val isProfileConfigured: Boolean
        get() = !memberId.isNullOrBlank() && !groupId.isNullOrBlank() && nickname.isNotBlank()

    fun shouldSendLocation(newLat: Double, newLng: Double, forceSend: Boolean = false): Boolean {
        if (forceSend) return true
        val prevLat = prefs.getString(KEY_LAST_LAT, null)?.toDoubleOrNull()
        val prevLng = prefs.getString(KEY_LAST_LNG, null)?.toDoubleOrNull()
        val prevTime = prefs.getLong(KEY_LAST_SENT_MS, 0L)
        val now = System.currentTimeMillis()

        // 최소 30분이 지났으면 정지 상태여도 생존 신호(하트비트) 전송
        if (prevLat == null || prevLng == null || (now - prevTime) >= 30 * 60 * 1000L) {
            return true
        }

        val results = FloatArray(1)
        Location.distanceBetween(prevLat, prevLng, newLat, newLng, results)
        // 배터리 및 데이터 절약: 30m 이상 이동했을 때만 DB 전송
        return results[0] >= 30f
    }

    fun recordSentLocation(lat: Double, lng: Double) {
        prefs.edit()
            .putString(KEY_LAST_LAT, lat.toString())
            .putString(KEY_LAST_LNG, lng.toString())
            .putLong(KEY_LAST_SENT_MS, System.currentTimeMillis())
            .apply()
    }

    companion object {
        private const val KEY_DEVICE_UID = "device_uid"
        private const val KEY_MEMBER_ID = "member_id"
        private const val KEY_GROUP_ID = "group_id"
        private const val KEY_INVITE_CODE = "invite_code"
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_AVATAR_CODE = "avatar_code"
        private const val KEY_PHONE_NUMBER = "phone_number"
        private const val KEY_BG_SHARING = "bg_sharing_enabled"
        private const val KEY_LAST_LAT = "last_lat"
        private const val KEY_LAST_LNG = "last_lng"
        private const val KEY_LAST_SENT_MS = "last_sent_ms"
    }
}
