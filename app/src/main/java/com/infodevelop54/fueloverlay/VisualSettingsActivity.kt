package com.infodevelop54.fueloverlay

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class VisualSettingsActivity : AppCompatActivity() {

    private var appearance = WidgetAppearance()
    private lateinit var previewRoot: LinearLayout
    private lateinit var fuelRepo: FuelStateRepository

    private val palette = listOf(
        "#FFFFFF", "#000000", "#CC000000", "#EE1A1A1A", "#EEFFFFFF",
        "#00E5FF", "#39FF14", "#FF1744", "#FFD600",
        "#2962FF", "#AA00FF", "#FF6D00", "#FFDB4D"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.btn_visual_settings)
        fuelRepo = FuelStateRepository(this)
        appearance = AppearanceRepository.load(this)

        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#1A1A1A")) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        scroll.addView(content)

        content.addView(sectionTitle("Предпросмотр"))
        val previewWrap = FrameLayout(this).apply { setPadding(0, dp(16), 0, dp(16)) }
        previewRoot = LayoutInflater.from(this).inflate(R.layout.overlay_layout, previewWrap, false) as LinearLayout
        previewWrap.addView(previewRoot); content.addView(previewWrap)

        content.addView(sectionTitle("Пресеты"))
        content.addView(makePresetRow())
        content.addView(sectionTitle("Цвет фона"))
        content.addView(makeColorRow { update(appearance.copy(backgroundColor = it)) })
        content.addView(sectionTitle("Цвет шкалы"))
        content.addView(makeColorRow { update(appearance.copy(scaleColor = it)) })
        content.addView(sectionTitle("Цвет делений"))
        content.addView(makeColorRow { update(appearance.copy(dividerColor = it)) })
        content.addView(sectionTitle("Цвет текста и иконок"))
        content.addView(makeColorRow { update(appearance.copy(textColor = it)) })
        content.addView(sectionTitle("Размер текста, sp"))
        content.addView(makeSlider(appearance.textSizeSp, 8f, 32f, 1f) { update(appearance.copy(textSizeSp = it)) })
        content.addView(sectionTitle("Ширина шкалы, dp"))
        content.addView(makeSlider(appearance.scaleWidthDp.toFloat(), 100f, 500f, 10f) { update(appearance.copy(scaleWidthDp = it.toInt())) })
        content.addView(sectionTitle("Толщина шкалы, dp"))
        content.addView(makeSlider(appearance.scaleHeightDp.toFloat(), 2f, 30f, 1f) { update(appearance.copy(scaleHeightDp = it.toInt())) })
        content.addView(sectionTitle("Толщина делений, dp"))
        content.addView(makeSlider(appearance.dividerThicknessDp.toFloat(), 1f, 10f, 1f) { update(appearance.copy(dividerThicknessDp = it.toInt())) })
        content.addView(sectionTitle("Радиус углов, dp"))
        content.addView(makeSlider(appearance.cornerRadiusDp.toFloat(), 0f, 40f, 1f) { update(appearance.copy(cornerRadiusDp = it.toInt())) })
        content.addView(sectionTitle("Отступы, dp"))
        content.addView(makeSlider(appearance.paddingDp.toFloat(), 0f, 40f, 1f) { update(appearance.copy(paddingDp = it.toInt())) })

        content.addView(sectionTitle("Настройки CWG"))
        content.addView(TextView(this).apply {
            text = "Размер шрифта CWG, sp"; textSize = 13f; setTextColor(Color.WHITE)
            setPadding(0, dp(8), 0, dp(4))
        })
        content.addView(makeSlider(appearance.cwgTextSizeSp, 8f, 32f, 1f) { update(appearance.copy(cwgTextSizeSp = it)) })
        content.addView(TextView(this).apply {
            text = "Цвет текста и иконок CWG"; textSize = 13f; setTextColor(Color.WHITE)
            setPadding(0, dp(12), 0, dp(4))
        })
        content.addView(makeColorRow { update(appearance.copy(cwgTextColor = it)) })
        content.addView(TextView(this).apply {
            text = "Цвет фона CWG"; textSize = 13f; setTextColor(Color.WHITE)
            setPadding(0, dp(12), 0, dp(4))
        })
        content.addView(makeColorRow { update(appearance.copy(cwgBackgroundColor = it)) })
        content.addView(CheckBox(this).apply {
            text = "Показывать фон"; isChecked = appearance.cwgShowBackground
            setTextColor(Color.WHITE); setPadding(0, dp(12), 0, dp(4))
            setOnCheckedChangeListener { _, b -> update(appearance.copy(cwgShowBackground = b)) }
        })
        content.addView(CheckBox(this).apply {
            text = "Показывать подписи"; isChecked = appearance.cwgShowLabels
            setTextColor(Color.WHITE); setPadding(0, dp(4), 0, dp(4))
            setOnCheckedChangeListener { _, b -> update(appearance.copy(cwgShowLabels = b)) }
        })
        content.addView(CheckBox(this).apply {
            text = "Показывать значки"; isChecked = appearance.cwgShowIcons
            setTextColor(Color.WHITE); setPadding(0, dp(4), 0, dp(12))
            setOnCheckedChangeListener { _, b -> update(appearance.copy(cwgShowIcons = b)) }
        })
        content.addView(TextView(this).apply {
            text = "Ширина полосы-индикатора, dp"; textSize = 13f; setTextColor(Color.WHITE)
            setPadding(0, dp(12), 0, dp(4))
        })
        content.addView(makeSlider(appearance.cwgIndicatorWidthDp.toFloat(), 60f, 400f, 5f) { update(appearance.copy(cwgIndicatorWidthDp = it.toInt())) })
        content.addView(TextView(this).apply {
            text = "Толщина полосы-индикатора, dp"; textSize = 13f; setTextColor(Color.WHITE)
            setPadding(0, dp(12), 0, dp(4))
        })
        content.addView(makeSlider(appearance.cwgIndicatorHeightDp.toFloat(), 2f, 30f, 1f) { update(appearance.copy(cwgIndicatorHeightDp = it.toInt())) })
        content.addView(TextView(this).apply {
            text = "Цвет полосы-индикатора"; textSize = 13f; setTextColor(Color.WHITE)
            setPadding(0, dp(12), 0, dp(4))
        })
        content.addView(makeColorRow { update(appearance.copy(cwgIndicatorColor = it)) })
        content.addView(TextView(this).apply {
            text = "Цвет фона полосы-индикатора"; textSize = 13f; setTextColor(Color.WHITE)
            setPadding(0, dp(12), 0, dp(4))
        })
        content.addView(makeColorRow { update(appearance.copy(cwgIndicatorBgColor = it)) })
        content.addView(TextView(this).apply {
            text = "Настройки применятся к CWG-виджетам при следующем тике сервиса (раз в секунду)."
            textSize = 11f; setTextColor(Color.parseColor("#AAFFFFFF"))
            setPadding(0, dp(4), 0, dp(8))
        })

        content.addView(Button(this).apply {
            text = "Сбросить к стандартному виду"
            setOnClickListener { update(WidgetAppearance()); recreate() }
        })

        setContentView(scroll); refreshPreview()
    }

    private fun update(a: WidgetAppearance) {
        appearance = a; AppearanceRepository.save(this, a); refreshPreview()
    }

    private fun refreshPreview() {
        AppearanceApplier.applyStatic(previewRoot, appearance, resources.displayMetrics.density)
        AppearanceApplier.applyScale(previewRoot, appearance,
            remainingLiters = fuelRepo.tankCapacityLiters,
            tankLiters = fuelRepo.tankCapacityLiters,
            density = resources.displayMetrics.density)
        CwgRefresher.refreshAll(this)
    }

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text; textSize = 14f; setTextColor(Color.WHITE)
        setPadding(0, dp(12), 0, dp(4))
    }

    private fun makeColorRow(onPick: (String) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.START }
        palette.forEach { hex ->
            row.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(6) }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(hex)); cornerRadius = dp(4).toFloat()
                    setStroke(dp(2), Color.parseColor("#888888"))
                }
                setOnClickListener { onPick(hex) }
            })
        }
        return row
    }

    private fun makeSlider(initial: Float, min: Float, max: Float, step: Float, onChanged: (Float) -> Unit): SeekBar {
        val steps = ((max - min) / step).toInt().coerceAtLeast(1)
        return SeekBar(this).apply {
            this.max = steps
            progress = ((initial - min) / step).toInt().coerceIn(0, steps)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) onChanged(min + p * step)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
    }

    private fun makePresetRow(): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        WidgetAppearance.PRESETS.forEach { (name, preset) ->
            row.addView(Button(this).apply {
                text = name; textSize = 12f
                setOnClickListener { update(preset); recreate() }
            })
        }
        return row
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}