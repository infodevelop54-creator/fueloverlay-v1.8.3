package com.infodevelop54.fueloverlay

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout

class SettingsActivity : AppCompatActivity() {

    private lateinit var fuelRepo: FuelStateRepository
    private lateinit var tvJamValue: TextView
    private lateinit var tvWarmupValue: TextView
    private lateinit var tvIdleValue: TextView
    private lateinit var tvSeasonValue: TextView

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() { updateCurrentValues(); refreshHandler.postDelayed(this, 1000L) }
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(FuelBackup.export(this).toByteArray()) }
            toast("БД экспортирована")
        } catch (e: Exception) { toast("Ошибка экспорта: ${e.message}") }
    }
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val text = contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: return@registerForActivityResult
            FuelBackup.import(this, text); toast("БД импортирована"); recreate()
        } catch (e: Exception) { toast("Ошибка импорта: ${e.message}") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fuelRepo = FuelStateRepository(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }
        val tabLayout = TabLayout(this)
        tabLayout.addTab(tabLayout.newTab().setText("Информация"))
        tabLayout.addTab(tabLayout.newTab().setText("Настройки"))
        root.addView(tabLayout)

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        scroll.addView(content)
        root.addView(scroll)

        val infoTab = buildInfoTab()
        val settingsTab = buildSettingsTab()
        content.addView(infoTab); content.addView(settingsTab)
        settingsTab.visibility = View.GONE

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val info = tab?.position == 0
                infoTab.visibility = if (info) View.VISIBLE else View.GONE
                settingsTab.visibility = if (info) View.GONE else View.VISIBLE
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        setContentView(root)
        updateCurrentValues()
    }

    override fun onResume() { super.onResume(); updateCurrentValues(); refreshHandler.post(refreshRunnable) }
    override fun onPause() { super.onPause(); refreshHandler.removeCallbacks(refreshRunnable) }

    private fun buildInfoTab(): LinearLayout {
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        c.addView(sectionTitle("Быстрые действия"))

        val showWidgetBtn = Button(this).apply {
            text = if (fuelRepo.widgetVisible) "Скрыть виджет" else "Показать виджет"
            setOnClickListener {
                val svc = OverlayService.instance
                if (svc != null) svc.toggleWidget() else {
                    fuelRepo.widgetVisible = true; fuelRepo.startHidden = false
                    startService(Intent(this@SettingsActivity, OverlayService::class.java))
                }
                text = if (fuelRepo.widgetVisible) "Скрыть виджет" else "Показать виджет"
            }
        }
        c.addView(showWidgetBtn)

        c.addView(Button(this).apply {
            text = getString(R.string.menu_adaptive)
            setOnClickListener { startActivity(Intent(this@SettingsActivity, AdaptiveConsumptionActivity::class.java)) }
        })

        c.addView(Button(this).apply {
            text = getString(R.string.menu_journal)
            setOnClickListener { startActivity(Intent(this@SettingsActivity, RefuelJournalActivity::class.java)) }
        })

        c.addView(sectionTitle("Данные поездки"))
        val etOdo = addLabeledInput(c, "Точный одометр, км", fuelRepo.currentOdometerKm.toString())
        val etTank = addLabeledInput(c, "Объём бака, л", fuelRepo.tankCapacityLiters.toString())
        val etCons = addLabeledInput(c, "Ручной средний расход, л/100 км", fuelRepo.manualAverageConsumptionL100.toString())
        val etRefuelOdo = addLabeledInput(c, "Одометр на последней заправке, км", fuelRepo.refuelOdometerKm.toString())

        c.addView(Button(this).apply {
            text = "Сохранить данные"
            setOnClickListener {
                parseFloat(etOdo)?.let { fuelRepo.syncOdometer(it) }
                parseFloat(etTank)?.let { fuelRepo.tankCapacityLiters = it }
                parseFloat(etCons)?.let {
                    fuelRepo.manualAverageConsumptionL100 = it
                    if (AdaptiveConsumption.loadCycles(this@SettingsActivity).isEmpty()) {
                        fuelRepo.averageConsumptionL100 = it
                    }
                }
                parseFloat(etRefuelOdo)?.let { fuelRepo.refuelOdometerKm = it }
                toast("Сохранено")
            }
        })

        c.addView(sectionTitle("Текущие значения"))
        tvJamValue = infoRow(c, "Пробки")
        tvWarmupValue = infoRow(c, "Прогрев")
        tvIdleValue = infoRow(c, "Остановка")

        c.addView(sectionTitle("Режим прогрева"))
        tvSeasonValue = TextView(this).apply {
            textSize = 15f; setTextColor(Color.WHITE); setPadding(0, dp(4), 0, dp(8))
        }
        c.addView(tvSeasonValue)

        val seasonGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val seasons = listOf(
            -1 to "Авто (по дате)",
            EngineStateManager.SEASON_WINTER to "Зимний (1.8 л/ч · 6 мин)",
            EngineStateManager.SEASON_SPRING to "Весенний (1.8 л/ч · 3 мин)",
            EngineStateManager.SEASON_SUMMER to "Летний (1.8 л/ч · 1 мин)",
            EngineStateManager.SEASON_AUTUMN to "Осенний (1.8 л/ч · 3 мин)"
        )
        val currentSel = fuelRepo.warmupSeasonOverride
        seasons.forEach { (value, label) ->
            val rb = RadioButton(this).apply {
                text = label; setTextColor(Color.WHITE); isChecked = currentSel == value
                setOnClickListener { fuelRepo.warmupSeasonOverride = value; updateCurrentValues() }
            }
            seasonGroup.addView(rb)
        }
        c.addView(seasonGroup)

        return c
    }

    private fun buildSettingsTab(): LinearLayout {
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        c.addView(sectionTitle("Внешний вид"))
        c.addView(Button(this).apply {
            text = getString(R.string.btn_visual_settings)
            setOnClickListener { startActivity(Intent(this@SettingsActivity, VisualSettingsActivity::class.java)) }
        })

        c.addView(sectionTitle("Уведомления"))
        val notifyCbs = mutableListOf<CheckBox>()
        val modes = listOf(
            EngineStateManager.NOTIFY_DIALOG to "Всплывающий диалог поверх виджета",
            EngineStateManager.NOTIFY_PUSH to "Пуш-уведомление",
            EngineStateManager.NOTIFY_WIDGET_BUTTONS to "Кнопки на обратной стороне виджета"
        )
        modes.forEachIndexed { i, (value, label) ->
            val cb = CheckBox(this).apply {
                text = label; setTextColor(Color.WHITE); isChecked = fuelRepo.notifyMode == value
                setPadding(0, dp(4), 0, dp(4))
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        notifyCbs.forEachIndexed { j, other -> if (j != i) other.isChecked = false }
                        fuelRepo.notifyMode = value
                    } else if (notifyCbs.count { it.isChecked } == 0) isChecked = true
                }
            }
            notifyCbs.add(cb); c.addView(cb)
        }
        c.addView(TextView(this).apply {
            text = "Выбрать можно только один способ."
            textSize = 11f; setTextColor(Color.parseColor("#AAFFFFFF"))
            setPadding(0, dp(4), 0, dp(8))
        })

        c.addView(sectionTitle("Топливо без движения"))
        c.addView(TextView(this).apply {
            text = "Скорость расхода в холостом режиме (база для пробки)"
            textSize = 12f; setTextColor(Color.parseColor("#CCFFFFFF"))
            setPadding(0, dp(8), 0, dp(4))
        })
        c.addView(makeSliderWithLabel("Холостой, л/ч", fuelRepo.idleRateLPerHour, 0.4f, 1.5f, 0.05f) {
            fuelRepo.idleRateLPerHour = it
        })

        c.addView(TextView(this).apply {
            text = "Скорость расхода во время прогрева"
            textSize = 12f; setTextColor(Color.parseColor("#CCFFFFFF"))
            setPadding(0, dp(12), 0, dp(4))
        })
        c.addView(makeSliderWithLabel("Прогрев, л/ч", fuelRepo.warmupRateLPerHour, 1.0f, 2.5f, 0.1f) {
            fuelRepo.warmupRateLPerHour = it
        })

        c.addView(CheckBox(this).apply {
            text = "Учитывать пробки"; isChecked = fuelRepo.jamTrackingEnabled
            setTextColor(Color.WHITE); setPadding(0, dp(8), 0, dp(4))
            setOnCheckedChangeListener { _, b -> fuelRepo.jamTrackingEnabled = b }
        })
        c.addView(CheckBox(this).apply {
            text = "Учитывать прогрев"; isChecked = fuelRepo.warmupTrackingEnabled
            setTextColor(Color.WHITE); setPadding(0, dp(4), 0, dp(8))
            setOnCheckedChangeListener { _, b -> fuelRepo.warmupTrackingEnabled = b }
        })

        c.addView(Button(this).apply {
            text = "Обнулить счётчики топлива без движения"
            setOnClickListener {
                fuelRepo.fuelJamL = 0f; fuelRepo.fuelWarmupL = 0f; fuelRepo.fuelParkedIdleL = 0f
                toast("Счётчики обнулены")
            }
        })

        c.addView(sectionTitle("Импорт\\Экспорт"))
        c.addView(TextView(this).apply {
            text = "Путь: ${FuelDatabase.baseDir(this@SettingsActivity).absolutePath}"
            textSize = 12f; setTextColor(Color.WHITE)
        })
        c.addView(Button(this).apply {
            text = getString(R.string.menu_export)
            setOnClickListener { exportLauncher.launch("fuel_overlay_backup.json") }
        })
        c.addView(Button(this).apply {
            text = getString(R.string.menu_import)
            setOnClickListener { importLauncher.launch(arrayOf("application/json", "*/*")) }
        })

        return c
    }

    private fun updateCurrentValues() {
        try {
            tvJamValue.text = String.format(java.util.Locale.US, "%.2f л", fuelRepo.fuelJamL)
            tvWarmupValue.text = String.format(java.util.Locale.US, "%.2f л", fuelRepo.fuelWarmupL)
            tvIdleValue.text = String.format(java.util.Locale.US, "%.2f л", fuelRepo.fuelParkedIdleL)
            val season = if (fuelRepo.warmupSeasonOverride == -1) "Авто → ${autoSeasonLabel()}"
                         else manualSeasonLabel(fuelRepo.warmupSeasonOverride)
            tvSeasonValue.text = "Режим прогрева: $season"
        } catch (_: Exception) {}
    }

    private fun autoSeasonLabel(): String {
        val month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)
        return when (month) {
            11, 0, 1 -> "Зимний"; 2, 3, 4 -> "Весенний"
            5, 6, 7 -> "Летний"; 8, 9, 10 -> "Осенний"
            else -> "Летний"
        }
    }

    private fun manualSeasonLabel(v: Int): String = when (v) {
        EngineStateManager.SEASON_WINTER -> "Зимний"
        EngineStateManager.SEASON_SPRING -> "Весенний"
        EngineStateManager.SEASON_SUMMER -> "Летний"
        EngineStateManager.SEASON_AUTUMN -> "Осенний"
        else -> "Авто"
    }

    private fun infoRow(parent: LinearLayout, label: String): TextView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(4), 0, dp(4))
        }
        row.addView(TextView(this).apply {
            text = "$label:"; textSize = 14f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val value = TextView(this).apply {
            text = "0.00 л"; textSize = 14f; setTextColor(Color.WHITE)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
        }
        row.addView(value); parent.addView(row); return value
    }

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text; textSize = 14f; setTextColor(Color.WHITE)
        setPadding(0, dp(16), 0, dp(6))
    }

    private fun addLabeledInput(parent: LinearLayout, label: String, initial: String): EditText {
        parent.addView(TextView(this).apply {
            text = label; textSize = 13f; setTextColor(Color.WHITE)
            setPadding(0, dp(8), 0, dp(4))
        })
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(initial); setTextColor(Color.WHITE)
        }
        parent.addView(et); return et
    }

    private fun makeSliderWithLabel(
        label: String, initial: Float, min: Float, max: Float, step: Float,
        onChanged: (Float) -> Unit
    ): LinearLayout {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = label; textSize = 14f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val valueTv = TextView(this).apply {
            text = String.format(java.util.Locale.US, "%.2f", initial)
            textSize = 15f; setTextColor(Color.WHITE)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
            minWidth = dp(64)
        }
        header.addView(valueTv); col.addView(header)

        val steps = ((max - min) / step).toInt().coerceAtLeast(1)
        val sb = android.widget.SeekBar(this).apply {
            this.max = steps
            progress = ((initial - min) / step).toInt().coerceIn(0, steps)
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                    val v = min + p * step
                    valueTv.text = String.format(java.util.Locale.US, "%.2f", v)
                    if (fromUser) onChanged(v)
                }
                override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(s: android.widget.SeekBar?) {}
            })
        }
        col.addView(sb); return col
    }

    private fun parseFloat(et: EditText): Float? =
        et.text.toString().replace(',', '.').trim().toFloatOrNull()

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}