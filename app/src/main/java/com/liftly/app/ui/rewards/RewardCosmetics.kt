package com.liftly.app.ui.rewards

import com.liftly.app.audio.RestAlertSound
import com.liftly.app.ui.theme.LiftlyCustomPalette

/** Converts stable catalog asset keys into effects understood by the current UI. */
object RewardCosmetics {
    fun palette(assetKey: String?): LiftlyCustomPalette? = when (assetKey) {
        "theme_amethyst" -> LiftlyCustomPalette(
            enabled = true,
            primary = "#D8A5FF",
            secondary = "#9B7BFF",
            background = "#0A0610",
            surface = "#18101F",
            text = "#FAF5FF",
        )
        "theme_carbon_oled" -> LiftlyCustomPalette(
            enabled = true,
            primary = "#D1C9D8",
            secondary = "#8E98A4",
            background = "#000000",
            surface = "#0D0E10",
            text = "#F4F4F6",
        )
        "theme_ocean" -> LiftlyCustomPalette(
            enabled = true,
            primary = "#76D9F5",
            secondary = "#7597FF",
            background = "#041017",
            surface = "#0B202B",
            text = "#F0FAFF",
        )
        "theme_ember" -> LiftlyCustomPalette(
            enabled = true,
            primary = "#F0A06A",
            secondary = "#C97956",
            background = "#100907",
            surface = "#21130F",
            text = "#FFF6F0",
        )
        "theme_ivory" -> LiftlyCustomPalette(
            enabled = true,
            primary = "#5C4A69",
            secondary = "#8B6F62",
            background = "#F6F1E8",
            surface = "#FFFBF4",
            text = "#242126",
        )
        else -> null
    }

    fun restSoundId(assetKey: String?): String? = when (assetKey) {
        "sound_clean_bell" -> RestAlertSound.CHIME.id
        "sound_impact" -> RestAlertSound.PULSE.id
        "sound_digital" -> RestAlertSound.ASCENDING.id
        else -> null
    }

    fun profileTitle(assetKey: String?): String? = when (assetKey) {
        "title_building" -> "Em construção"
        "title_consistency" -> "Constância acima de motivação"
        "title_no_shortcuts" -> "Sem atalhos"
        else -> null
    }

    fun profileFrameArgb(assetKey: String?): Long? = when (assetKey) {
        "frame_graphite" -> 0xFF70747CL
        "frame_amethyst" -> 0xFFC08BFFL
        "frame_gold" -> 0xFFD5AE62L
        else -> null
    }
}
