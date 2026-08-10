package com.liftly.app.integration.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.liftly.app.LiftlyApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WearCommandListenerService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WearSessionBridge.ACTION_PATH) return
        val parts = messageEvent.data.toString(Charsets.UTF_8).split('|')
        if (parts.size < 5) return
        val setId = parts[0]
        val reps = parts[1].toIntOrNull() ?: return
        val load = parts[2].toDoubleOrNull() ?: return
        val rir = parts[3].toIntOrNull()?.takeIf { it >= 0 }
        val complete = parts[4].toBooleanStrictOrNull() ?: return
        val app = applicationContext as LiftlyApplication
        scope.launch {
            runCatching {
                app.repository.updateSetFromWear(
                    setId = setId,
                    reps = reps,
                    loadKg = load,
                    rir = rir,
                    complete = complete,
                )
            }
        }
    }
}
