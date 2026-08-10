package com.liftly.app.integration.wear

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.liftly.app.data.SessionEntity
import com.liftly.app.data.SessionSetEntity

object WearSessionBridge {
    const val SESSION_PATH = "/liftly/session"
    const val ACTION_PATH = "/liftly/action/set"

    fun publish(
        context: Context,
        sessions: List<SessionEntity>,
        sets: List<SessionSetEntity>,
    ) {
        val active = sessions.firstOrNull { it.status == "Em andamento" }
        val activeSets = sets
            .filter { it.sessionId == active?.id }
            .sortedWith(compareBy<SessionSetEntity> { it.exerciseOrder }.thenBy { it.setNumber })
        val next = activeSets.firstOrNull { !it.completed }
        val request = PutDataMapRequest.create(SESSION_PATH).apply {
            dataMap.putLong("updatedAt", System.currentTimeMillis())
            dataMap.putBoolean("active", active != null)
            dataMap.putString("workout", active?.workoutName.orEmpty())
            dataMap.putInt("completed", activeSets.count { it.completed })
            dataMap.putInt("total", activeSets.size)
            dataMap.putString("setId", next?.id.orEmpty())
            dataMap.putString("exercise", next?.exerciseName.orEmpty())
            dataMap.putInt("setNumber", next?.setNumber ?: 0)
            dataMap.putInt("reps", next?.reps ?: 0)
            dataMap.putDouble("loadKg", next?.loadKg ?: 0.0)
            dataMap.putInt("rir", next?.rir ?: -1)
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request)
    }
}
