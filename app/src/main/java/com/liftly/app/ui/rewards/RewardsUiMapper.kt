package com.liftly.app.ui.rewards

import com.liftly.app.data.RewardMissionEntity
import com.liftly.app.data.RewardSnapshot
import com.liftly.app.data.RewardSlots
import com.liftly.app.data.RewardStoreItem
import com.liftly.app.domain.RewardPeriod

fun RewardSnapshot.toUiState(
    workoutStreak: Int,
    selectedCategory: RewardCategory,
    selectedMissionPeriod: MissionPeriod,
): RewardsUiState = RewardsUiState(
    account = RewardsAccountUi(
        level = level.level,
        levelTitle = levelTitle(level.level),
        currentXp = level.xpInLevel.toDisplayInt(),
        nextLevelXp = level.xpForNextLevel.toDisplayInt(),
        coinBalance = wallet.coinBalance.toDisplayInt(),
        workoutStreak = workoutStreak.coerceAtLeast(0),
    ),
    items = store.map { it.toUi(level.level) },
    missions = missions.map { it.toUi() },
    activity = recentActivity.map { entry ->
        RewardActivityUi(
            id = entry.id,
            title = entry.description,
            detail = when (entry.sourceType) {
                "WORKOUT" -> "Treino concluído"
                "MISSION" -> "Missão concluída"
                "PURCHASE" -> "Compra na loja"
                else -> "Movimentação"
            },
            xp = entry.deltaXp.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
            coins = entry.deltaCoins.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
            occurredAt = entry.createdAt,
        )
    },
    selectedCategory = selectedCategory,
    selectedMissionPeriod = selectedMissionPeriod,
)

private fun RewardStoreItem.toUi(currentLevel: Int): RewardItemUi {
    val visual = when (item.slot) {
        RewardSlots.THEME -> RewardVisual.Theme
        RewardSlots.WALLPAPER -> RewardVisual.Wallpaper
        RewardSlots.PROFILE_FRAME -> RewardVisual.ProfileFrame
        RewardSlots.PROFILE_TITLE -> RewardVisual.ProfileTitle
        RewardSlots.REST_SOUND -> RewardVisual.Sound
        RewardSlots.TIMER_STYLE -> RewardVisual.Timer
        RewardSlots.CHART_STYLE -> RewardVisual.Chart
        else -> RewardVisual.Theme
    }
    val category = when {
        item.slot == RewardSlots.REST_SOUND -> RewardCategory.Sounds
        item.category.equals("Temas", ignoreCase = true) -> RewardCategory.Themes
        item.category.equals("Wallpapers", ignoreCase = true) -> RewardCategory.Wallpapers
        item.category.equals("Perfil", ignoreCase = true) || item.category.equals("Títulos", ignoreCase = true) -> RewardCategory.Profile
        else -> RewardCategory.Focus
    }
    val rarity = when (item.rarity.lowercase()) {
        "comum" -> RewardRarity.Essential
        "raro" -> RewardRarity.Select
        else -> RewardRarity.Signature
    }
    return RewardItemUi(
        id = item.id,
        title = item.title,
        description = item.description,
        category = category,
        rarity = rarity,
        visual = visual,
        price = item.priceCoins.toDisplayInt(),
        previewColors = previewColors(item.assetKey),
        owned = owned,
        equipped = equipped,
        featured = item.sortOrder in setOf(10, 20, 32, 52),
        limitedLabel = if (item.requiredLevel > 1 && !owned) "Nível ${item.requiredLevel}" else null,
        available = item.enabled && currentLevel >= item.requiredLevel,
    )
}

private fun RewardMissionEntity.toUi(): RewardMissionUi = RewardMissionUi(
    id = id,
    title = title,
    detail = description,
    period = when (runCatching { RewardPeriod.valueOf(period) }.getOrNull()) {
        RewardPeriod.WEEKLY -> MissionPeriod.Weekly
        RewardPeriod.MONTHLY -> MissionPeriod.Monthly
        else -> MissionPeriod.Daily
    },
    progress = progress,
    target = target,
    coinReward = coinReward.toDisplayInt(),
    xpReward = xpReward.toDisplayInt(),
    claimed = completedAt != null,
)

private fun levelTitle(level: Int): String = when {
    level >= 20 -> "Disciplina consolidada"
    level >= 12 -> "Consistência forte"
    level >= 7 -> "Ritmo estabelecido"
    level >= 3 -> "Base em construção"
    else -> "Primeiros passos"
}

private fun previewColors(assetKey: String): List<Long> = when (assetKey) {
    "theme_amethyst", "wallpaper_aurora", "frame_amethyst" -> listOf(0xFF160B22L, 0xFF7447A5L, 0xFFD8A5FFL)
    "theme_carbon_oled", "frame_graphite", "wallpaper_steel" -> listOf(0xFF050507L, 0xFF292B30L, 0xFFD1C9D8L)
    "theme_ocean" -> listOf(0xFF041017L, 0xFF174F67L, 0xFF76D9F5L)
    "theme_ember" -> listOf(0xFF100907L, 0xFF71341FL, 0xFFF0A06AL)
    "theme_ivory" -> listOf(0xFFF6F1E8L, 0xFFD6CBBCL, 0xFF5C4A69L)
    "wallpaper_grid", "sound_digital", "timer_sport" -> listOf(0xFF07101AL, 0xFF27577CL, 0xFF75C8FFL)
    "wallpaper_smoke" -> listOf(0xFF110A18L, 0xFF5D396CL, 0xFFB98AC7L)
    "frame_gold", "title_no_shortcuts" -> listOf(0xFF17120AL, 0xFF765C29L, 0xFFD5AE62L)
    "sound_clean_bell", "sound_impact", "timer_ring", "timer_minimal" -> listOf(0xFF17131CL, 0xFF57455FL, 0xFFBE8DD8L)
    else -> listOf(0xFF18131CL, 0xFF4A364FL, 0xFFC89AE2L)
}

private fun Long.toDisplayInt(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
