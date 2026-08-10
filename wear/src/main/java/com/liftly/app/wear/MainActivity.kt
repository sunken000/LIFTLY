package com.liftly.app.wear

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import java.util.Locale

private const val SESSION_PATH = "/liftly/session"
private const val ACTION_PATH = "/liftly/action/set"

data class WearSessionState(
    val updatedAt: Long = 0L,
    val active: Boolean = false,
    val workout: String = "",
    val completed: Int = 0,
    val total: Int = 0,
    val setId: String = "",
    val exercise: String = "",
    val setNumber: Int = 0,
    val reps: Int = 0,
    val loadKg: Double = 0.0,
    val rir: Int = -1,
)

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {
    private var session by mutableStateOf(WearSessionState())
    private var heartRate by mutableStateOf<Double?>(null)
    private val dataClient by lazy { Wearable.getDataClient(this) }
    private val healthClient by lazy { HealthServices.getClient(this) }
    // ExerciseClient is kept as the workout-lifecycle owner; MeasureClient supplies the foreground HR value.
    private val exerciseClient by lazy { healthClient.exerciseClient }
    private val measureClient by lazy { healthClient.measureClient }

    private val heartRateCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) = Unit
        override fun onDataReceived(data: DataPointContainer) {
            heartRate = data.getData(DataType.HEART_RATE_BPM).lastOrNull()?.value
        }
        override fun onRegistrationFailed(throwable: Throwable) {
            heartRate = null
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) registerHeartRate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                WearSession(
                    state = session,
                    heartRate = heartRate,
                    onUpdate = ::sendSetUpdate,
                )
            }
        }
        val permission = if (Build.VERSION.SDK_INT >= 36) "android.permission.health.READ_HEART_RATE" else Manifest.permission.BODY_SENSORS
        if (checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            registerHeartRate()
        } else {
            permissionLauncher.launch(permission)
        }
        // Capability lookup initializes the ExerciseClient path without stealing an exercise owned by another app.
        exerciseClient.getCapabilitiesAsync()
    }

    override fun onStart() {
        super.onStart()
        dataClient.addListener(this)
        dataClient.dataItems.addOnSuccessListener { buffer ->
            try {
                buffer.firstOrNull { it.uri.path == SESSION_PATH }?.let(::consume)
            } finally {
                buffer.release()
            }
        }
    }

    override fun onStop() {
        dataClient.removeListener(this)
        measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, heartRateCallback)
        super.onStop()
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == SESSION_PATH) consume(event.dataItem)
        }
    }

    private fun consume(item: com.google.android.gms.wearable.DataItem) {
        val map = DataMapItem.fromDataItem(item).dataMap
        session = WearSessionState(
            updatedAt = map.getLong("updatedAt"),
            active = map.getBoolean("active"),
            workout = map.getString("workout").orEmpty(),
            completed = map.getInt("completed"),
            total = map.getInt("total"),
            setId = map.getString("setId").orEmpty(),
            exercise = map.getString("exercise").orEmpty(),
            setNumber = map.getInt("setNumber"),
            reps = map.getInt("reps"),
            loadKg = map.getDouble("loadKg"),
            rir = map.getInt("rir", -1),
        )
    }

    private fun registerHeartRate() {
        runCatching { measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, heartRateCallback) }
    }

    private fun sendSetUpdate(setId: String, reps: Int, loadKg: Double, rir: Int, complete: Boolean) {
        if (setId.isBlank()) return
        val payload = "$setId|${reps.coerceAtLeast(0)}|${loadKg.coerceAtLeast(0.0)}|$rir|$complete".toByteArray()
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            nodes.filter { it.isNearby }.ifEmpty { nodes }.forEach { node ->
                Wearable.getMessageClient(this).sendMessage(node.id, ACTION_PATH, payload)
            }
        }
    }
}

@Composable
private fun WearSession(
    state: WearSessionState,
    heartRate: Double?,
    onUpdate: (String, Int, Double, Int, Boolean) -> Unit,
) {
    if (!state.active || state.setId.isBlank()) {
        Column(
            Modifier.fillMaxSize().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("LIFTLY", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text("Nenhum treino ativo no celular.", textAlign = TextAlign.Center)
            heartRate?.let { Text("FC ${it.toInt()} bpm", style = MaterialTheme.typography.labelMedium) }
        }
        return
    }

    var reps by remember(state.setId, state.updatedAt) { mutableIntStateOf(state.reps) }
    var load by remember(state.setId, state.updatedAt) { mutableDoubleStateOf(state.loadKg) }
    var rir by remember(state.setId, state.updatedAt) { mutableIntStateOf(state.rir) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text("${state.completed}/${state.total} • ${state.workout}", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        Text(state.exercise, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 2)
        Text("SÉRIE ${state.setNumber}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        heartRate?.let { Text("♥ ${it.toInt()} bpm", style = MaterialTheme.typography.labelMedium) }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Button(onClick = { reps = (reps - 1).coerceAtLeast(0); onUpdate(state.setId, reps, load, rir, false) }, modifier = Modifier.weight(1f)) { Text("−") }
            Column(Modifier.weight(1.3f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(reps.toString(), fontWeight = FontWeight.Black)
                Text("REPS", style = MaterialTheme.typography.labelSmall)
            }
            Button(onClick = { reps += 1; onUpdate(state.setId, reps, load, rir, false) }, modifier = Modifier.weight(1f)) { Text("+") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Button(onClick = { load = (load - 0.5).coerceAtLeast(0.0); onUpdate(state.setId, reps, load, rir, false) }, modifier = Modifier.weight(1f)) { Text("−") }
            Column(Modifier.weight(1.3f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (load % 1.0 == 0.0) load.toInt().toString() else String.format(Locale.US, "%.1f", load), fontWeight = FontWeight.Black)
                Text("KG", style = MaterialTheme.typography.labelSmall)
            }
            Button(onClick = { load += 0.5; onUpdate(state.setId, reps, load, rir, false) }, modifier = Modifier.weight(1f)) { Text("+") }
        }
        Button(onClick = { rir = if (rir >= 5) -1 else rir + 1; onUpdate(state.setId, reps, load, rir, false) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (rir < 0) "RIR —" else "RIR $rir")
        }
        Button(onClick = { onUpdate(state.setId, reps, load, rir, true) }, modifier = Modifier.fillMaxWidth()) {
            Text("CONCLUIR SÉRIE", fontWeight = FontWeight.Black)
        }
    }
}
