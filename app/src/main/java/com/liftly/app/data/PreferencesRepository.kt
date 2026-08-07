package com.liftly.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.liftly.app.audio.RestAlertSound

private val Context.dataStore by preferencesDataStore("liftly_preferences")

data class UserPreferences(
    val onboardingDone: Boolean = false,
    val theme: String = "Roxo Neon",
    val haptics: Boolean = true,
    val restTimer: Boolean = true,
    val restEndVibration: Boolean = true,
    val restEndSound: Boolean = true,
    val restEndSoundType: String = RestAlertSound.ASCENDING.id,
    val restEndSoundDurationSeconds: Int = 2,
    val demoEnabled: Boolean = false,
    val exerciseFilters: String = "",
    val profilePhotoUri: String = "",
    val hideBodyMetrics: Boolean = false,
    /** URI local escolhida pelo SAF; não entra em backup nem sai do aparelho. */
    val customWallpaperUri: String = "",
    val customWallpaperEnabled: Boolean = false,
    val wallpaperDimPercent: Int = 45,
    /** Paleta opcional em #RRGGBB. Vazia/desativada preserva integralmente o tema escolhido. */
    val customPaletteEnabled: Boolean = false,
    val customPrimaryColor: String = "",
    val customSecondaryColor: String = "",
    val customBackgroundColor: String = "",
    val customSurfaceColor: String = "",
    val customTextColor: String = "",
    val weeklyWorkoutGoal: Int = 3,
    val goalNotifications: Boolean = true,
    val streakNotifications: Boolean = true,
    val discordWebhookEnabled: Boolean = false,
    /** Segredo local: nunca entra no backup nem deve ser escrito em logs. */
    val discordWebhookUrl: String = ""
)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val theme = stringPreferencesKey("theme")
        val haptics = booleanPreferencesKey("haptics")
        val restTimer = booleanPreferencesKey("rest_timer")
        val restEndVibration = booleanPreferencesKey("rest_end_vibration")
        val restEndSound = booleanPreferencesKey("rest_end_sound")
        val restEndSoundType = stringPreferencesKey("rest_end_sound_type")
        val restEndSoundDurationSeconds = intPreferencesKey("rest_end_sound_duration_seconds")
        val demo = booleanPreferencesKey("demo_enabled")
        val filters = stringPreferencesKey("exercise_filters")
        val profilePhotoUri = stringPreferencesKey("profile_photo_uri")
        val hideBodyMetrics = booleanPreferencesKey("hide_body_metrics")
        val customWallpaperUri = stringPreferencesKey("custom_wallpaper_uri")
        val customWallpaperEnabled = booleanPreferencesKey("custom_wallpaper_enabled")
        val wallpaperDimPercent = intPreferencesKey("wallpaper_dim_percent")
        val customPaletteEnabled = booleanPreferencesKey("custom_palette_enabled")
        val customPrimaryColor = stringPreferencesKey("custom_primary_color")
        val customSecondaryColor = stringPreferencesKey("custom_secondary_color")
        val customBackgroundColor = stringPreferencesKey("custom_background_color")
        val customSurfaceColor = stringPreferencesKey("custom_surface_color")
        val customTextColor = stringPreferencesKey("custom_text_color")
        val weeklyWorkoutGoal = intPreferencesKey("weekly_workout_goal")
        val goalNotifications = booleanPreferencesKey("goal_notifications")
        val streakNotifications = booleanPreferencesKey("streak_notifications")
        val discordWebhookEnabled = booleanPreferencesKey("discord_webhook_enabled")
        val discordWebhookUrl = stringPreferencesKey("discord_webhook_url")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { value ->
        UserPreferences(
            onboardingDone = value[Keys.onboarding] ?: false,
            theme = value[Keys.theme] ?: "Roxo Neon",
            haptics = value[Keys.haptics] ?: true,
            restTimer = value[Keys.restTimer] ?: true,
            restEndVibration = value[Keys.restEndVibration] ?: true,
            restEndSound = value[Keys.restEndSound] ?: true,
            restEndSoundType = RestAlertSound.fromId(value[Keys.restEndSoundType]).id,
            restEndSoundDurationSeconds = (value[Keys.restEndSoundDurationSeconds] ?: 2).coerceIn(1, 10),
            demoEnabled = value[Keys.demo] ?: false,
            exerciseFilters = value[Keys.filters] ?: "",
            profilePhotoUri = value[Keys.profilePhotoUri] ?: "",
            hideBodyMetrics = value[Keys.hideBodyMetrics] ?: false,
            customWallpaperUri = value[Keys.customWallpaperUri] ?: "",
            customWallpaperEnabled = value[Keys.customWallpaperEnabled] ?: false,
            wallpaperDimPercent = (value[Keys.wallpaperDimPercent] ?: 45).coerceIn(20, 80),
            customPaletteEnabled = value[Keys.customPaletteEnabled] ?: false,
            customPrimaryColor = value[Keys.customPrimaryColor] ?: "",
            customSecondaryColor = value[Keys.customSecondaryColor] ?: "",
            customBackgroundColor = value[Keys.customBackgroundColor] ?: "",
            customSurfaceColor = value[Keys.customSurfaceColor] ?: "",
            customTextColor = value[Keys.customTextColor] ?: "",
            weeklyWorkoutGoal = (value[Keys.weeklyWorkoutGoal] ?: 3).coerceIn(1, 14),
            goalNotifications = value[Keys.goalNotifications] ?: true,
            streakNotifications = value[Keys.streakNotifications] ?: true,
            discordWebhookEnabled = value[Keys.discordWebhookEnabled] ?: false,
            discordWebhookUrl = value[Keys.discordWebhookUrl] ?: ""
        )
    }

    suspend fun finishOnboarding(useDemo: Boolean) = context.dataStore.edit {
        it[Keys.onboarding] = true
        it[Keys.demo] = useDemo
    }

    suspend fun setTheme(value: String) = context.dataStore.edit { it[Keys.theme] = value }
    suspend fun setHaptics(value: Boolean) = context.dataStore.edit { it[Keys.haptics] = value }
    suspend fun setRestTimer(value: Boolean) = context.dataStore.edit { it[Keys.restTimer] = value }
    suspend fun setRestEndVibration(value: Boolean) = context.dataStore.edit { it[Keys.restEndVibration] = value }
    suspend fun setRestEndSound(value: Boolean) = context.dataStore.edit { it[Keys.restEndSound] = value }
    suspend fun setRestEndSoundType(value: String) = context.dataStore.edit {
        it[Keys.restEndSoundType] = RestAlertSound.fromId(value).id
    }
    suspend fun setRestEndSoundDurationSeconds(value: Int) = context.dataStore.edit {
        it[Keys.restEndSoundDurationSeconds] = value.coerceIn(1, 10)
    }
    suspend fun setFilters(value: String) = context.dataStore.edit { it[Keys.filters] = value }
    suspend fun setProfilePhotoUri(value: String) = context.dataStore.edit { preferences ->
        if (value.isBlank()) preferences.remove(Keys.profilePhotoUri)
        else preferences[Keys.profilePhotoUri] = value
    }
    suspend fun setHideBodyMetrics(value: Boolean) = context.dataStore.edit {
        it[Keys.hideBodyMetrics] = value
    }
    suspend fun setCustomWallpaperUri(value: String) = context.dataStore.edit { preferences ->
        if (value.isBlank()) {
            preferences.remove(Keys.customWallpaperUri)
            preferences[Keys.customWallpaperEnabled] = false
        } else {
            preferences[Keys.customWallpaperUri] = value
            preferences[Keys.customWallpaperEnabled] = true
        }
    }
    suspend fun setCustomWallpaperEnabled(value: Boolean) = context.dataStore.edit { preferences ->
        preferences[Keys.customWallpaperEnabled] = value && !preferences[Keys.customWallpaperUri].isNullOrBlank()
    }
    suspend fun setWallpaperDimPercent(value: Int) = context.dataStore.edit {
        it[Keys.wallpaperDimPercent] = value.coerceIn(20, 80)
    }
    suspend fun setCustomPalette(
        primary: String,
        secondary: String,
        background: String,
        surface: String,
        text: String,
    ) = context.dataStore.edit { preferences ->
        preferences[Keys.customPrimaryColor] = primary
        preferences[Keys.customSecondaryColor] = secondary
        preferences[Keys.customBackgroundColor] = background
        preferences[Keys.customSurfaceColor] = surface
        preferences[Keys.customTextColor] = text
        preferences[Keys.customPaletteEnabled] = true
    }
    suspend fun setCustomPaletteEnabled(value: Boolean) = context.dataStore.edit {
        it[Keys.customPaletteEnabled] = value
    }
    suspend fun resetCustomPalette() = context.dataStore.edit { preferences ->
        preferences.remove(Keys.customPaletteEnabled)
        preferences.remove(Keys.customPrimaryColor)
        preferences.remove(Keys.customSecondaryColor)
        preferences.remove(Keys.customBackgroundColor)
        preferences.remove(Keys.customSurfaceColor)
        preferences.remove(Keys.customTextColor)
    }
    suspend fun setWeeklyWorkoutGoal(value: Int) = context.dataStore.edit {
        it[Keys.weeklyWorkoutGoal] = value.coerceIn(1, 14)
    }
    suspend fun setGoalNotifications(value: Boolean) = context.dataStore.edit { it[Keys.goalNotifications] = value }
    suspend fun setStreakNotifications(value: Boolean) = context.dataStore.edit { it[Keys.streakNotifications] = value }
    suspend fun setDiscordWebhookEnabled(value: Boolean) = context.dataStore.edit { it[Keys.discordWebhookEnabled] = value }
    suspend fun setDiscordWebhookUrl(value: String) = context.dataStore.edit { preferences ->
        if (value.isBlank()) preferences.remove(Keys.discordWebhookUrl)
        else preferences[Keys.discordWebhookUrl] = value.trim()
    }
    suspend fun reset() = context.dataStore.edit { it.clear() }
}
