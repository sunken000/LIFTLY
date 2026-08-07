package com.liftly.app.data

import com.liftly.app.domain.RewardLevelProgress
import com.liftly.app.domain.RewardMetric
import com.liftly.app.domain.RewardPeriod
import com.liftly.app.domain.RewardProgression
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object RewardSlots {
    const val THEME = "theme"
    const val WALLPAPER = "wallpaper"
    const val PROFILE_FRAME = "profile_frame"
    const val REST_SOUND = "rest_sound"
    const val TIMER_STYLE = "timer_style"
    const val PROFILE_TITLE = "profile_title"
    const val CHART_STYLE = "chart_style"
}

object LiftlyRewardCatalog {
    val items: List<RewardCatalogItemEntity> = listOf(
        item("theme.amethyst", "Ametista", "Roxo profundo com detalhes luminosos.", "Temas", RewardSlots.THEME, "Raro", 850, 4, "theme_amethyst", 10),
        item("theme.oled", "Carbono OLED", "Preto sólido, contraste alto e superfícies em grafite.", "Temas", RewardSlots.THEME, "Épico", 1_200, 6, "theme_carbon_oled", 11),
        item("theme.ocean", "Oceano", "Azul-marinho com destaques em ciano.", "Temas", RewardSlots.THEME, "Raro", 900, 4, "theme_ocean", 12),
        item("theme.ember", "Brasa", "Carvão com detalhes quentes em cobre.", "Temas", RewardSlots.THEME, "Raro", 900, 4, "theme_ember", 13),
        item("theme.ivory", "Marfim", "Interface clara, limpa e de alto contraste.", "Temas", RewardSlots.THEME, "Épico", 1_250, 7, "theme_ivory", 14),

        item("wallpaper.aurora", "Aurora", "Ondas lentas de luz roxa e azul.", "Wallpapers", RewardSlots.WALLPAPER, "Raro", 420, 3, "wallpaper_aurora", 20),
        item("wallpaper.grid", "Grade noturna", "Grade técnica com movimento discreto.", "Wallpapers", RewardSlots.WALLPAPER, "Comum", 280, 2, "wallpaper_grid", 21),
        item("wallpaper.smoke", "Fumaça violeta", "Névoa suave sobre fundo escuro.", "Wallpapers", RewardSlots.WALLPAPER, "Épico", 650, 5, "wallpaper_smoke", 22),
        item("wallpaper.steel", "Aço escovado", "Textura industrial sóbria e sem distrações.", "Wallpapers", RewardSlots.WALLPAPER, "Raro", 480, 3, "wallpaper_steel", 23),

        item("frame.graphite", "Grafite", "Moldura fina em metal escuro.", "Perfil", RewardSlots.PROFILE_FRAME, "Comum", 180, 2, "frame_graphite", 30),
        item("frame.amethyst", "Aro ametista", "Contorno violeta com brilho controlado.", "Perfil", RewardSlots.PROFILE_FRAME, "Raro", 360, 3, "frame_amethyst", 31),
        item("frame.gold", "Precisão dourada", "Detalhe dourado reservado para perfis consistentes.", "Perfil", RewardSlots.PROFILE_FRAME, "Lendário", 1_100, 8, "frame_gold", 32),

        item("sound.clean_bell", "Sino limpo", "Aviso curto e claro para o fim do descanso.", "Treino", RewardSlots.REST_SOUND, "Comum", 140, 1, "sound_clean_bell", 40),
        item("sound.impact", "Impacto", "Batida grave curta para ambientes barulhentos.", "Treino", RewardSlots.REST_SOUND, "Raro", 260, 2, "sound_impact", 41),
        item("sound.digital", "Pulso digital", "Sequência eletrônica discreta.", "Treino", RewardSlots.REST_SOUND, "Raro", 260, 2, "sound_digital", 42),

        item("title.building", "Em construção", "Título de perfil para quem está começando.", "Títulos", RewardSlots.PROFILE_TITLE, "Comum", 100, 1, "title_building", 60),
        item("title.consistency", "Constância acima de motivação", "Título para quem mantém o plano.", "Títulos", RewardSlots.PROFILE_TITLE, "Raro", 350, 4, "title_consistency", 61),
        item("title.no_shortcuts", "Sem atalhos", "Título de disciplina de longo prazo.", "Títulos", RewardSlots.PROFILE_TITLE, "Lendário", 1_300, 10, "title_no_shortcuts", 62),
    )

