package com.infodevelop54.fueloverlay

import org.json.JSONObject

data class WidgetAppearance(
    val backgroundColor: String = "#CC000000",
    val textColor: String = "#FFFFFF",
    val scaleColor: String = "#FFFFFF",
    val dividerColor: String = "#000000",
    val textSizeSp: Float = 14f,
    val scaleWidthDp: Int = 260,
    val scaleHeightDp: Int = 10,
    val cornerRadiusDp: Int = 8,
    val paddingDp: Int = 12,
    val dividerThicknessDp: Int = 2,
    val showSpent: Boolean = true,
    val showRemaining: Boolean = true,
    val spentIconIndex: Int = 0,
    val remainingIconIndex: Int = 0,
    val mileageIconIndex: Int = 0,
    val rangeIconIndex: Int = 0,
    val jamIconIndex: Int = 0,
    val warmupIconIndex: Int = 0,
    val cwgTextColor: String = "#FFFFFF",
    val cwgTextSizeSp: Float = 14f,
    val cwgBackgroundColor: String = "#CC000000",
    val cwgShowBackground: Boolean = true,
    val cwgShowLabels: Boolean = true,
    val cwgShowIcons: Boolean = true,
    val cwgIndicatorColor: String = "#FFFFFF",
    val cwgIndicatorBgColor: String = "#33FFFFFF",
    val cwgIndicatorWidthDp: Int = 180,
    val cwgIndicatorHeightDp: Int = 12
) {
    fun toJson(): String = JSONObject().apply {
        put("backgroundColor", backgroundColor)
        put("textColor", textColor)
        put("scaleColor", scaleColor)
        put("dividerColor", dividerColor)
        put("textSizeSp", textSizeSp.toDouble())
        put("scaleWidthDp", scaleWidthDp); put("scaleHeightDp", scaleHeightDp)
        put("cornerRadiusDp", cornerRadiusDp); put("paddingDp", paddingDp)
        put("dividerThicknessDp", dividerThicknessDp)
        put("showSpent", showSpent); put("showRemaining", showRemaining)
        put("spentIconIndex", spentIconIndex); put("remainingIconIndex", remainingIconIndex)
        put("mileageIconIndex", mileageIconIndex); put("rangeIconIndex", rangeIconIndex)
        put("jamIconIndex", jamIconIndex); put("warmupIconIndex", warmupIconIndex)
        put("cwgTextColor", cwgTextColor); put("cwgTextSizeSp", cwgTextSizeSp.toDouble())
        put("cwgBackgroundColor", cwgBackgroundColor); put("cwgShowBackground", cwgShowBackground)
        put("cwgShowLabels", cwgShowLabels); put("cwgShowIcons", cwgShowIcons)
        put("cwgIndicatorColor", cwgIndicatorColor); put("cwgIndicatorBgColor", cwgIndicatorBgColor)
        put("cwgIndicatorWidthDp", cwgIndicatorWidthDp); put("cwgIndicatorHeightDp", cwgIndicatorHeightDp)
    }.toString()

    companion object {
        fun fromJson(json: String): WidgetAppearance = try {
            val o = JSONObject(json)
            WidgetAppearance(
                backgroundColor = o.optString("backgroundColor", "#CC000000"),
                textColor = o.optString("textColor", "#FFFFFF"),
                scaleColor = o.optString("scaleColor", "#FFFFFF"),
                dividerColor = o.optString("dividerColor", "#000000"),
                textSizeSp = o.optDouble("textSizeSp", 14.0).toFloat(),
                scaleWidthDp = o.optInt("scaleWidthDp", 260),
                scaleHeightDp = o.optInt("scaleHeightDp", 10),
                cornerRadiusDp = o.optInt("cornerRadiusDp", 8),
                paddingDp = o.optInt("paddingDp", 12),
                dividerThicknessDp = o.optInt("dividerThicknessDp", 2),
                showSpent = o.optBoolean("showSpent", true),
                showRemaining = o.optBoolean("showRemaining", true),
                spentIconIndex = o.optInt("spentIconIndex", 0),
                remainingIconIndex = o.optInt("remainingIconIndex", 0),
                mileageIconIndex = o.optInt("mileageIconIndex", 0),
                rangeIconIndex = o.optInt("rangeIconIndex", 0),
                jamIconIndex = o.optInt("jamIconIndex", 0),
                warmupIconIndex = o.optInt("warmupIconIndex", 0),
                cwgTextColor = o.optString("cwgTextColor", "#FFFFFF"),
                cwgTextSizeSp = o.optDouble("cwgTextSizeSp", 14.0).toFloat(),
                cwgBackgroundColor = o.optString("cwgBackgroundColor", "#CC000000"),
                cwgShowBackground = o.optBoolean("cwgShowBackground", true),
                cwgShowLabels = o.optBoolean("cwgShowLabels", true),
                cwgShowIcons = o.optBoolean("cwgShowIcons", true),
                cwgIndicatorColor = o.optString("cwgIndicatorColor", "#FFFFFF"),
                cwgIndicatorBgColor = o.optString("cwgIndicatorBgColor", "#33FFFFFF"),
                cwgIndicatorWidthDp = o.optInt("cwgIndicatorWidthDp", 180),
                cwgIndicatorHeightDp = o.optInt("cwgIndicatorHeightDp", 12)
            )
        } catch (_: Exception) { WidgetAppearance() }

        val PRESETS: Map<String, WidgetAppearance> = linkedMapOf(
            "Классика" to WidgetAppearance(),
            "Тёмная" to WidgetAppearance(backgroundColor = "#EE1A1A1A", scaleColor = "#00E5FF",
                dividerColor = "#1A1A1A", textColor = "#00E5FF"),
            "Светлая" to WidgetAppearance(backgroundColor = "#EEFFFFFF", scaleColor = "#222222",
                dividerColor = "#FFFFFF", textColor = "#222222"),
            "Неон" to WidgetAppearance(backgroundColor = "#CC000000", scaleColor = "#39FF14",
                dividerColor = "#000000", textColor = "#39FF14"),
            "Яндекс" to WidgetAppearance(backgroundColor = "#EEFFDB4D", scaleColor = "#111111",
                dividerColor = "#FFDB4D", textColor = "#111111", cornerRadiusDp = 20)
        )
    }
}