package com.infodevelop54.fueloverlay

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.widget.RemoteViews
import java.util.Locale

class FuelSpentWidget : BaseFuelWidget() {
    override val layoutId = R.layout.fuel_widget_spent
    override val hasIcon = true; override val hasLabel = true
    override fun bindValue(views: RemoteViews, context: Context, repo: FuelStateRepository, app: WidgetAppearance) {
        val mileage = (repo.currentOdometerKm - repo.refuelOdometerKm).coerceAtLeast(0f)
        val spent = mileage * repo.averageConsumptionL100 / 100f
        views.setTextViewText(R.id.cwgValue, String.format(Locale.US, "%.1f л", spent))
        applyTextStyle(views, app, R.id.cwgLabel, R.id.cwgValue)
        applyIconBitmap(views, context, app, "spent", app.spentIconIndex)
    }
}

class FuelRemainingWidget : BaseFuelWidget() {
    override val layoutId = R.layout.fuel_widget_remaining
    override val hasIcon = true; override val hasLabel = true
    override fun bindValue(views: RemoteViews, context: Context, repo: FuelStateRepository, app: WidgetAppearance) {
        val mileage = (repo.currentOdometerKm - repo.refuelOdometerKm).coerceAtLeast(0f)
        val spent = mileage * repo.averageConsumptionL100 / 100f
        val remaining = (repo.tankCapacityLiters - spent - repo.fuelNoDistanceL + repo.extraFuelAddedL)
            .coerceIn(0f, repo.tankCapacityLiters)
        views.setTextViewText(R.id.cwgValue, String.format(Locale.US, "%.1f л", remaining))
        applyTextStyle(views, app, R.id.cwgLabel, R.id.cwgValue)
        applyIconBitmap(views, context, app, "remaining", app.remainingIconIndex)
    }
}

class FuelMileageWidget : BaseFuelWidget() {
    override val layoutId = R.layout.fuel_widget_mileage
    override val hasIcon = true; override val hasLabel = true
    override fun bindValue(views: RemoteViews, context: Context, repo: FuelStateRepository, app: WidgetAppearance) {
        val mileage = (repo.currentOdometerKm - repo.refuelOdometerKm).coerceAtLeast(0f)
        views.setTextViewText(R.id.cwgValue, String.format(Locale.US, "%.1f км", mileage))
        applyTextStyle(views, app, R.id.cwgLabel, R.id.cwgValue)
        applyIconBitmap(views, context, app, "mileage", app.mileageIconIndex)
    }
}

class FuelRangeWidget : BaseFuelWidget() {
    override val layoutId = R.layout.fuel_widget_range
    override val hasIcon = true; override val hasLabel = true
    override fun bindValue(views: RemoteViews, context: Context, repo: FuelStateRepository, app: WidgetAppearance) {
        val mileage = (repo.currentOdometerKm - repo.refuelOdometerKm).coerceAtLeast(0f)
        val spent = mileage * repo.averageConsumptionL100 / 100f
        val remaining = (repo.tankCapacityLiters - spent - repo.fuelNoDistanceL + repo.extraFuelAddedL)
            .coerceIn(0f, repo.tankCapacityLiters)
        val rangeKm = if (repo.averageConsumptionL100 > 0f) remaining / repo.averageConsumptionL100 * 100f else 0f
        views.setTextViewText(R.id.cwgValue, String.format(Locale.US, "%.1f км", rangeKm))
        applyTextStyle(views, app, R.id.cwgLabel, R.id.cwgValue)
        applyIconBitmap(views, context, app, "range", app.rangeIconIndex)
    }
}

class FuelJamWidget : BaseFuelWidget() {
    override val layoutId = R.layout.fuel_widget_jam
    override val hasIcon = true; override val hasLabel = true
    override fun bindValue(views: RemoteViews, context: Context, repo: FuelStateRepository, app: WidgetAppearance) {
        val active = repo.motionState == EngineStateManager.MOTION_JAM &&
                     repo.engineState != EngineStateManager.ENGINE_OFF
        views.setTextViewText(R.id.cwgValue, String.format(Locale.US, "%.2f л", repo.fuelJamL))
        val color = if (active) safeColor(app.cwgTextColor, Color.WHITE)
                    else dim(safeColor(app.cwgTextColor, Color.WHITE), 0.5f)
        views.setTextColor(R.id.cwgLabel, color); views.setTextColor(R.id.cwgValue, color)
        applyIconBitmap(views, context, app, "jam", app.jamIconIndex)
        val i = Intent(context, OverlayService::class.java).apply { action = OverlayService.ACTION_SET_JAM }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        views.setOnClickPendingIntent(R.id.cwgRoot, PendingIntent.getForegroundService(context, 401, i, flags))
    }
}

