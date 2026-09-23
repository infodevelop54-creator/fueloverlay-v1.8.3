package com.infodevelop54.fueloverlay

import android.content.Context
import org.json.JSONObject

class FuelStateRepository(context: Context) {

    init { ensureLoaded(context.applicationContext) }
    private fun read(): JSONObject = sharedState ?: JSONObject()

    private fun write(mutator: (JSONObject) -> Unit) {
        val obj = read(); mutator(obj)
        sharedState = obj; sharedDirty = true; maybeFlush()
    }

    var tankCapacityLiters: Float
        get() = read().optDouble("tankCapacityLiters", 50.0).toFloat()
        set(v) = write { it.put("tankCapacityLiters", v.toDouble()) }

    var averageConsumptionL100: Float
        get() = read().optDouble("averageConsumptionL100", 8.5).toFloat()
        set(v) = write { it.put("averageConsumptionL100", v.toDouble()) }

    var manualAverageConsumptionL100: Float
        get() = read().optDouble("manualAverageConsumptionL100", 8.5).toFloat()
        set(v) = write { it.put("manualAverageConsumptionL100", v.toDouble()) }

    var currentOdometerKm: Float
        get() = read().optDouble("currentOdometerKm", 0.0).toFloat()
        set(v) = write { it.put("currentOdometerKm", v.toDouble()) }

    var refuelOdometerKm: Float
        get() = read().optDouble("refuelOdometerKm", 0.0).toFloat()
        set(v) = write { it.put("refuelOdometerKm", v.toDouble()) }

    var extraFuelAddedL: Float
        get() = read().optDouble("extraFuelAddedL", 0.0).toFloat()
        set(v) = write { it.put("extraFuelAddedL", v.toDouble()) }

    var lastSyncedOdometerKm: Float
        get() = read().optDouble("lastSyncedOdometerKm", 0.0).toFloat()
        set(v) = write { it.put("lastSyncedOdometerKm", v.toDouble()) }

    var gpsTrackingEnabled: Boolean
        get() = read().optBoolean("gpsTrackingEnabled", false)
        set(v) = write { it.put("gpsTrackingEnabled", v) }

    var gpsAccumulatedMeters: Double
        get() = read().optDouble("gpsAccumulatedMeters", 0.0)
        set(v) = write { it.put("gpsAccumulatedMeters", v) }

    var gpsCalibrationFactor: Float
        get() = read().optDouble("gpsCalibrationFactor", 1.0).toFloat()
        set(v) = write { it.put("gpsCalibrationFactor", v.toDouble()) }

    var overlayX: Int
        get() = read().optInt("overlayX", 100)
        set(v) = write { it.put("overlayX", v) }

    var overlayY: Int
        get() = read().optInt("overlayY", 200)
        set(v) = write { it.put("overlayY", v) }

    var widgetVisible: Boolean
        get() = read().optBoolean("widgetVisible", true)
        set(v) = write { it.put("widgetVisible", v) }

    var startHidden: Boolean
        get() = read().optBoolean("startHidden", false)
        set(v) = write { it.put("startHidden", v) }

    var hasLaunchedBefore: Boolean
        get() = read().optBoolean("hasLaunchedBefore", false)
        set(v) = write { it.put("hasLaunchedBefore", v) }

    var notifyMode: Int
        get() = read().optInt("notifyMode", 1)
        set(v) = write { it.put("notifyMode", v) }

    var warmupSeasonOverride: Int
        get() = read().optInt("warmupSeasonOverride", -1)
        set(v) = write { it.put("warmupSeasonOverride", v) }

    var promptActive: Boolean
        get() = read().optBoolean("promptActive", false)
        set(v) = write { it.put("promptActive", v) }

    var promptKind: Int
        get() = read().optInt("promptKind", 0)
        set(v) = write { it.put("promptKind", v) }

    var engineState: Int
        get() = read().optInt("engineState", 0)
        set(v) = write { it.put("engineState", v) }

    var motionState: Int
        get() = read().optInt("motionState", 0)
        set(v) = write { it.put("motionState", v) }

    var stopStartTs: Long
        get() = read().optLong("stopStartTs", 0L)
        set(v) = write { it.put("stopStartTs", v) }

    var lastPromptTs: Long
        get() = read().optLong("lastPromptTs", 0L)
        set(v) = write { it.put("lastPromptTs", v) }

    var jamLevel: Int
        get() = read().optInt("jamLevel", 5)
        set(v) = write { it.put("jamLevel", v) }

