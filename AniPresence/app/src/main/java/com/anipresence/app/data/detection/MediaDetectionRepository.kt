package com.anipresence.app.data.detection

import com.anipresence.app.domain.model.DetectedMedia
import kotlinx.coroutines.flow.StateFlow

interface MediaDetectionRepository {
    val currentMedia: StateFlow<DetectedMedia?>
    fun start()
    fun stop()
}
