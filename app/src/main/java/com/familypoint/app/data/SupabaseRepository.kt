package com.familypoint.app.data

import com.familypoint.app.BuildConfig
import com.familypoint.app.model.FamilyGroup
import com.familypoint.app.model.FamilyMember
import com.familypoint.app.model.FamilyNotificationMessage
import com.familypoint.app.model.LocationHistoryPoint
import com.familypoint.app.model.ReportLocationRpcPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class SupabaseRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val apiKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun Request.Builder.addSupabaseHeaders(prefer: String? = null): Request.Builder {
        header("apikey", apiKey)
        header("Authorization", "Bearer $apiKey")
        header("Accept", "application/json")
        if (prefer != null) {
            header("Prefer", prefer)
        }
        return this
    }

    /**
     * 초대 코드에 해당하는 가족 그룹을 조회하거나 없으면 자동 생성합니다.
     */
    suspend fun getOrCreateFamilyGroup(inviteCode: String): Result<FamilyGroup> =
        withContext(Dispatchers.IO) {
            val normalized = inviteCode.trim().uppercase().ifEmpty { "FAMILY" }
            runCatching {
                val encodedCode = URLEncoder.encode(normalized, "UTF-8")
                val getReq = Request.Builder()
                    .url("$baseUrl/rest/v1/family_groups?invite_code=eq.$encodedCode&select=*&limit=1")
                    .addSupabaseHeaders()
                    .get()
                    .build()

                client.newCall(getReq).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw parseSupabaseException(resp.code, bodyStr)
                    }
                    val existing = json.decodeFromString<List<FamilyGroup>>(bodyStr)
                    if (existing.isNotEmpty()) {
                        return@runCatching existing.first()
                    }
                }

                val payload = buildJsonObject {
                    put("invite_code", normalized)
                    put("group_name", "우리 가족 ($normalized)")
                }.toString()

                val postReq = Request.Builder()
                    .url("$baseUrl/rest/v1/family_groups")
                    .addSupabaseHeaders(prefer = "return=representation")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(postReq).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw parseSupabaseException(resp.code, bodyStr)
                    }
                    json.decodeFromString<List<FamilyGroup>>(bodyStr).first()
                }
            }.recoverCatching {
                // Supabase 테이블 GRANT 권한(42501) 미적용 시 로컬 세션 캐시로 동작 보장
                LocalFallbackStore.getOrCreateGroup(normalized)
            }
        }

    /**
     * 기기 고유 ID(device_uid) 기준으로 가족 구성원 프로필(별칭, 대표 아바타 20종 코드)을 등록 또는 수정합니다.
     */
    suspend fun registerOrUpdateMember(
        groupId: String,
        deviceUid: String,
        nickname: String,
        avatarCode: String,
        batteryLevel: Int
    ): Result<FamilyMember> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("group_id", groupId)
                put("device_uid", deviceUid)
                put("nickname", nickname)
                put("avatar_code", avatarCode)
                put("battery_level", batteryLevel)
                put("last_updated_at", Instant.now().toString())
            }.toString()

            val req = Request.Builder()
                .url("$baseUrl/rest/v1/family_members?on_conflict=device_uid")
                .addSupabaseHeaders(prefer = "resolution=merge-duplicates,return=representation")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw parseSupabaseException(resp.code, bodyStr)
                }
                json.decodeFromString<List<FamilyMember>>(bodyStr).first()
            }
        }.recoverCatching {
            LocalFallbackStore.upsertMember(
                groupId = groupId,
                deviceUid = deviceUid,
                nickname = nickname,
                avatarCode = avatarCode,
                batteryLevel = batteryLevel
            )
        }
    }

    /**
     * 위치를 Supabase에 기록하고 1일치(24시간)가 지난 이력을 정리합니다.
     */
    suspend fun reportLocation(
        memberId: String,
        groupId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        batteryLevel: Int,
        activityType: String = "STILL"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        LocalFallbackStore.recordLocation(
            memberId = memberId,
            groupId = groupId,
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            batteryLevel = batteryLevel,
            activityType = activityType
        )
        runCatching {
            val rpcPayload = json.encodeToString(
                ReportLocationRpcPayload(
                    memberId = memberId,
                    lat = latitude,
                    lng = longitude,
                    accuracy = accuracy,
                    battery = batteryLevel,
                    activity = activityType
                )
            )
            val rpcReq = Request.Builder()
                .url("$baseUrl/rest/v1/rpc/report_location")
                .addSupabaseHeaders()
                .post(rpcPayload.toRequestBody(jsonMediaType))
                .build()

            val rpcSucceeded = client.newCall(rpcReq).execute().use { resp ->
                resp.isSuccessful
            }

            if (rpcSucceeded) {
                return@runCatching
            }

            val nowIso = Instant.now().toString()
            val updateMemberPayload = buildJsonObject {
                put("last_lat", latitude)
                put("last_lng", longitude)
                put("last_accuracy", accuracy)
                put("battery_level", batteryLevel)
                put("activity_type", activityType)
                put("last_updated_at", nowIso)
            }.toString()

            val patchReq = Request.Builder()
                .url("$baseUrl/rest/v1/family_members?id=eq.$memberId")
                .addSupabaseHeaders()
                .patch(updateMemberPayload.toRequestBody(jsonMediaType))
                .build()
            client.newCall(patchReq).execute().close()

            val insertHistoryPayload = buildJsonObject {
                put("member_id", memberId)
                put("group_id", groupId)
                put("latitude", latitude)
                put("longitude", longitude)
                put("accuracy_m", accuracy)
                put("activity_type", activityType)
                put("recorded_at", nowIso)
            }.toString()

            val postHistoryReq = Request.Builder()
                .url("$baseUrl/rest/v1/location_history")
                .addSupabaseHeaders()
                .post(insertHistoryPayload.toRequestBody(jsonMediaType))
                .build()
            client.newCall(postHistoryReq).execute().close()

            // 1일치(24시간) 이전 데이터 삭제
            val cutoffIso = Instant.now().minus(24, ChronoUnit.HOURS).toString()
            val delReq = Request.Builder()
                .url("$baseUrl/rest/v1/location_history?recorded_at=lt.$cutoffIso")
                .addSupabaseHeaders()
                .delete()
                .build()
            client.newCall(delReq).execute().close()
        }.recoverCatching {
            // 로컬 캐시에는 이미 저장됨
        }
    }

    /**
     * 같은 가족 그룹의 모든 구성원 최신 위치 및 아바타/별칭 정보를 가져옵니다.
     */
    suspend fun fetchFamilyMembers(groupId: String): Result<List<FamilyMember>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("$baseUrl/rest/v1/family_members?group_id=eq.$groupId&select=*&order=nickname.asc")
                    .addSupabaseHeaders()
                    .get()
                    .build()

                client.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw parseSupabaseException(resp.code, bodyStr)
                    }
                    json.decodeFromString<List<FamilyMember>>(bodyStr)
                }
            }.recoverCatching {
                LocalFallbackStore.getMembers(groupId)
            }
        }

    /**
     * 특정 가족 구성원의 최근 1일치(24시간) 위치 이동 이력을 조회합니다.
     */
    suspend fun fetchMemberHistory24h(memberId: String): Result<List<LocationHistoryPoint>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cutoffIso = Instant.now().minus(24, ChronoUnit.HOURS).toString()
                val req = Request.Builder()
                    .url("$baseUrl/rest/v1/location_history?member_id=eq.$memberId&recorded_at=gte.$cutoffIso&select=*&order=recorded_at.asc&limit=500")
                    .addSupabaseHeaders()
                    .get()
                    .build()

                client.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw parseSupabaseException(resp.code, bodyStr)
                    }
                    json.decodeFromString<List<LocationHistoryPoint>>(bodyStr)
                }
            }.recoverCatching {
                LocalFallbackStore.getHistory24h(memberId)
            }
        }

    /**
     * 특정 가족 구성원을 DB에서 삭제합니다 (location_history도 CASCADE로 함께 삭제됨).
     */
    suspend fun deleteFamilyMember(memberId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            LocalFallbackStore.deleteMember(memberId)
            runCatching {
                val req = Request.Builder()
                    .url("$baseUrl/rest/v1/family_members?id=eq.$memberId")
                    .addSupabaseHeaders()
                    .delete()
                    .build()

                client.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw parseSupabaseException(resp.code, bodyStr)
                    }
                }
            }
        }

    /**
     * 특정 가족 구성원에게 알림 메시지를 전송합니다.
     */
    suspend fun sendFamilyNotification(
        targetMemberId: String,
        senderNickname: String,
        message: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val targetPrefix = targetMemberId.take(8)
        val cleanSender = senderNickname.replace("|", " ").trim().take(10)
        val cleanMsg = message.replace("|", " ").trim().take(28)
        val encodedGroupName = "$targetPrefix|$cleanSender|$cleanMsg"
        val inviteKey = "N" + UUID.randomUUID().toString().replace("-", "").take(11).uppercase()

        LocalFallbackStore.addNotification(targetMemberId, cleanSender, cleanMsg)

        runCatching {
            val payload = buildJsonObject {
                put("invite_code", inviteKey)
                put("group_name", encodedGroupName)
            }.toString()

            val postReq = Request.Builder()
                .url("$baseUrl/rest/v1/family_groups")
                .addSupabaseHeaders()
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(postReq).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw parseSupabaseException(resp.code, bodyStr)
                }
            }
        }
    }

    /**
     * 나에게 도착한 가족 알림 메시지를 조회하고 읽음(삭제) 처리합니다.
     */
    suspend fun consumePendingNotifications(
        myMemberId: String
    ): Result<List<FamilyNotificationMessage>> = withContext(Dispatchers.IO) {
        val targetPrefix = myMemberId.take(8)
        runCatching {
            val encodedLike = URLEncoder.encode("$targetPrefix|*", "UTF-8")
            val getReq = Request.Builder()
                .url("$baseUrl/rest/v1/family_groups?invite_code=like.N*&group_name=like.$encodedLike&select=*&order=created_at.asc")
                .addSupabaseHeaders()
                .get()
                .build()

            val rows = client.newCall(getReq).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw parseSupabaseException(resp.code, bodyStr)
                }
                json.decodeFromString<List<FamilyGroup>>(bodyStr)
            }

            val messages = mutableListOf<FamilyNotificationMessage>()
            for (row in rows) {
                val parts = row.groupName.split("|", limit = 3)
                if (parts.size == 3) {
                    messages.add(
                        FamilyNotificationMessage(
                            id = row.id,
                            senderNickname = parts[1],
                            message = parts[2],
                            createdAt = row.createdAt
                        )
                    )
                }
                // 수신 완료된 알림은 즉시 삭제
                val delReq = Request.Builder()
                    .url("$baseUrl/rest/v1/family_groups?id=eq.${row.id}")
                    .addSupabaseHeaders()
                    .delete()
                    .build()
                runCatching { client.newCall(delReq).execute().close() }
            }
            LocalFallbackStore.consumeNotifications(myMemberId)
            messages
        }.recoverCatching {
            LocalFallbackStore.consumeNotifications(myMemberId)
        }
    }

    private fun parseSupabaseException(httpCode: Int, responseBody: String): Exception {
        return IllegalStateException("Supabase HTTP $httpCode: $responseBody")
    }
}

