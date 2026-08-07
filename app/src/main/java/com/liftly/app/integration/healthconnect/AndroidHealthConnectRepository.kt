package com.liftly.app.integration.healthconnect

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

object LiftlyHealthPermissions {
    val readWeight: String = HealthPermission.getReadPermission(WeightRecord::class)
    val readSleep: String = HealthPermission.getReadPermission(SleepSessionRecord::class)
    val writeExercise: String = HealthPermission.getWritePermission(ExerciseSessionRecord::class)

    val all: Set<String> = setOf(readWeight, readSleep, writeExercise)
    val reads: Set<String> = setOf(readWeight, readSleep)
}

class AndroidHealthConnectRepository(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : HealthConnectRepository {
    private val applicationContext = context.applicationContext

    @Volatile
    private var cachedClient: HealthConnectClient? = null

    override fun availability(): HealthConnectAvailability =
        when (HealthConnectClient.getSdkStatus(applicationContext)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED
            else -> HealthConnectAvailability.UNAVAILABLE
        }

    override suspend fun grantedPermissions(): HealthConnectPermissionState {
        if (availability() != HealthConnectAvailability.AVAILABLE) {
            return HealthConnectPermissionState.None
        }
        val granted = runCatching { client().permissionController.getGrantedPermissions() }
            .getOrDefault(emptySet())
        return granted.toLiftlyPermissionState()
    }

    override suspend fun readLatestMetrics(
        lookback: Duration,
    ): HealthConnectReadResult {
        val currentAvailability = availability()
        if (currentAvailability != HealthConnectAvailability.AVAILABLE) {
            return HealthConnectReadResult.Unavailable(currentAvailability)
        }

        val healthClient = runCatching { client() }
            .getOrElse { return HealthConnectReadResult.Failed(it.toFailureReason()) }
        val granted = runCatching {
            healthClient.permissionController.getGrantedPermissions()
        }.getOrElse {
            return HealthConnectReadResult.Failed(it.toFailureReason())
        }
        val permissionState = granted.toLiftlyPermissionState()
        val now = clock.instant()
        val safeLookback = lookback
            .takeIf { !it.isNegative && !it.isZero }
            ?.coerceAtMost(Duration.ofDays(HealthConnectRepository.DEFAULT_HEALTH_LOOKBACK_DAYS))
            ?: Duration.ofDays(HealthConnectRepository.DEFAULT_HEALTH_LOOKBACK_DAYS)
        val start = now.minus(safeLookback)
        val failedMetrics = mutableSetOf<HealthMetric>()

        val latestWeight = if (permissionState.canReadWeight) {
            runCatching { healthClient.readLatestWeight(start, now) }
                .onFailure { failedMetrics += HealthMetric.WEIGHT }
                .getOrNull()
        } else {
            null
        }

        val latestSleep = if (permissionState.canReadSleep) {
            runCatching { healthClient.readLatestSleep(start, now) }
                .onFailure { failedMetrics += HealthMetric.SLEEP }
                .getOrNull()
        } else {
            null
        }

        return HealthConnectReadResult.Success(
            HealthConnectSnapshot(
                permissions = permissionState,
                latestWeight = latestWeight,
                latestSleep = latestSleep,
                failedMetrics = failedMetrics,
            ),
        )
    }

    override suspend fun exportWorkout(
        payload: WorkoutHealthExport,
    ): HealthConnectExportResult {
        val currentAvailability = availability()
        if (currentAvailability != HealthConnectAvailability.AVAILABLE) {
            return HealthConnectExportResult.Unavailable(currentAvailability)
        }
        if (!payload.startedAt.isBefore(payload.endedAt)) {
            return HealthConnectExportResult.Skipped(
                HealthConnectExportSkipReason.INVALID_TIME_RANGE,
            )
        }

        val healthClient = runCatching { client() }
            .getOrElse { return HealthConnectExportResult.Failed(it.toFailureReason()) }
        val granted = runCatching {
            healthClient.permissionController.getGrantedPermissions()
        }.getOrElse {
            return HealthConnectExportResult.Failed(it.toFailureReason())
        }
        if (LiftlyHealthPermissions.writeExercise !in granted) {
            return HealthConnectExportResult.Skipped(
                HealthConnectExportSkipReason.PERMISSION_NOT_GRANTED,
            )
        }

        val startOffset = zoneId.rules.getOffset(payload.startedAt)
        val endOffset = zoneId.rules.getOffset(payload.endedAt)
        val device = Device(
            type = Device.TYPE_PHONE,
            manufacturer = Build.MANUFACTURER.takeIf { it.isNotBlank() },
            model = Build.MODEL.takeIf { it.isNotBlank() },
        )
        val record = ExerciseSessionRecord(
            startTime = payload.startedAt,
            startZoneOffset = startOffset,
            endTime = payload.endedAt,
            endZoneOffset = endOffset,
            metadata = Metadata.activelyRecorded(
                device = device,
                clientRecordId = payload.clientRecordId,
                clientRecordVersion = payload.clientRecordVersion,
            ),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = payload.title,
            notes = payload.notes,
        )

        return runCatching {
            val response = healthClient.insertRecords(listOf(record))
            HealthConnectExportResult.Exported(
                clientRecordId = payload.clientRecordId,
                healthConnectRecordId = response.recordIdsList.firstOrNull(),
            )
        }.getOrElse {
            HealthConnectExportResult.Failed(it.toFailureReason())
        }
    }

    private fun client(): HealthConnectClient {
        cachedClient?.let { return it }
        return synchronized(this) {
            cachedClient ?: HealthConnectClient.getOrCreate(applicationContext)
                .also { cachedClient = it }
        }
    }

    companion object {
        private const val HEALTH_CONNECT_PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

        fun permissionRequestContract(): ActivityResultContract<Set<String>, Set<String>> =
            PermissionController.createRequestPermissionResultContract()

        fun manageHealthConnectIntent(context: Context): Intent =
            HealthConnectClient.getHealthConnectManageDataIntent(context)

        fun providerInstallOrUpdateIntent(context: Context): Intent {
            val uri = Uri.parse(
                "market://details?id=$HEALTH_CONNECT_PROVIDER_PACKAGE" +
                    "&url=healthconnect%3A%2F%2Fonboarding",
            )
            return Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.android.vending")
                putExtra("overlay", true)
                putExtra("callerId", context.packageName)
            }
        }

        fun providerWebInstallIntent(): Intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(
                "https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PROVIDER_PACKAGE",
            ),
        )
    }
}

