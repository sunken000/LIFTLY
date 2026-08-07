package com.anipresence.app.data.preferences

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anipresence.app.domain.model.ManualCorrection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64

private val Context.settingsDataStore by preferencesDataStore("anipresence_settings")

class SettingsRepository(private val context: Context) {
    private val enabledKey = booleanPreferencesKey("detection_enabled")
    private val webhookKey = stringPreferencesKey("discord_webhook_encrypted")
    private val messageIdKey = stringPreferencesKey("discord_message_id")
    private val correctionsKey = stringPreferencesKey("manual_corrections")
    private val remoteCacheKey = stringPreferencesKey("anime_remote_cache")

    val detectionEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[enabledKey] ?: false }

    suspend fun setDetectionEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[enabledKey] = value }
    }

    suspend fun saveWebhook(url: String) {
        val normalized = url.trim().trimEnd('/')
        context.settingsDataStore.edit {
            if (normalized.isBlank()) it.remove(webhookKey)
            else it[webhookKey] = SecretBox.encrypt(normalized)
            it.remove(messageIdKey)
        }
    }

    suspend fun webhook(): String? = context.settingsDataStore.data.first()[webhookKey]
        ?.let { runCatching { SecretBox.decrypt(it) }.getOrNull() }

    suspend fun saveMessageId(id: String?) {
        context.settingsDataStore.edit {
            if (id == null) it.remove(messageIdKey) else it[messageIdKey] = id
        }
    }

    suspend fun messageId(): String? = context.settingsDataStore.data.first()[messageIdKey]

    suspend fun saveCorrection(correction: ManualCorrection) {
        val all = readJsonObject(correctionsKey)
        all.put(correctionKey(correction.rawTitle, correction.packageName), JSONObject().apply {
            put("raw", correction.rawTitle)
            put("package", correction.packageName)
            put("title", correction.title)
            put("season", correction.season)
            put("episode", correction.episode)
        })
        context.settingsDataStore.edit { it[correctionsKey] = all.toString() }
    }

    suspend fun correction(rawTitle: String, packageName: String?): ManualCorrection? {
        val all = readJsonObject(correctionsKey)
        val exact = all.optJSONObject(correctionKey(rawTitle, packageName))
            ?: all.optJSONObject(correctionKey(rawTitle, null))
            ?: return null
        return ManualCorrection(
            rawTitle = exact.optString("raw", rawTitle),
            packageName = exact.optString("package").ifBlank { null },
            title = exact.optString("title"),
            season = exact.optIntOrNull("season"),
            episode = exact.optIntOrNull("episode"),
        )
    }

    suspend fun cachedAnime(query: String): String? =
        readJsonObject(remoteCacheKey).optString(normalizeKey(query)).ifBlank { null }

    suspend fun cacheAnime(query: String, value: String) {
        val cache = readJsonObject(remoteCacheKey)
        cache.put(normalizeKey(query), value)
        while (cache.length() > 50) cache.remove(cache.keys().next())
        context.settingsDataStore.edit { it[remoteCacheKey] = cache.toString() }
    }

    private suspend fun readJsonObject(key: androidx.datastore.preferences.core.Preferences.Key<String>) =
        runCatching { JSONObject(context.settingsDataStore.data.first()[key] ?: "{}") }
            .getOrDefault(JSONObject())

    private fun correctionKey(raw: String, packageName: String?) =
        "${packageName.orEmpty()}|${normalizeKey(raw)}"

    private fun normalizeKey(text: String) = text.lowercase().trim()
}

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private object SecretBox {
    private const val ALIAS = "anipresence_webhook"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    fun encrypt(clear: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = cipher.iv + cipher.doFinal(clear.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = payload.copyOfRange(0, 12)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(payload.copyOfRange(12, payload.size)).toString(Charsets.UTF_8)
    }
}