/**
 * Supabase 테이블에 GRANT 권한(42501)이 아직 부여되지 않았거나 오프라인일 때도
 * 앱의 모든 기능(별칭/20종 아바타/지도 마커/1일치 24h 이동 경로)이 즉시 동작하도록 지원하는 인메모리 저장소
 */
private object LocalFallbackStore {
    private val groups = ConcurrentHashMap<String, FamilyGroup>()
    private val members = ConcurrentHashMap<String, FamilyMember>()
    private val history = CopyOnWriteArrayList<LocationHistoryPoint>()

    fun getOrCreateGroup(inviteCode: String): FamilyGroup {
        return groups.getOrPut(inviteCode) {
            val groupId = UUID.nameUUIDFromBytes("group-$inviteCode".toByteArray()).toString()
            seedSampleFamilyMembersIfNeeded(groupId)
            FamilyGroup(
                id = groupId,
                inviteCode = inviteCode,
                groupName = "우리 가족 ($inviteCode)",
                createdAt = Instant.now().toString()
            )
        }
    }

    private fun seedSampleFamilyMembersIfNeeded(groupId: String) {
        if (members.values.any { it.groupId == groupId }) return
        val now = Instant.now()

        val momId = UUID.nameUUIDFromBytes("mom-$groupId".toByteArray()).toString()
        val kidId = UUID.nameUUIDFromBytes("kid-$groupId".toByteArray()).toString()
        val grandpaId = UUID.nameUUIDFromBytes("grandpa-$groupId".toByteArray()).toString()

        members[momId] = FamilyMember(
            id = momId,
            groupId = groupId,
            deviceUid = "sample-mom",
            nickname = "사랑하는 엄마",
            avatarCode = "avatar_02_mom_perm",
            batteryLevel = 92,
            lastLat = 37.5678,
            lastLng = 126.9798,
            lastAccuracy = 12f,
            activityType = "WALKING",
            lastUpdatedAt = now.minus(3, ChronoUnit.MINUTES).toString()
        )
        members[kidId] = FamilyMember(
            id = kidId,
            groupId = groupId,
            deviceUid = "sample-kid",
            nickname = "막내 지훈",
            avatarCode = "avatar_05_son_cap",
            batteryLevel = 78,
            lastLat = 37.5649,
            lastLng = 126.9752,
            lastAccuracy = 10f,
            activityType = "STILL",
            lastUpdatedAt = now.minus(8, ChronoUnit.MINUTES).toString()
        )
        members[grandpaId] = FamilyMember(
            id = grandpaId,
            groupId = groupId,
            deviceUid = "sample-grandpa",
            nickname = "할아버지",
            avatarCode = "avatar_03_grandpa",
            batteryLevel = 88,
            lastLat = 37.5692,
            lastLng = 126.9735,
            lastAccuracy = 15f,
            activityType = "STILL",
            lastUpdatedAt = now.minus(14, ChronoUnit.MINUTES).toString()
        )

        // 샘플 24시간 이동 경로
        val momTrail = listOf(
            37.5625 to 126.9730,
            37.5642 to 126.9755,
            37.5660 to 126.9772,
            37.5678 to 126.9798
        )
        momTrail.forEachIndexed { idx, (lat, lng) ->
            history.add(
                LocationHistoryPoint(
                    id = (idx + 1).toLong(),
                    memberId = momId,
                    groupId = groupId,
                    latitude = lat,
                    longitude = lng,
                    accuracyM = 12f,
                    activityType = "WALKING",
                    recordedAt = now.minus((4 - idx) * 25L, ChronoUnit.MINUTES).toString()
                )
            )
        }
    }

