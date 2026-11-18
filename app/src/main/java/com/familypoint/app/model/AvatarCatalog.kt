package com.familypoint.app.model

import androidx.annotation.DrawableRes
import com.familypoint.app.R

data class AvatarOption(
    val code: String,
    val label: String,
    @DrawableRes val drawableRes: Int
)

object AvatarCatalog {
    val avatars: List<AvatarOption> = listOf(
        AvatarOption("avatar_01_dad_glasses", "아빠 (안경)", R.drawable.avatar_01),
        AvatarOption("avatar_02_mom_perm", "엄마 (단발웨이브)", R.drawable.avatar_02),
        AvatarOption("avatar_03_grandpa", "할아버지", R.drawable.avatar_03),
        AvatarOption("avatar_04_grandma", "할머니", R.drawable.avatar_04),
        AvatarOption("avatar_05_son_cap", "아들 (모자)", R.drawable.avatar_05),
        AvatarOption("avatar_06_daughter_ponytail", "딸 (포니테일)", R.drawable.avatar_06),
        AvatarOption("avatar_07_teen_boy", "청소년 남 (헤드폰)", R.drawable.avatar_07),
        AvatarOption("avatar_08_teen_girl", "청소년 여 (긴머리)", R.drawable.avatar_08),
        AvatarOption("avatar_09_kid_boy", "어린이 남", R.drawable.avatar_09),
        AvatarOption("avatar_10_kid_girl", "어린이 여 (양갈래)", R.drawable.avatar_10),
        AvatarOption("avatar_11_baby", "막내 아기", R.drawable.avatar_11),
        AvatarOption("avatar_12_uncle", "삼촌 (수염)", R.drawable.avatar_12),
        AvatarOption("avatar_13_aunt", "이모/고모 (베레모)", R.drawable.avatar_13),
        AvatarOption("avatar_14_sporty_dad", "운동파 가족", R.drawable.avatar_14),
        AvatarOption("avatar_15_studious", "학생 (뿔테안경)", R.drawable.avatar_15),
        AvatarOption("avatar_16_dog_puppy", "반려견 (강아지)", R.drawable.avatar_16),
        AvatarOption("avatar_17_cat_kitty", "반려묘 (고양이)", R.drawable.avatar_17),
        AvatarOption("avatar_18_bear", "곰돌이 캐릭터", R.drawable.avatar_18),
        AvatarOption("avatar_19_rabbit", "토끼 캐릭터", R.drawable.avatar_19),
        AvatarOption("avatar_20_penguin", "펭귄 캐릭터", R.drawable.avatar_20)
    )

    @DrawableRes
    fun getDrawableRes(code: String?): Int {
        if (code.isNullOrBlank()) return R.drawable.avatar_01
        val normalized = code.trim()
        avatars.firstOrNull { it.code == normalized || normalized.startsWith(it.code.take(9)) }
            ?.let { return it.drawableRes }
        return R.drawable.avatar_01
    }

    fun getLabel(code: String?): String {
        if (code.isNullOrBlank()) return avatars.first().label
        val normalized = code.trim()
        return avatars.firstOrNull {
            it.code == normalized || normalized.startsWith(it.code.take(9))
        }?.label ?: avatars.first().label
    }
}