    private fun item(
        id: String,
        title: String,
        description: String,
        category: String,
        slot: String,
        rarity: String,
        price: Long,
        level: Int,
        assetKey: String,
        order: Int,
    ) = RewardCatalogItemEntity(
        id = id,
        title = title,
        description = description,
        category = category,
        slot = slot,
        rarity = rarity,
        priceCoins = price,
        requiredLevel = level,
        assetKey = assetKey,
        sortOrder = order,
    )
}

data class RewardStoreItem(
    val item: RewardCatalogItemEntity,
    val owned: Boolean,
    val equipped: Boolean,
)

data class RewardSnapshot(
    val wallet: RewardWalletEntity = RewardWalletEntity(updatedAt = 0L),
    val level: RewardLevelProgress = RewardProgression.fromLifetimeXp(0),
    val store: List<RewardStoreItem> = emptyList(),
    val missions: List<RewardMissionEntity> = emptyList(),
    val recentActivity: List<RewardLedgerEntity> = emptyList(),
)

data class RewardGrant(
    val xp: Long = 0L,
    val coins: Long = 0L,
    val completedMissionIds: List<String> = emptyList(),
    val duplicate: Boolean = false,
) {
    companion object { val NONE = RewardGrant() }
}

internal object RewardMissionFactory {
    fun current(date: LocalDate, zoneId: ZoneId): List<RewardMissionEntity> {
        val dayStart = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val weekDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekStart = weekDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val weekEnd = weekDate.plusWeeks(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val month = YearMonth.from(date)
        val monthDate = month.atDay(1)
        val monthStart = monthDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val monthEnd = month.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

        return listOf(
            mission("daily.$date.workout", RewardPeriod.DAILY, RewardMetric.WORKOUT_COMPLETED, "Treino do dia", "Conclua um treino válido hoje.", 1, 50, 20, dayStart, dayEnd, 10),
            mission("daily.$date.rir", RewardPeriod.DAILY, RewardMetric.RIR_SET_RECORDED, "Treino consciente", "Registre o RIR em 3 séries concluídas.", 3, 30, 10, dayStart, dayEnd, 11),
            mission("weekly.$weekDate.workouts", RewardPeriod.WEEKLY, RewardMetric.WORKOUT_COMPLETED, "Ritmo semanal", "Conclua 3 treinos nesta semana.", 3, 150, 50, weekStart, weekEnd, 20),
            mission("weekly.$weekDate.complete", RewardPeriod.WEEKLY, RewardMetric.COMPLETE_WORKOUT, "Plano cumprido", "Finalize 2 fichas sem deixar séries pendentes.", 2, 100, 35, weekStart, weekEnd, 21),
            mission("monthly.$month.workouts", RewardPeriod.MONTHLY, RewardMetric.WORKOUT_COMPLETED, "Consistência mensal", "Conclua 12 treinos neste mês.", 12, 400, 150, monthStart, monthEnd, 30),
            mission("monthly.$month.records", RewardPeriod.MONTHLY, RewardMetric.PERSONAL_RECORD, "Evolução mensurável", "Registre 3 novos recordes pessoais.", 3, 200, 75, monthStart, monthEnd, 31),
        )
    }

    private fun mission(
        id: String,
        period: RewardPeriod,
        metric: RewardMetric,
        title: String,
        description: String,
        target: Int,
        xp: Long,
        coins: Long,
        start: Long,
        end: Long,
        order: Int,
    ) = RewardMissionEntity(
        id = id,
        period = period.name,
        metric = metric.name,
        title = title,
        description = description,
        target = target,
        xpReward = xp,
        coinReward = coins,
        periodStart = start,
        periodEnd = end,
        sortOrder = order,
    )
}