class FuelWarmupWidget : BaseFuelWidget() {
    override val layoutId = R.layout.fuel_widget_warmup
    override val hasIcon = true; override val hasLabel = true
    override fun bindValue(views: RemoteViews, context: Context, repo: FuelStateRepository, app: WidgetAppearance) {
        val active = repo.motionState == EngineStateManager.MOTION_PARKED &&
                     repo.warmupPhase == EngineStateManager.WARMUP_FAST_IDLE
        views.setTextViewText(R.id.cwgValue, String.format(Locale.US, "%.2f л", repo.fuelWarmupL))
        val color = if (active) safeColor(app.cwgTextColor, Color.WHITE)
                    else dim(safeColor(app.cwgTextColor, Color.WHITE), 0.5f)
        views.setTextColor(R.id.cwgLabel, color); views.setTextColor(R.id.cwgValue, color)
        applyIconBitmap(views, context, app, "warmup", app.warmupIconIndex)
        val i = Intent(context, OverlayService::class.java).apply { action = OverlayService.ACTION_SET_WARMUP }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        views.setOnClickPendingIntent(R.id.cwgRoot, PendingIntent.getForegroundService(context, 402, i, flags))
    }
}

class FuelParkedWidget : BaseFuelWidget() {
    override val layoutId = R.layout.fuel_widget_parked
    override val hasIcon = true; override val hasLabel = true
    override fun bindValue(views: RemoteViews, context: Context, repo: FuelStateRepository, app: WidgetAppearance) {
        val active = repo.motionState == EngineStateManager.MOTION_PARKED &&
                     repo.engineState == EngineStateManager.ENGINE_ON &&
                     repo.warmupPhase != EngineStateManager.WARMUP_FAST_IDLE
        views.setTextViewText(R.id.cwgValue, String.format(Locale.US, "%.2f л", repo.fuelParkedIdleL))
        val color = if (active) safeColor(app.cwgTextColor, Color.WHITE)
                    else dim(safeColor(app.cwgTextColor, Color.WHITE), 0.5f)
        views.setTextColor(R.id.cwgLabel, color); views.setTextColor(R.id.cwgValue, color)
        try {
            val density = context.resources.displayMetrics.density
            val sizePx = (22 * density).toInt()
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color }
            val pad = sizePx * 0.25f
            canvas.drawRect(pad, pad, sizePx - pad, sizePx - pad, paint)
            views.setImageViewBitmap(R.id.cwgIcon, bmp)
        } catch (_: Exception) { }
        val i = Intent(context, OverlayService::class.java).apply { action = OverlayService.ACTION_SET_PARKED }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        views.setOnClickPendingIntent(R.id.cwgRoot, PendingIntent.getForegroundService(context, 403, i, flags))
    }
}

class FuelOffWidget : BaseFuelWidget() {
    override val layoutId = R.layout.fuel_widget_off
    override val hasIcon = true
    override val hasLabel = true
    override fun bindValue(views: RemoteViews, context: Context, repo: FuelStateRepository, app: WidgetAppearance) {
        val active = repo.engineState == EngineStateManager.ENGINE_OFF
        views.setTextViewText(R.id.cwgValue, if (active) "двиг." else "работает")

        val color = if (active) safeColor(app.cwgTextColor, Color.WHITE)
                    else dim(safeColor(app.cwgTextColor, Color.WHITE), 0.5f)
        views.setTextColor(R.id.cwgLabel, color)
        views.setTextColor(R.id.cwgValue, color)

        try {
            val density = context.resources.displayMetrics.density
            val sizePx = (22 * density).toInt()
            val pathData = "M13,3 L11,3 L11,13 L13,13 Z M17.83,5.17 L16.41,6.59 C17.99,7.86 19,9.81 19,12 C19,15.87 15.87,19 12,19 C8.13,19 5,15.87 5,12 C5,9.81 6.01,7.86 7.58,6.59 L6.17,5.17 C4.23,6.82 3,9.26 3,12 C3,16.97 7.03,21 12,21 C16.97,21 21,16.97 21,12 C21,9.26 19.77,6.82 17.83,5.17 Z"
            val path = androidx.core.graphics.PathParser.createPathFromPathData(pathData)
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color }
            canvas.save(); canvas.scale(sizePx / 24f, sizePx / 24f); canvas.drawPath(path, paint); canvas.restore()
            views.setImageViewBitmap(R.id.cwgIcon, bmp)
        } catch (_: Exception) { }

        val i = Intent(context, OverlayService::class.java).apply { action = OverlayService.ACTION_SET_OFF }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        views.setOnClickPendingIntent(R.id.cwgRoot,
            PendingIntent.getForegroundService(context, 404, i, flags))
    }
}