    var warmupStartedTs: Long
        get() = read().optLong("warmupStartedTs", 0L)
        set(v) = write { it.put("warmupStartedTs", v) }

    var warmupPhase: Int
        get() = read().optInt("warmupPhase", 0)
        set(v) = write { it.put("warmupPhase", v) }

    var lastMovementTimestamp: Long
        get() = read().optLong("lastMovementTimestamp", 0L)
        set(v) = write { it.put("lastMovementTimestamp", v) }

    var fuelJamL: Float
        get() = read().optDouble("fuelJamL", 0.0).toFloat()
        set(v) = write { it.put("fuelJamL", v.toDouble()) }

    var fuelWarmupL: Float
        get() = read().optDouble("fuelWarmupL", 0.0).toFloat()
        set(v) = write { it.put("fuelWarmupL", v.toDouble()) }

    var fuelParkedIdleL: Float
        get() = read().optDouble("fuelParkedIdleL", 0.0).toFloat()
        set(v) = write { it.put("fuelParkedIdleL", v.toDouble()) }

    val fuelNoDistanceL: Float
        get() = fuelJamL + fuelWarmupL + fuelParkedIdleL

    var idleRateLPerHour: Float
        get() = read().optDouble("idleRateLPerHour", 0.8).toFloat()
        set(v) = write { it.put("idleRateLPerHour", v.toDouble()) }

    var warmupRateLPerHour: Float
        get() = read().optDouble("warmupRateLPerHour", 1.8).toFloat()
        set(v) = write { it.put("warmupRateLPerHour", v.toDouble()) }

    var warmupDurationMs: Long
        get() = read().optLong("warmupDurationMs", 0L)
        set(v) = write { it.put("warmupDurationMs", v) }

    var jamTrackingEnabled: Boolean
        get() = read().optBoolean("jamTrackingEnabled", true)
        set(v) = write { it.put("jamTrackingEnabled", v) }

    var warmupTrackingEnabled: Boolean
        get() = read().optBoolean("warmupTrackingEnabled", true)
        set(v) = write { it.put("warmupTrackingEnabled", v) }

    fun addGpsDistanceMeters(meters: Double): Float {
        val newAcc = gpsAccumulatedMeters + meters
        gpsAccumulatedMeters = newAcc
        val km = newAcc / 1000.0 * gpsCalibrationFactor
        val newOdo = lastSyncedOdometerKm + km.toFloat()
        currentOdometerKm = newOdo
        return newOdo
    }

    fun syncOdometer(realOdometerKm: Float) {
        val gpsKm = gpsAccumulatedMeters / 1000.0
        if (gpsKm > 0.2) {
            val drivenByOdo = realOdometerKm - lastSyncedOdometerKm
            if (drivenByOdo > 0) {
                val raw = (drivenByOdo / gpsKm).toFloat().coerceIn(0.5f, 2.0f)
                gpsCalibrationFactor = 0.7f * gpsCalibrationFactor + 0.3f * raw
            }
        }
        currentOdometerKm = realOdometerKm
        lastSyncedOdometerKm = realOdometerKm
        gpsAccumulatedMeters = 0.0
    }

    companion object {
        private val lock = Any()
        @Volatile private var sharedState: JSONObject? = null
        @Volatile private var sharedCtx: Context? = null
        private var sharedDirty = false
        private var lastFlush = 0L
        private const val FLUSH_MS = 20_000L

        private fun ensureLoaded(ctx: Context) {
            synchronized(lock) {
                sharedCtx = ctx
                if (sharedState == null) {
                    sharedState = FuelDatabase.readJson(ctx, FuelDatabase.settingsFile(ctx))
                        ?: JSONObject()
                }
            }
        }

        fun maybeFlush() {
            if (!sharedDirty) return
            if (System.currentTimeMillis() - lastFlush < FLUSH_MS) return
            flush()
        }

        fun flush() {
            synchronized(lock) {
                val ctx = sharedCtx ?: return
                val st = sharedState ?: return
                if (!sharedDirty) return
                FuelDatabase.writeJson(FuelDatabase.settingsFile(ctx), st)
                sharedDirty = false
                lastFlush = System.currentTimeMillis()
            }
        }

        fun invalidate(context: Context) {
            synchronized(lock) {
                sharedCtx = context.applicationContext
                sharedState = null; sharedDirty = false; lastFlush = 0L
            }
            ensureLoaded(context.applicationContext)
        }
    }
}