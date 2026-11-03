package app.farwalker.work

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.Period

/**
 * Steps from Health Connect: the phone's shared health store, where Samsung Health, Mi Fitness,
 * Zepp Life, Google Fit, watches and bands put the steps they count. Read as totals per day, so the
 * traveller walks as far as the health app says, and steps a watch hands over late still arrive.
 */
class HealthSteps(private val context: Context) {
    private val status: Int get() = HealthConnectClient.getSdkStatus(context)

    /** Health Connect is on the phone and ready. */
    val available: Boolean get() = status == HealthConnectClient.SDK_AVAILABLE

    /** Health Connect could work here once it is installed or updated from the store. */
    val installable: Boolean get() = status == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    private var made: HealthConnectClient? = null

    /** Made once Health Connect is there, which it may become only after the app has started. */
    private val client: HealthConnectClient?
        get() = made ?: if (!available) null else runCatching { HealthConnectClient.getOrCreate(context) }
            .onFailure { Log.w(TAG, "client", it) }.getOrNull()?.also { made = it }

    /** What to ask for: the steps, and reading them in the background where the phone supports it. */
    fun permissions(): Set<String> {
        val c = client ?: return setOf(READ)
        val background = runCatching {
            c.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        }.getOrDefault(false)
        return if (background) setOf(READ, HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND) else setOf(READ)
    }

    suspend fun allowed(): Boolean {
        val c = client ?: return false
        return runCatching { READ in c.permissionController.getGrantedPermissions() }.getOrDefault(false)
    }

    /** Steps per day from [from] to [to], keyed by epoch day; null if Health Connect can't be read now. */
    suspend fun days(from: LocalDate, to: LocalDate): Map<Long, Long>? {
        val c = client ?: return null
        return runCatching {
            val req = AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()),
                timeRangeSlicer = Period.ofDays(1),
            )
            c.aggregateGroupByPeriod(req).associate { g ->
                g.startTime.toLocalDate().toEpochDay() to (g.result[StepsRecord.COUNT_TOTAL] ?: 0L)
            }
        }.onFailure { Log.w(TAG, "read", it) }.getOrNull()
    }

    /** The store page where Health Connect can be installed or updated. */
    fun storeIntent(): Intent = Intent(Intent.ACTION_VIEW, "market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding".toUri())
        .setPackage("com.android.vending")
        .putExtra("overlay", true)
        .putExtra("callerId", context.packageName)

    companion object {
        private const val TAG = "HealthSteps"
        const val PROVIDER = "com.google.android.apps.healthdata"
        val READ = HealthPermission.getReadPermission(StepsRecord::class)

        /** Health Connect's own screen for granting permissions. */
        fun requestContract() = PermissionController.createRequestPermissionResultContract()
    }
}
