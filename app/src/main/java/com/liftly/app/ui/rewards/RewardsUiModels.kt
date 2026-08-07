package com.liftly.app.ui.rewards

import androidx.compose.runtime.Immutable

/** Sections exposed by the rewards catalog. Kept UI-only so persistence can evolve independently. */
enum class RewardCategory(val label: String) {
    All("Todos"),
    Themes("Temas"),
    Wallpapers("Wallpapers"),
    Profile("Perfil"),
    Focus("Treino"),
    Sounds("Sons"),
}

enum class RewardRarity(val label: String) {
    Essential("Essencial"),
    Select("Seleto"),
    Signature("Assinatura"),
}

enum class RewardVisual {
    Theme,
    Wallpaper,
    ProfileFrame,
    ProfileTitle,
    Timer,
    Sound,
    Chart,
}

enum class MissionPeriod(val label: String) {
    Daily("Hoje"),
    Weekly("Semana"),
    Monthly("Mês"),
}

enum class RewardsViewerMode {
    User,
    AdminPreview,
}

@Immutable
data class RewardsAccountUi(
    val level: Int,
    val levelTitle: String,
    val currentXp: Int,
    val nextLevelXp: Int,
    val coinBalance: Int,
    val workoutStreak: Int,
) {
    val levelProgress: Float
        get() = if (nextLevelXp <= 0) 0f else (currentXp.toFloat() / nextLevelXp).coerceIn(0f, 1f)
}

@Immutable
data class RewardItemUi(
    val id: String,
    val title: String,
    val description: String,
    val category: RewardCategory,
    val rarity: RewardRarity,
    val visual: RewardVisual,
    val price: Int,
    /** ARGB color values used only for the abstract product preview. */
    val previewColors: List<Long>,
    val owned: Boolean = false,
    val equipped: Boolean = false,
    val featured: Boolean = false,
    val limitedLabel: String? = null,
    val available: Boolean = true,
)

@Immutable
data class RewardMissionUi(
    val id: String,
    val title: String,
    val detail: String,
    val period: MissionPeriod,
    val progress: Int,
    val target: Int,
    val coinReward: Int,
    val xpReward: Int,
    val claimed: Boolean = false,
) {
    val completed: Boolean get() = progress >= target
    val progressFraction: Float
        get() = if (target <= 0) 0f else (progress.toFloat() / target).coerceIn(0f, 1f)
}

@Immutable
data class RewardActivityUi(
    val id: String,
    val title: String,
    val detail: String,
    val xp: Int,
    val coins: Int,
    val occurredAt: Long,
)

@Immutable
data class RewardsUiState(
    val account: RewardsAccountUi,
    val items: List<RewardItemUi>,
    val missions: List<RewardMissionUi>,
    val activity: List<RewardActivityUi> = emptyList(),
    val selectedCategory: RewardCategory = RewardCategory.All,
    val selectedMissionPeriod: MissionPeriod = MissionPeriod.Daily,
    val viewerMode: RewardsViewerMode = RewardsViewerMode.User,
    val isLoading: Boolean = false,
) {
    val featuredItems: List<RewardItemUi> get() = items.filter(RewardItemUi::featured)
    val ownedItems: List<RewardItemUi> get() = items.filter(RewardItemUi::owned)
    val visibleItems: List<RewardItemUi>
        get() = when (selectedCategory) {
            RewardCategory.All -> items
            else -> items.filter { it.category == selectedCategory }
        }
    val visibleMissions: List<RewardMissionUi>
        get() = missions.filter { it.period == selectedMissionPeriod }
}

@Immutable
data class RewardsActions(
    val onCategorySelected: (RewardCategory) -> Unit = {},
    val onMissionPeriodSelected: (MissionPeriod) -> Unit = {},
    val onBuy: (RewardItemUi) -> Unit = {},
    val onEquip: (RewardItemUi) -> Unit = {},
    val onClaimMission: (RewardMissionUi) -> Unit = {},
    val onOpenHistory: () -> Unit = {},
    val onOpenInventory: () -> Unit = {},
    val onOpenAdminPanel: () -> Unit = {},
)