private suspend fun HealthConnectClient.readLatestWeight(
    start: Instant,
    end: Instant,
): HealthWeightMeasurement? {
    val record = readRecords(
        ReadRecordsRequest(
            recordType = WeightRecord::class,
            timeRangeFilter = TimeRangeFilter.between(start, end),
            ascendingOrder = false,
            pageSize = 1,
        ),
    ).records.firstOrNull() ?: return null

    return HealthWeightMeasurement(
        kilograms = record.weight.inKilograms,
        measuredAt = record.time,
        sourcePackageName = record.metadata.dataOrigin.packageName,
    )
}

private suspend fun HealthConnectClient.readLatestSleep(
    start: Instant,
    end: Instant,
): HealthSleepMeasurement? {
    val record = readRecords(
        ReadRecordsRequest(
            recordType = SleepSessionRecord::class,
            timeRangeFilter = TimeRangeFilter.between(start, end),
            ascendingOrder = false,
            pageSize = 8,
        ),
    ).records.maxByOrNull { it.endTime } ?: return null

    val timeInBed = Duration.between(record.startTime, record.endTime)
    val sleepStages = record.stages.filter { stage ->
        stage.stage in setOf(
            SleepSessionRecord.STAGE_TYPE_SLEEPING,
            SleepSessionRecord.STAGE_TYPE_LIGHT,
            SleepSessionRecord.STAGE_TYPE_DEEP,
            SleepSessionRecord.STAGE_TYPE_REM,
        )
    }
    val sleepDuration = if (sleepStages.isEmpty()) {
        timeInBed
    } else {
        sleepStages.fold(Duration.ZERO) { total, stage ->
            total.plus(Duration.between(stage.startTime, stage.endTime))
        }
    }

    return HealthSleepMeasurement(
        startedAt = record.startTime,
        endedAt = record.endTime,
        sleepDuration = sleepDuration,
        timeInBed = timeInBed,
        sourcePackageName = record.metadata.dataOrigin.packageName,
    )
}

private fun Set<String>.toLiftlyPermissionState(): HealthConnectPermissionState =
    HealthConnectPermissionState(
        canReadWeight = LiftlyHealthPermissions.readWeight in this,
        canReadSleep = LiftlyHealthPermissions.readSleep in this,
        canWriteExercise = LiftlyHealthPermissions.writeExercise in this,
    )

private fun Throwable.toFailureReason(): HealthConnectFailureReason = when (this) {
    is SecurityException -> HealthConnectFailureReason.PERMISSION_REVOKED
    is IOException -> HealthConnectFailureReason.IO_ERROR
    is IllegalStateException,
    is UnsupportedOperationException -> HealthConnectFailureReason.PROVIDER_ERROR
    else -> HealthConnectFailureReason.UNKNOWN
}