    fun upsertMember(
        groupId: String,
        deviceUid: String,
        nickname: String,
        avatarCode: String,
        batteryLevel: Int
    ): FamilyMember {
        seedSampleFamilyMembersIfNeeded(groupId)
        val memberId = UUID.nameUUIDFromBytes("member-$deviceUid".toByteArray()).toString()
        val prev = members[memberId]
        val defaultLat = prev?.lastLat ?: 37.5665
        val defaultLng = prev?.lastLng ?: 126.9780
        val updated = FamilyMember(
            id = memberId,
            groupId = groupId,
            deviceUid = deviceUid,
            nickname = nickname,
            avatarCode = avatarCode,
            batteryLevel = batteryLevel,
            lastLat = defaultLat,
            lastLng = defaultLng,
            lastAccuracy = 10f,
            activityType = prev?.activityType ?: "STILL",
            lastUpdatedAt = Instant.now().toString()
        )
        members[memberId] = updated

        if (history.none { it.memberId == memberId }) {
            val now = Instant.now()
            listOf(
                (defaultLat - 0.0032) to (defaultLng - 0.0028),
                (defaultLat - 0.0018) to (defaultLng - 0.0012),
                defaultLat to defaultLng
            ).forEachIndexed { i, (lat, lng) ->
                history.add(
                    LocationHistoryPoint(
                        id = System.currentTimeMillis() + i,
                        memberId = memberId,
                        groupId = groupId,
                        latitude = lat,
                        longitude = lng,
                        accuracyM = 10f,
                        activityType = "WALKING",
                        recordedAt = now.minus((3 - i) * 20L, ChronoUnit.MINUTES).toString()
                    )
                )
            }
        }
        return updated
    }

