package com.infodevelop54.fueloverlay

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews

internal fun safeColor(hex: String, fallback: Int): Int =
    try { Color.parseColor(hex) } catch (_: Exception) { fallback }

internal fun BaseFuelWidget.applyTextStyle(views: RemoteViews, app: WidgetAppearance, vararg ids: Int) {
    val tc = safeColor(app.cwgTextColor, Color.WHITE)
    ids.forEach { id ->
        views.setTextColor(id, tc)
        views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, app.cwgTextSizeSp)
    }
}

internal fun BaseFuelWidget.applyIconTint(views: RemoteViews, app: WidgetAppearance) {
    try {
        views.setInt(R.id.cwgIcon, "setColorFilter", safeColor(app.cwgTextColor, Color.WHITE))
    } catch (_: Exception) { }
}

abstract class BaseFuelWidget : AppWidgetProvider() {
    abstract val layoutId: Int
    abstract fun bindValue(views: RemoteViews, context: Context, repo: FuelStateRepository, app: WidgetAppearance)

    open val hasIcon: Boolean = false
    open val hasLabel: Boolean = false

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) mgr.updateAppWidget(id, buildViews(context))
    }

    fun buildViews(context: Context): RemoteViews {
        val repo = FuelStateRepository(context)
        val app = AppearanceRepository.load(context)
        val views = RemoteViews(context.packageName, layoutId)

        val bgColor = if (app.cwgShowBackground) safeColor(app.cwgBackgroundColor, 0xCC000000.toInt())
                      else Color.TRANSPARENT
        views.setInt(R.id.cwgRoot, "setBackgroundColor", bgColor)

        if (hasLabel && !app.cwgShowLabels) views.setViewVisibility(R.id.cwgLabel, View.GONE)
        else if (hasLabel) views.setViewVisibility(R.id.cwgLabel, View.VISIBLE)
        if (hasIcon && !app.cwgShowIcons) {
            try { views.setViewVisibility(R.id.cwgIcon, View.GONE) } catch (_: Exception) {}
        } else if (hasIcon) {
            try { views.setViewVisibility(R.id.cwgIcon, View.VISIBLE) } catch (_: Exception) {}
        }

        bindValue(views, context, repo, app)
        return views
    }
}

object CwgRefresher {
    fun refreshAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val providers = listOf(
            FuelSpentWidget(),
            FuelRemainingWidget(),
            FuelMileageWidget(),
            FuelRangeWidget(),
            FuelJamWidget(),
            FuelWarmupWidget(),
            FuelParkedWidget(),
            FuelOffWidget(),
            FuelIndicatorWidget(),
            RefuelFullWidget(),
            RefuelPlusWidget(),
            FoWidget()
        )
        for (p in providers) {
            val ids = mgr.getAppWidgetIds(ComponentName(context, p.javaClass))
            if (ids.isEmpty()) continue
            for (id in ids) mgr.updateAppWidget(id, p.buildViews(context))
        }
    }
}