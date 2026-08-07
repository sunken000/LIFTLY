package com.liftly.app.integration.healthconnect

import java.time.Duration
import java.time.Instant

/**
 * Domain-facing contract for Health Connect.
 *
 * Keeping AndroidX record types out of this contract makes the integration optional and keeps the
 * workout/domain code unit-testable without an Android device.
 */
interface HealthConnectRepository {
    fun availability(): HealthConnectAvailability

    suspend fun grantedPermissions(): HealthConnectPermissionState

    suspend fun readLatestMetrics(
        lookback: Duration = Duration.ofDays(DEFAULT_HEALTH_LOOKBACK_DAYS),
    ): HealthConnectReadResult

    suspend fun exportWorkout(payload: WorkoutHealthExport): HealthConnectExportResult

    companion object {
        const val DEFAULT_HEALTH_LOOKBACK_DAYS = 30L
    }
}

enum class HealthConnectAvailability {
    AVAILABLE,
    PROVIDER_UPDATE_REQUIRED,
    UNAVAILABLE,
}

data class HealthConnectPermissionState(
    val canReadWeight: Boolean,
    val canReadSleep: Boolean,
    val canWriteExercise: Boolean,
) {
    val hasAnyReadPermission: Boolean
        get() = canReadWeight || canReadSleep

    val hasAllPermissions: Boolean
        get() = canReadWeight && canReadSleep && canWriteExercise

    companion object {
        val None = HealthConnectPermissionState(
            canReadWeight = false,
            canReadSleep = false,
            canWriteExercise = false,
        )
    }
}

data class HealthWeightMeasurement(
    val kilograms: Double,
    val measuredAt: Instant,
    val sourcePackageName: String,
)

/**
 * [timeInBed] is the complete session interval. [sleepDuration] excludes explicitly awake and
 * out-of-bed stages when the provider supplied stages; otherwise it falls back to the interval.
 */
data class HealthSleepMeasurement(
    val startedAt: Instant,
    val endedAt: Instant,
    val sleepDuration: Duration,
    val timeInBed: Duration,
    val sourcePackageName: String,
)

enum class HealthMetric {
    WEIGHT,
    SLEEP,
}

data class HealthConnectSnapshot(
    val permissions: HealthConnectPermissionState,
    val latestWeight: HealthWeightMeasurement?,
    val latestSleep: HealthSleepMeasurement?,
    /** A metric appears here only when it was permitted but its read operation failed. */
    val failedMetrics: Set<HealthMetric> = emptySet(),
)

sealed interface HealthConnectReadResult {
    data class Success(val snapshot: HealthConnectSnapshot) : HealthConnectReadResult

    data class Unavailable(
        val availability: HealthConnectAvailability,
    ) : HealthConnectReadResult

    data class Failed(
        val reason: HealthConnectFailureReason,
    ) : HealthConnectReadResult
}

enum class HealthConnectFailureReason {
    PERMISSION_REVOKED,
    PROVIDER_ERROR,
    IO_ERROR,
    UNKNOWN,
}

/**
 * Stable, idempotent representation of a Liftly workout that can be written to Health Connect.
 */
data class WorkoutHealthExport(
    val sessionId: String,
    val title: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val notes: String,
    val clientRecordVersion: Long,
) {
    val clientRecordId: String
        get() = "liftly-workout-$sessionId"
}

enum class HealthConnectExportSkipReason {
    TEST_SESSION,
    SESSION_NOT_FINISHED,
    INVALID_TIME_RANGE,
    PERMISSION_NOT_GRANTED,
}

sealed interface WorkoutHealthExportPreparation {
    data class Ready(val payload: WorkoutHealthExport) : WorkoutHealthExportPreparation

    data class Skipped(
        val reason: HealthConnectExportSkipReason,
    ) : WorkoutHealthExportPreparation
}

sealed interface HealthConnectExportResult {
    data class Exported(
        val clientRecordId: String,
        val healthConnectRecordId: String?,
    ) : HealthConnectExportResult

    data class Skipped(
        val reason: HealthConnectExportSkipReason,
    ) : HealthConnectExportResult

    data class Unavailable(
        val availability: HealthConnectAvailability,
    ) : HealthConnectExportResult

    data class Failed(
        val reason: HealthConnectFailureReason,
    ) : HealthConnectExportResult
}