/** Preview data doubles as a concise contract example for the eventual ViewModel mapper. */
internal object RewardsPreviewData {
    val state = RewardsUiState(
        account = RewardsAccountUi(
            level = 12,
            levelTitle = "Consistência forte",
            currentXp = 1_420,
            nextLevelXp = 2_000,
            coinBalance = 1_240,
            workoutStreak = 4,
        ),
        items = listOf(
            RewardItemUi(
                id = "theme_carbon",
                title = "Carbono",
                description = "Superfícies grafite, contraste preciso e detalhes em aço.",
                category = RewardCategory.Themes,
                rarity = RewardRarity.Signature,
                visual = RewardVisual.Theme,
                price = 900,
                previewColors = listOf(0xFF0A0A0CL, 0xFF29282CL, 0xFFDDDCE2L),
                featured = true,
            ),
            RewardItemUi(
                id = "wallpaper_aurora",
                title = "Aurora lenta",
                description = "Movimento suave em violeta, azul profundo e névoa.",
                category = RewardCategory.Wallpapers,
                rarity = RewardRarity.Select,
                visual = RewardVisual.Wallpaper,
                price = 420,
                previewColors = listOf(0xFF211035L, 0xFF7744A8L, 0xFF5476C9L),
                owned = true,
                equipped = true,
                featured = true,
            ),
            RewardItemUi(
                id = "timer_focus",
                title = "Cronômetro Foco",
                description = "Leitura ampla, controles reduzidos e nenhuma distração.",
                category = RewardCategory.Focus,
                rarity = RewardRarity.Select,
                visual = RewardVisual.Timer,
                price = 310,
                previewColors = listOf(0xFF15121AL, 0xFFE1BDFFL),
                featured = true,
            ),
            RewardItemUi(
                id = "frame_precision",
                title = "Moldura Precisão",
                description = "Contorno fino para destacar sua foto no perfil.",
                category = RewardCategory.Profile,
                rarity = RewardRarity.Essential,
                visual = RewardVisual.ProfileFrame,
                price = 180,
                previewColors = listOf(0xFF28222EL, 0xFFB786D7L),
                owned = true,
            ),
            RewardItemUi(
                id = "sound_clean_bell",
                title = "Sino limpo",
                description = "Aviso curto, claro e confortável para fones.",
                category = RewardCategory.Sounds,
                rarity = RewardRarity.Essential,
                visual = RewardVisual.Sound,
                price = 120,
                previewColors = listOf(0xFF19161DL, 0xFF79B8D8L),
            ),
            RewardItemUi(
                id = "title_no_shortcuts",
                title = "Sem atalhos",
                description = "Título de perfil liberado após três meses de metas.",
                category = RewardCategory.Profile,
                rarity = RewardRarity.Signature,
                visual = RewardVisual.ProfileTitle,
                price = 0,
                previewColors = listOf(0xFF19151EL, 0xFFD8B16EL),
                limitedLabel = "Conquista",
                available = false,
            ),
        ),
        missions = listOf(
            RewardMissionUi(
                id = "daily_rir",
                title = "Treino bem registrado",
                detail = "Informe o RIR em todas as séries de um exercício.",
                period = MissionPeriod.Daily,
                progress = 1,
                target = 1,
                coinReward = 10,
                xpReward = 30,
            ),
            RewardMissionUi(
                id = "daily_warmup",
                title = "Preparação completa",
                detail = "Conclua o aquecimento sugerido antes do exercício.",
                period = MissionPeriod.Daily,
                progress = 0,
                target = 1,
                coinReward = 8,
                xpReward = 20,
            ),
            RewardMissionUi(
                id = "weekly_sessions",
                title = "Meta da semana",
                detail = "Complete sua frequência semanal definida no perfil.",
                period = MissionPeriod.Weekly,
                progress = 3,
                target = 4,
                coinReward = 50,
                xpReward = 150,
            ),
            RewardMissionUi(
                id = "monthly_consistency",
                title = "Constância acima de tudo",
                detail = "Atinja a meta semanal em três semanas do mês.",
                period = MissionPeriod.Monthly,
                progress = 2,
                target = 3,
                coinReward = 150,
                xpReward = 400,
            ),
        ),
    )
}