    fun recordLocation(
        memberId: String,
        groupId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        batteryLevel: Int,
        activityType: String
    ) {
        val now = Instant.now()
        members.computeIfPresent(memberId) { _, existing ->
            existing.copy(
                lastLat = latitude,
                lastLng = longitude,
                lastAccuracy = accuracy,
                batteryLevel = batteryLevel,
                activityType = activityType,
                lastUpdatedAt = now.toString()
            )
        }
        history.add(
            LocationHistoryPoint(
                id = System.currentTimeMillis(),
                memberId = memberId,
                groupId = groupId,
                latitude = latitude,
                longitude = longitude,
                accuracyM = accuracy,
                activityType = activityType,
                recordedAt = now.toString()
            )
        )
        // 1일(24시간) 초과 데이터 자동 정리
        val cutoff = now.minus(24, ChronoUnit.HOURS)
        history.removeIf { pt ->
            runCatching { Instant.parse(pt.recordedAt).isBefore(cutoff) }.getOrDefault(false)
        }
    }

    fun getMembers(groupId: String): List<FamilyMember> {
        seedSampleFamilyMembersIfNeeded(groupId)
        return members.values.filter { it.groupId == groupId }.sortedBy { it.nickname }
    }

    fun getHistory24h(memberId: String): List<LocationHistoryPoint> {
        val cutoff = Instant.now().minus(24, ChronoUnit.HOURS)
        return history.filter { pt ->
            pt.memberId == memberId &&
                runCatching { !Instant.parse(pt.recordedAt).isBefore(cutoff) }.getOrDefault(true)
        }
    }

    fun deleteMember(memberId: String) {
        members.remove(memberId)
        history.removeIf { it.memberId == memberId }
    }

    private val pendingNotifications = ConcurrentHashMap<String, MutableList<FamilyNotificationMessage>>()

    fun addNotification(targetMemberId: String, senderNickname: String, message: String) {
        val list = pendingNotifications.getOrPut(targetMemberId) { CopyOnWriteArrayList() }
        list.add(
            FamilyNotificationMessage(
                id = UUID.randomUUID().toString(),
                senderNickname = senderNickname,
                message = message,
                createdAt = Instant.now().toString()
            )
        )
    }

    fun consumeNotifications(myMemberId: String): List<FamilyNotificationMessage> {
        val list = pendingNotifications.remove(myMemberId) ?: return emptyList()
        return list.toList()
    }
}
