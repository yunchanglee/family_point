package com.familypoint.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FamilyGroup(
    val id: String,
    @SerialName("invite_code") val inviteCode: String,
    @SerialName("group_name") val groupName: String = "우리 가족",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class FamilyMember(
    val id: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("device_uid") val deviceUid: String,
    val nickname: String,
    @SerialName("avatar_code") val avatarCode: String,
    @SerialName("battery_level") val batteryLevel: Int = 100,
    @SerialName("last_lat") val lastLat: Double? = null,
    @SerialName("last_lng") val lastLng: Double? = null,
    @SerialName("last_accuracy") val lastAccuracy: Float? = null,
    @SerialName("activity_type") val activityType: String? = "STILL",
    @SerialName("last_updated_at") val lastUpdatedAt: String? = null
) {
    val cleanAvatarCode: String
        get() = avatarCode.substringBefore("|")

    val phoneNumber: String
        get() = avatarCode.substringAfter("|", "").trim()
}

@Serializable
data class LocationHistoryPoint(
    val id: Long? = null,
    @SerialName("member_id") val memberId: String,
    @SerialName("group_id") val groupId: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("accuracy_m") val accuracyM: Float? = null,
    @SerialName("activity_type") val activityType: String? = "STILL",
    @SerialName("recorded_at") val recordedAt: String? = null
)

@Serializable
data class ReportLocationRpcPayload(
    @SerialName("p_member_id") val memberId: String,
    @SerialName("p_lat") val lat: Double,
    @SerialName("p_lng") val lng: Double,
    @SerialName("p_accuracy") val accuracy: Float,
    @SerialName("p_battery") val battery: Int,
    @SerialName("p_activity") val activity: String
)

data class FamilyNotificationMessage(
    val id: String,
    val senderNickname: String,
    val message: String,
    val createdAt: String? = null
)