class FuelIndicatorWidget : BaseFuelWidget() {
    override val layoutId = R.layout.fuel_widget_indicator
    override fun bindValue(views: RemoteViews, context: Context, repo: FuelStateRepository, app: WidgetAppearance) {
        val mileage = (repo.currentOdometerKm - repo.refuelOdometerKm).coerceAtLeast(0f)
        val spent = mileage * repo.averageConsumptionL100 / 100f
        val remaining = (repo.tankCapacityLiters - spent - repo.fuelNoDistanceL + repo.extraFuelAddedL)
            .coerceIn(0f, repo.tankCapacityLiters)
        val fillRatio = if (repo.tankCapacityLiters > 0f) (remaining / repo.tankCapacityLiters).coerceIn(0f, 1f) else 0f
        val fillColor = when {
            fillRatio > 0.50f -> safeColor(app.cwgIndicatorColor, Color.WHITE)
            fillRatio > 0.20f -> Color.parseColor("#FF9800")
            else -> Color.parseColor("#F44336")
        }
        val bgColor = safeColor(app.cwgIndicatorBgColor, 0x33FFFFFF)
        val density = context.resources.displayMetrics.density
        val widthPx = (app.cwgIndicatorWidthDp * density).toInt().coerceAtLeast(20)
        val heightPx = (app.cwgIndicatorHeightDp * density).toInt().coerceAtLeast(4)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = bgColor; canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), paint)
        paint.color = fillColor
        val fillWidth = widthPx * fillRatio
        if (fillWidth > 0f) canvas.drawRect(0f, 0f, fillWidth, heightPx.toFloat(), paint)
        views.setImageViewBitmap(R.id.cwgIndicatorImage, bitmap)
    }
}

class RefuelFullWidget : BaseFuelWidget() {
    override val layoutId = R.layout.fuel_widget_refuel_full
    override val hasLabel = false
    override fun bindValue(views: RemoteViews, context: Context, repo: FuelStateRepository, app: WidgetAppearance) {
        applyTextStyle(views, app, R.id.cwgLabel)
        try { views.setViewVisibility(R.id.cwgLabel, View.VISIBLE) } catch (_: Exception) { }
        val i = Intent(context, OverlayService::class.java).apply { action = OverlayService.ACTION_REFUEL_FULL }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        views.setOnClickPendingIntent(R.id.cwgRoot, PendingIntent.getForegroundService(context, 101, i, flags))
    }
}

class RefuelPlusWidget : BaseFuelWidget() {
    override val layoutId = R.layout.fuel_widget_refuel_plus
    override val hasLabel = false
    override fun bindValue(views: RemoteViews, context: Context, repo: FuelStateRepository, app: WidgetAppearance) {
        applyTextStyle(views, app, R.id.cwgLabel)
        try { views.setViewVisibility(R.id.cwgLabel, View.VISIBLE) } catch (_: Exception) { }
        val i = Intent(context, OverlayService::class.java).apply { action = OverlayService.ACTION_REFUEL_PLUS }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        views.setOnClickPendingIntent(R.id.cwgRoot, PendingIntent.getForegroundService(context, 102, i, flags))
    }
}

class FoWidget : BaseFuelWidget() {
    override val layoutId = R.layout.fuel_widget_fo
    override val hasLabel = false; override val hasIcon = false
    override fun bindValue(views: RemoteViews, context: Context, repo: FuelStateRepository, app: WidgetAppearance) {
        applyTextStyle(views, app, R.id.cwgLabel)
        try {
            val color = safeColor(app.cwgTextColor, Color.WHITE)
            val pathData = "M12,2 L2,20 L22,20 Z M12,6 L19,18 L5,18 Z"
            val density = context.resources.displayMetrics.density
            val sizePx = (22 * density).toInt()
            val path = androidx.core.graphics.PathParser.createPathFromPathData(pathData)
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color }
            canvas.save(); canvas.scale(sizePx / 24f, sizePx / 24f); canvas.drawPath(path, paint); canvas.restore()
            views.setImageViewBitmap(R.id.cwgIcon, bmp)
            views.setViewVisibility(R.id.cwgLabel, View.VISIBLE)
            views.setViewVisibility(R.id.cwgIcon, View.VISIBLE)
        } catch (_: Exception) { }
        val i = Intent(context, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(context, 103, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.cwgRoot, pi)
    }
}

private fun dim(color: Int, factor: Float): Int {
    val a = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
    return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
}

internal fun BaseFuelWidget.applyIconBitmap(
    views: RemoteViews, context: Context, app: WidgetAppearance,
    category: String, index: Int
) {
    try {
        val def = VectorIconLibrary.get(category, index)
        val density = context.resources.displayMetrics.density
        val sizePx = (22 * density).toInt()
        val color = safeColor(app.cwgTextColor, Color.WHITE)
        val path = androidx.core.graphics.PathParser.createPathFromPathData(def.pathData)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color }
        canvas.save(); canvas.scale(sizePx / 24f, sizePx / 24f); canvas.drawPath(path, paint); canvas.restore()
        views.setImageViewBitmap(R.id.cwgIcon, bmp)
    } catch (_: Exception) { }
}