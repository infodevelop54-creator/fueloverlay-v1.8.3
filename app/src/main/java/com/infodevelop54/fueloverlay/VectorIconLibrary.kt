package com.infodevelop54.fueloverlay

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.core.graphics.PathParser

object VectorIconLibrary {
    data class IconDef(val id: String, val label: String, val pathData: String)

    val SPENT = listOf(
        IconDef("flame", "Пламя", "M12,2 C10,7 6,10 6,14 C6,17.31 8.69,20 12,20 C15.31,20 18,17.31 18,14 C18,10 14,7 12,2 Z"),
        IconDef("bolt", "Молния", "M11,2 L4,13 L10,13 L9,22 L20,9 L14,9 Z"),
        IconDef("arrow_down", "Стрелка вниз", "M11,4 L13,4 L13,15 L18,15 L12,22 L6,15 L11,15 Z")
    )
    val REMAINING = listOf(
        IconDef("droplet", "Капля", "M12,2 C12,2 5,10 5,15 C5,18.87 8.13,22 12,22 C15.87,22 19,18.87 19,15 C19,10 12,2 12,2 Z"),
        IconDef("circle_full", "Полный круг", "M12,2 A10,10 0 1,0 12,22 A10,10 0 1,0 12,2 Z"),
        IconDef("circle_half", "Полукруг", "M12,2 A10,10 0 0,1 12,22 L12,2 Z")
    )
    val MILEAGE = listOf(
        IconDef("car", "Автомобиль", "M18.92,6.01 C18.72,5.42 18.16,5 17.5,5 L6.5,5 C5.84,5 5.29,5.42 5.08,6.01 L3,12 L3,20 L5,20 L5,18 L19,18 L19,20 L21,20 L21,12 L18.92,6.01 Z M6.5,16 C5.67,16 5,15.33 5,14.5 C5,13.67 5.67,13 6.5,13 C7.33,13 8,13.67 8,14.5 C8,15.33 7.33,16 6.5,16 Z M17.5,16 C16.67,16 16,15.33 16,14.5 C16,13.67 16.67,13 17.5,13 C18.33,13 19,13.67 19,14.5 C19,15.33 18.33,16 17.5,16 Z"),
        IconDef("road", "Дорога", "M4,22 L10,2 L14,2 L20,22 L17,22 L12,6 L7,22 Z"),
        IconDef("pin", "Метка", "M12,2 C8.13,2 5,5.13 5,9 C5,14.25 12,22 12,22 C12,22 19,14.25 19,9 C19,5.13 15.87,2 12,2 Z M12,11.5 C10.62,11.5 9.5,10.38 9.5,9 C9.5,7.62 10.62,6.5 12,6.5 C13.38,6.5 14.5,7.62 14.5,9 C14.5,10.38 13.38,11.5 12,11.5 Z")
    )
    val RANGE = listOf(
        IconDef("flag", "Флаг", "M14.4,6 L14,4 L5,4 L5,21 L7,21 L7,14 L13.6,14 L14,16 L20,16 L20,6 Z"),
        IconDef("compass", "Компас", "M12,2 C6.48,2 2,6.48 2,12 C2,17.52 6.48,22 12,22 C17.52,22 22,17.52 22,12 C22,6.48 17.52,2 12,2 Z M14.8,14.8 L6,18 L9.2,9.2 L18,6 L14.8,14.8 Z"),
        IconDef("route", "Маршрут", "M4,20 L4,10 C4,7 6,5 9,5 L16,5 L16,2 L22,7 L16,12 L16,9 L9,9 C8,9 7,10 7,11 L7,20 Z")
    )
    val JAM = listOf(
        IconDef("queued", "Очередь", "M3,7 L21,7 L21,10 L3,10 Z M3,12 L21,12 L21,15 L3,15 Z M3,17 L21,17 L21,20 L3,20 Z"),
        IconDef("pause", "Пауза", "M7,5 L10,5 L10,19 L7,19 Z M14,5 L17,5 L17,19 L14,19 Z"),
        IconDef("traffic_light", "Светофор", "M9,3 L15,3 L15,21 L9,21 Z M6,7 L9,7 L9,10 L6,10 Z M15,7 L18,7 L18,10 L15,10 Z")
    )
    val WARMUP = listOf(
        IconDef("thermometer", "Термометр", "M11,3 L13,3 L13,14 L15,17 L15,20 L9,20 L9,17 L11,14 Z"),
        IconDef("engine", "Двигатель", "M4,9 L8,9 L8,6 L16,6 L16,9 L20,9 L20,16 L16,16 L16,19 L8,19 L8,16 L4,16 Z"),
        IconDef("flame", "Пламя", "M12,2 C10,7 6,10 6,14 C6,17.31 8.69,20 12,20 C15.31,20 18,17.31 18,14 C18,10 14,7 12,2 Z")
    )

    fun get(category: String, index: Int): IconDef = when (category) {
        "spent"     -> SPENT.getOrElse(index) { SPENT[0] }
        "remaining" -> REMAINING.getOrElse(index) { REMAINING[0] }
        "mileage"   -> MILEAGE.getOrElse(index) { MILEAGE[0] }
        "range"     -> RANGE.getOrElse(index) { RANGE[0] }
        "jam"       -> JAM.getOrElse(index) { JAM[0] }
        "warmup"    -> WARMUP.getOrElse(index) { WARMUP[0] }
        else        -> SPENT[0]
    }

    fun createDrawable(pathData: String, color: Int): Drawable {
        val path = PathParser.createPathFromPathData(pathData)
        return VectorIconDrawable(path, VIEWPORT, color)
    }
    private const val VIEWPORT = 24f
}

class VectorIconDrawable(
    private val path: Path,
    private val viewportSize: Float,
    initialColor: Int
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = initialColor
    }
    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val scale = minOf(b.width(), b.height()) / viewportSize
        canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        canvas.scale(scale, scale)
        canvas.drawPath(path, paint)
        canvas.restore()
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}