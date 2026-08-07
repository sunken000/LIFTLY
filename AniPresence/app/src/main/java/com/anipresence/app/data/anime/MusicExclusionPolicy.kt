package com.anipresence.app.data.anime

object MusicExclusionPolicy {
    private val excludedPackages = setOf(
        "com.spotify.music",
        "com.google.android.apps.youtube.music",
        "com.apple.android.music",
        "com.amazon.mp3",
        "com.soundcloud.android",
        "deezer.android.app",
        "com.pandora.android",
        "com.tidal.android",
    )

    fun isExcluded(packageName: String): Boolean = packageName in excludedPackages
}
