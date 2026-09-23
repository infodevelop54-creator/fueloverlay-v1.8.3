package com.infodevelop54.fueloverlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

class EngineStateManager(
    private val context: Context,
    private val repo: FuelStateRepository,
    private val uiCallback: UiCallback
) {
    interface UiCallback {
        fun showStatePrompt()
        fun showJamReprompt()
        fun showWarmupEndPrompt()
    }

    private var appStartTs = System.currentTimeMillis()
    fun setAppStartTs(ts: Long) { appStartTs = ts }

    fun setJamMode() = onJamConfirmed()
    fun setWarmupMode() = onWarmupStarted()
    fun setParkedMode() = onParkedEngineOn()
    fun setEngineOff() = onEngineOff()

    fun onTick() {
        val now = System.currentTimeMillis()
        val dtHours = 1f / 3600f

        when (repo.motionState) {
            MOTION_MOVING -> {
                if (repo.warmupPhase == WARMUP_FAST_IDLE) repo.warmupPhase = WARMUP_DONE
            }
            MOTION_STOPPED_RECENT -> {
                val recentStart = (now - appStartTs) < 5 * 60_000L
                if (recentStart && repo.warmupTrackingEnabled
                    && repo.warmupPhase == WARMUP_NONE
                    && repo.engineState != ENGINE_OFF) {
                    repo.engineState = ENGINE_ON
                    repo.warmupPhase = WARMUP_FAST_IDLE
                    repo.warmupStartedTs = now
                }
                if (repo.warmupPhase == WARMUP_FAST_IDLE && repo.warmupTrackingEnabled) {
                    addWarmup(repo.warmupRateLPerHour * dtHours)
                } else if (repo.engineState != ENGINE_OFF) {
                    addIdle(repo.idleRateLPerHour * dtHours)
                }
                if (now - repo.stopStartTs > STOP_ASK_AFTER_MS) {
                    repo.motionState = MOTION_STOPPED_ASKING
                    dispatchPrompt(PROMPT_STATE)
                }
            }
            MOTION_STOPPED_ASKING -> {
                if (now - repo.lastPromptTs > PROMPT_TIMEOUT_MS) {
                    repo.motionState = MOTION_JAM
                    repo.engineState = ENGINE_ON
                    repo.jamLevel = 6
                    if (repo.jamTrackingEnabled) addJam(repo.idleRateLPerHour * 1.35f * dtHours)
                    clearPrompt()
                }
            }
            MOTION_JAM -> {
                if (repo.engineState == ENGINE_ON && repo.jamTrackingEnabled) {
                    addJam(repo.idleRateLPerHour * jamMultiplier(repo.jamLevel) * dtHours)
                }
                if (now - repo.lastPromptTs > repromptInterval(repo.jamLevel)) {
                    dispatchPrompt(PROMPT_JAM_REPROMPT)
                    repo.lastPromptTs = now
                }
            }
            MOTION_PARKED -> {
                if (repo.engineState == ENGINE_ON) {
                    if (repo.warmupPhase == WARMUP_FAST_IDLE && repo.warmupTrackingEnabled) {
                        addWarmup(repo.warmupRateLPerHour * dtHours)
                        if (now - repo.warmupStartedTs > effectiveWarmupDuration()) {
                            dispatchPrompt(PROMPT_WARMUP_END)
                        }
                    } else {
                        addIdle(repo.idleRateLPerHour * dtHours)
                    }
                }
            }
        }
    }

    fun onMovementDetected() {
        repo.lastMovementTimestamp = System.currentTimeMillis()
        if (repo.motionState != MOTION_MOVING) {
            repo.motionState = MOTION_MOVING
            repo.jamLevel = 0
        }
    }
    fun onNoMovementTimeout() {
        if (repo.motionState == MOTION_MOVING) {
            repo.motionState = MOTION_STOPPED_RECENT
            repo.stopStartTs = System.currentTimeMillis()
        }
    }
    fun onJamConfirmed() { repo.motionState = MOTION_JAM; repo.engineState = ENGINE_ON; repo.jamLevel = 8; repo.lastPromptTs = System.currentTimeMillis(); clearPrompt() }
    fun onParkedEngineOn() { repo.motionState = MOTION_PARKED; repo.engineState = ENGINE_ON; repo.warmupPhase = WARMUP_DONE; repo.lastPromptTs = System.currentTimeMillis(); clearPrompt() }
    fun onParkedEngineOff() = onEngineOff()
    fun onEngineOff() { repo.motionState = MOTION_PARKED; repo.engineState = ENGINE_OFF; repo.warmupPhase = WARMUP_NONE; repo.lastPromptTs = System.currentTimeMillis(); clearPrompt() }
    fun onWarmupStarted() { repo.motionState = MOTION_PARKED; repo.engineState = ENGINE_ON; repo.warmupPhase = WARMUP_FAST_IDLE; repo.warmupStartedTs = System.currentTimeMillis(); repo.lastPromptTs = System.currentTimeMillis(); clearPrompt() }
    fun onWarmupEndConfirmed() { repo.warmupPhase = WARMUP_DONE; clearPrompt() }
    fun onWarmupExtended() { repo.warmupStartedTs = System.currentTimeMillis(); clearPrompt() }
    fun onStillInJam() { repo.lastPromptTs = System.currentTimeMillis(); clearPrompt() }
    fun onJamOver() { repo.motionState = MOTION_MOVING; repo.engineState = ENGINE_UNKNOWN; repo.jamLevel = 0; clearPrompt() }

    private fun addIdle(l: Float)   { repo.fuelParkedIdleL = repo.fuelParkedIdleL + l }
    private fun addJam(l: Float)    { repo.fuelJamL = repo.fuelJamL + l }
    private fun addWarmup(l: Float) { repo.fuelWarmupL = repo.fuelWarmupL + l }

    private fun dispatchPrompt(kind: Int) {
        repo.lastPromptTs = System.currentTimeMillis()
        when (repo.notifyMode) {
            NOTIFY_PUSH -> publishPushPrompt(kind)
            NOTIFY_DIALOG -> when (kind) {
                PROMPT_STATE -> uiCallback.showStatePrompt()
                PROMPT_JAM_REPROMPT -> uiCallback.showJamReprompt()
                PROMPT_WARMUP_END -> uiCallback.showWarmupEndPrompt()
            }
            NOTIFY_WIDGET_BUTTONS -> { repo.promptKind = kind; repo.promptActive = true }
        }
    }

    private fun clearPrompt() { repo.promptActive = false; repo.promptKind = PROMPT_NONE }

    private fun publishPushPrompt(kind: Int) {
        val nm = context.getSystemService(NotificationManager::class.java)
        ensurePromptChannel(nm)
        val title = when (kind) {
            PROMPT_STATE -> "Машина стоит больше 30 секунд"
            PROMPT_JAM_REPROMPT -> "Ещё в пробке?"
            else -> "Прогрев завершён?"
        }
        val items = when (kind) {
            PROMPT_STATE -> arrayOf("Пробка", "Стою, двиг. вкл", "Прогреваюсь", "Заглушил")
            PROMPT_JAM_REPROMPT -> arrayOf("Да, ещё в пробке", "Уже поехали", "Заглушил")
            else -> arrayOf("Да, прогрев закончен", "Заглушил", "Ещё греюсь")
        }
        val actions = when (kind) {
            PROMPT_STATE -> arrayOf(OverlayService.ACTION_JAM_CONFIRMED, OverlayService.ACTION_PARKED_ENGINE_ON, OverlayService.ACTION_WARMUP_STARTED, OverlayService.ACTION_ENGINE_OFF)
            PROMPT_JAM_REPROMPT -> arrayOf(OverlayService.ACTION_STILL_IN_JAM, OverlayService.ACTION_JAM_OVER, OverlayService.ACTION_ENGINE_OFF)
            else -> arrayOf(OverlayService.ACTION_WARMUP_DONE, OverlayService.ACTION_ENGINE_OFF, OverlayService.ACTION_WARMUP_EXTEND)
        }
        val builder = NotificationCompat.Builder(context, PROMPT_CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        items.forEachIndexed { i, label ->
            if (i < actions.size) builder.addAction(0, label, servicePi(actions[i], 500 + i))
        }
        nm.notify(PROMPT_NOTIFICATION_ID, builder.build())
    }

    private fun ensurePromptChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(PROMPT_CHANNEL_ID, "Опросы состояния авто", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(ch)
        }
    }

    private fun servicePi(action: String, requestCode: Int): PendingIntent {
        val i = Intent(context, OverlayService::class.java).apply { this.action = action }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getForegroundService(context, requestCode, i, flags)
    }

    fun effectiveSeason(): Int {
        val override = repo.warmupSeasonOverride
        if (override in SEASON_WINTER..SEASON_AUTUMN) return override
        val month = Calendar.getInstance().get(Calendar.MONTH)
        return when (month) {
            11, 0, 1 -> SEASON_WINTER
            2, 3, 4  -> SEASON_SPRING
            5, 6, 7  -> SEASON_SUMMER
            8, 9, 10 -> SEASON_AUTUMN
            else -> SEASON_SUMMER
        }
    }

    fun effectiveWarmupDuration(): Long {
        if (repo.warmupDurationMs > 0L) return repo.warmupDurationMs
        return when (effectiveSeason()) {
            SEASON_WINTER -> 360_000L
            SEASON_SPRING -> 180_000L
            SEASON_AUTUMN -> 180_000L
            SEASON_SUMMER -> 60_000L
            else -> 60_000L
        }
    }

    fun seasonLabel(): String = when (effectiveSeason()) {
        SEASON_WINTER -> "Зимний"; SEASON_SPRING -> "Весенний"
        SEASON_SUMMER -> "Летний"; SEASON_AUTUMN -> "Осенний"
        else -> "Летний"
    }

    private fun jamMultiplier(level: Int): Float = when (level) {
        in 1..3 -> 1.15f; in 4..7 -> 1.35f; else -> 1.55f
    }
    private fun repromptInterval(level: Int): Long = when (level) {
        in 1..3 -> 10 * 60_000L; in 4..7 -> 5 * 60_000L; else -> 3 * 60_000L
    }

    companion object {
        const val ENGINE_UNKNOWN = 0; const val ENGINE_ON = 1; const val ENGINE_OFF = 2
        const val MOTION_MOVING = 0; const val MOTION_STOPPED_RECENT = 1
        const val MOTION_STOPPED_ASKING = 2; const val MOTION_JAM = 3; const val MOTION_PARKED = 4
        const val WARMUP_NONE = 0; const val WARMUP_FAST_IDLE = 1; const val WARMUP_DONE = 2
        const val STOP_ASK_AFTER_MS = 30_000L
        const val PROMPT_TIMEOUT_MS = 120_000L
        const val NO_MOVEMENT_TIMEOUT_MS = 30_000L
        const val NOTIFY_PUSH = 0; const val NOTIFY_DIALOG = 1; const val NOTIFY_WIDGET_BUTTONS = 2
        const val PROMPT_NONE = 0; const val PROMPT_STATE = 1
        const val PROMPT_JAM_REPROMPT = 2; const val PROMPT_WARMUP_END = 3
        const val SEASON_WINTER = 0; const val SEASON_SPRING = 1
        const val SEASON_SUMMER = 2; const val SEASON_AUTUMN = 3
        const val PROMPT_CHANNEL_ID = "fuel_overlay_prompts"
        const val PROMPT_NOTIFICATION_ID = 2
    }
}