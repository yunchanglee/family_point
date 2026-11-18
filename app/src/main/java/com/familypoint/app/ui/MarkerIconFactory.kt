package com.familypoint.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.ContextCompat
import com.familypoint.app.model.AvatarCatalog
import kotlin.math.max

object MarkerIconFactory {

    /**
     * 오픈스트리트맵(OSMDroid) 마커용 커스텀 비트맵 생성:
     * - 상단: 선택한 대표 아바타 얼굴 (20종 중 1개) + 선택/온라인/연결끊김 상태 테두리
     * - 연결 끊김(isOffline = true) 시:
     *   1) 아바타 흑백(채도 감소) 처리
     *   2) 테두리 붉은색 점선 + 우측 상단 빨간색 '!' 경고 배지 표시
     *   3) 하단 말풍선 배지에 "[연결끊김]" 및 경과 시간 표시
     */
    fun createMemberMarkerDrawable(
        context: Context,
        avatarCode: String,
        nickname: String,
        batteryLevel: Int,
        isSelected: Boolean,
        isMe: Boolean,
        isOffline: Boolean,
        offlineTimeText: String = ""
    ): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        val avatarSizePx = (58f * density).toInt()

        val baseName = if (isMe) "$nickname (나)" else nickname
        val labelText = if (isOffline) {
            if (offlineTimeText.isNotBlank()) "$baseName · 연결끊김 ($offlineTimeText)" else "$baseName · 연결끊김"
        } else {
            "$baseName · $batteryLevel%"
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val textWidth = textPaint.measureText(labelText)
        val badgePaddingH = 12f * density
        val badgeHeight = 24f * density
        val badgeWidth = max(avatarSizePx.toFloat() + 14f * density, textWidth + badgePaddingH * 2)
        val pointerHeight = 10f * density
        val spacing = 4f * density

        val totalWidth = (badgeWidth + 16f * density).toInt()
        val totalHeight = (avatarSizePx + spacing + badgeHeight + pointerHeight + 12f * density).toInt()

        val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val centerX = totalWidth / 2f
        val avatarCenterY = avatarSizePx / 2f + 6f * density
        val avatarRadius = avatarSizePx / 2f

        val primaryColor = when {
            isOffline -> Color.parseColor("#DC2626")  // 연결 끊김: 경고 레드
            isSelected -> Color.parseColor("#2563EB") // 선택된 가족: 선명한 블루
            isMe -> Color.parseColor("#059669")       // 나: 에메랄드 그린
            else -> Color.parseColor("#334155")       // 기본 온라인 가족: 슬레이트 다크
        }

        // 1) 아바타 외곽 원형 배경 및 테두리
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, avatarCenterY, avatarRadius + 3.5f * density, ringPaint)

        // 2) 대표 아바타 벡터 드로어블 그리기 (연결 끊김 시 채도 낮춤 + 반투명)
        val drawableRes = AvatarCatalog.getDrawableRes(avatarCode)
        val avatarDrawable = ContextCompat.getDrawable(context, drawableRes)?.mutate()
        if (avatarDrawable != null) {
            val left = (centerX - avatarRadius).toInt()
            val top = (avatarCenterY - avatarRadius).toInt()
            val right = (centerX + avatarRadius).toInt()
            val bottom = (avatarCenterY + avatarRadius).toInt()
            avatarDrawable.setBounds(left, top, right, bottom)
            if (isOffline) {
                val cm = ColorMatrix().apply { setSaturation(0.15f) }
                avatarDrawable.colorFilter = ColorMatrixColorFilter(cm)
                avatarDrawable.alpha = 185
            } else {
                avatarDrawable.colorFilter = null
                avatarDrawable.alpha = 255
            }
            avatarDrawable.draw(canvas)
        }

        // 3) 아바타 외곽 상태 링 (연결 끊김 시 빨간색 점선 링)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            style = Paint.Style.STROKE
            strokeWidth = if (isSelected || isOffline) 3.5f * density else 2.5f * density
            if (isOffline) {
                pathEffect = DashPathEffect(floatArrayOf(8f * density, 5f * density), 0f)
            }
        }
        canvas.drawCircle(centerX, avatarCenterY, avatarRadius + 1.5f * density, strokePaint)

        // 4) 연결 끊김일 때 아바타 우측 상단에 빨간색 '!' 경고 아이콘 배지 표시
        if (isOffline) {
            val warnRadius = 11f * density
            val warnCx = centerX + avatarRadius * 0.72f
            val warnCy = avatarCenterY - avatarRadius * 0.72f

            val warnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#DC2626")
                style = Paint.Style.FILL
            }
            val warnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 2f * density
            }
            canvas.drawCircle(warnCx, warnCy, warnRadius, warnBgPaint)
            canvas.drawCircle(warnCx, warnCy, warnRadius, warnBorderPaint)

            val bangPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 14f * density
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val fm = bangPaint.fontMetrics
            val bangY = warnCy - (fm.ascent + fm.descent) / 2f
            canvas.drawText("!", warnCx, bangY, bangPaint)
        }

        // 5) 하단 별칭(Nickname) 알약 배지
        val badgeTop = avatarCenterY + avatarRadius + spacing
        val badgeBottom = badgeTop + badgeHeight
        val badgeRect = RectF(
            centerX - badgeWidth / 2f,
            badgeTop,
            centerX + badgeWidth / 2f,
            badgeBottom
        )
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(badgeRect, badgeHeight / 2f, badgeHeight / 2f, badgePaint)

        // 6) 배지 안의 텍스트
        val fontMetrics = textPaint.fontMetrics
        val textBaseline = badgeRect.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(labelText, centerX, textBaseline, textPaint)

        // 7) 하단 좌표 지점 포인터 삼각형
        val pointerPath = Path().apply {
            moveTo(centerX - 6f * density, badgeBottom - 1f)
            lineTo(centerX + 6f * density, badgeBottom - 1f)
            lineTo(centerX, badgeBottom + pointerHeight)
            close()
        }
        canvas.drawPath(pointerPath, badgePaint)

        return BitmapDrawable(context.resources, bitmap)
    }
}
