package com.liftly.app.integration.spotify

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens playback and authentication in Spotify; Liftly never handles Spotify credentials. */
object SpotifyLauncher {
    private const val SPOTIFY_ANDROID_PACKAGE = "com.spotify.music"

    fun openPlaylist(context: Context, links: SpotifyPlaylistLinks): Boolean {
        val referrer = Uri.parse("android-app://${context.packageName}")
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(links.spotifyUri)).apply {
            setPackage(SPOTIFY_ANDROID_PACKAGE)
            putExtra(Intent.EXTRA_REFERRER, referrer)
            addLaunchFlagWhenNeeded(context)
        }
        if (context.tryStartActivity(appIntent)) return true

        val webUri = Uri.parse(links.webUrl).buildUpon()
            .appendQueryParameter("utm_campaign", context.packageName)
            .build()
        val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            putExtra(Intent.EXTRA_REFERRER, referrer)
            addLaunchFlagWhenNeeded(context)
        }
        return context.tryStartActivity(webIntent)
    }

    private fun Intent.addLaunchFlagWhenNeeded(context: Context) {
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun Context.tryStartActivity(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
