package com.infodevelop54.fueloverlay

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.util.Locale

class OverlayService : Service(), EngineStateManager.UiCallback {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var fuelRepo: FuelStateRepository
    private lateinit var engineManager: EngineStateManager

    private lateinit var frontSide: View
    private lateinit var backSide: View
    private lateinit var normalPanel: LinearLayout
    private lateinit var promptPanel: LinearLayout
    private lateinit var textSpent: TextView
    private lateinit var textRemaining: TextView
    private lateinit var textMileage: TextView
    private lateinit var textRange: TextView
    private lateinit var modeJam: TextView
    private lateinit var modeWarmup: TextView
    private lateinit var modeParked: TextView
    private lateinit var modeOff: TextView

    private var gpsTracker: GpsTracker? = null
    private var iconPickerOpen = false
    private var isAttached = false
    private var activeDialog: AlertDialog? = null

    private val appStartTs = System.currentTimeMillis()

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() { tickEngineAndUi(); FuelStateRepository.maybeFlush(); handler.postDelayed(this, 1000L) }
    }
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> applyAppearance() }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()

        fuelRepo = FuelStateRepository(this)
        fuelRepo.promptActive = false
        fuelRepo.promptKind = EngineStateManager.PROMPT_NONE
        engineManager = EngineStateManager(this, fuelRepo, this)
        engineManager.setAppStartTs(appStartTs)

        if (fuelRepo.lastMovementTimestamp == 0L) fuelRepo.lastMovementTimestamp = System.currentTimeMillis()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = fuelRepo.overlayX; y = fuelRepo.overlayY }

        bindViews(); setupButtons(); setupPromptButtons(); setupIconLongPresses(); setupTouchListener()
        applyAppearance()
        AppearanceRepository.getPrefs(this).registerOnSharedPreferenceChangeListener(prefsListener)

        if (fuelRepo.gpsTrackingEnabled) startGps()

        val showOnStart = !fuelRepo.startHidden
        fuelRepo.widgetVisible = showOnStart
        if (showOnStart) attachOverlay()

        startForegroundCompat()
        handler.post(updateRunnable)
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            val type = if (hasLocationPermission()) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            try {
                startForeground(1, notification, type)
            } catch (_: Exception) {
                try {
                    startForeground(1, notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } catch (_: Exception) { }
            }
        } else {
            startForeground(1, notification)
        }
    }

    private fun hasLocationPermission(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REFUEL_FULL -> { if (!fuelRepo.widgetVisible) showWidget(); showRefuelFullDialog() }
            ACTION_REFUEL_PLUS -> { if (!fuelRepo.widgetVisible) showWidget(); showRefuelPlusDialog() }
            ACTION_SHOW_WIDGET -> showWidget()
            ACTION_OPEN_SETTINGS -> startActivity(Intent(this, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            ACTION_JAM_CONFIRMED -> engineManager.onJamConfirmed()
            ACTION_PARKED_ENGINE_ON -> engineManager.onParkedEngineOn()
            ACTION_PARKED_ENGINE_OFF -> engineManager.onParkedEngineOff()
            ACTION_WARMUP_STARTED -> engineManager.onWarmupStarted()
            ACTION_WARMUP_DONE -> engineManager.onWarmupEndConfirmed()
            ACTION_WARMUP_EXTEND -> engineManager.onWarmupExtended()
            ACTION_STILL_IN_JAM -> engineManager.onStillInJam()
            ACTION_JAM_OVER -> engineManager.onJamOver()
            ACTION_ENGINE_OFF -> engineManager.onEngineOff()
            ACTION_SET_JAM -> engineManager.setJamMode()
            ACTION_SET_WARMUP -> engineManager.setWarmupMode()
            ACTION_SET_PARKED -> engineManager.setParkedMode()
            ACTION_SET_OFF -> engineManager.setEngineOff()
            else -> {
                if (fuelRepo.widgetVisible && !isAttached) attachOverlay()
                else if (!fuelRepo.widgetVisible && isAttached) detachOverlay()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        gpsTracker?.stop(); gpsTracker = null
        dismissActiveDialog()
        FuelStateRepository.flush()
        AppearanceRepository.getPrefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        if (isAttached && ::floatingView.isInitialized && floatingView.isAttachedToWindow) {
            try { windowManager.removeView(floatingView) } catch (_: Exception) { }
        }
        isAttached = false
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun showStatePrompt() {
        dismissActiveDialog()
        val dlg = AlertDialog.Builder(this)
            .setTitle("Машина стоит больше 30 секунд")
            .setItems(arrayOf("Пробка", "Стою, двиг. вкл", "Прогреваюсь", "Заглушил двигатель")) { _, which ->
                when (which) {
                    0 -> engineManager.onJamConfirmed()
                    1 -> engineManager.onParkedEngineOn()
                    2 -> engineManager.onWarmupStarted()
                    3 -> engineManager.onEngineOff()
                }
                updateTexts()
            }
            .setOnCancelListener { engineManager.onParkedEngineOn() }
            .create()
        dlg.window?.setType(overlayWindowType()); dlg.show(); activeDialog = dlg
    }

    override fun showJamReprompt() {
        dismissActiveDialog()
        val dlg = AlertDialog.Builder(this)
            .setTitle("Ещё в пробке?")
            .setItems(arrayOf("Да, ещё в пробке", "Уже поехали", "Заглушил двигатель")) { _, which ->
                when (which) {
                    0 -> engineManager.onStillInJam()
                    1 -> engineManager.onJamOver()
                    2 -> engineManager.onEngineOff()
                }
                updateTexts()
            }
            .setOnCancelListener { engineManager.onStillInJam() }
            .create()
        dlg.window?.setType(overlayWindowType()); dlg.show(); activeDialog = dlg
    }

    override fun showWarmupEndPrompt() {
        dismissActiveDialog()
        val dlg = AlertDialog.Builder(this)
            .setTitle("Прогрев завершён?")
            .setItems(arrayOf("Да, прогрев закончен", "Заглушил двигатель", "Ещё греюсь")) { _, which ->
                when (which) {
                    0 -> engineManager.onWarmupEndConfirmed()
                    1 -> engineManager.onEngineOff()
                    2 -> engineManager.onWarmupExtended()
                }
                updateTexts()
            }
            .setOnCancelListener { engineManager.onWarmupExtended() }
            .create()
        dlg.window?.setType(overlayWindowType()); dlg.show(); activeDialog = dlg
    }

    private fun dismissActiveDialog() {
        try { activeDialog?.dismiss() } catch (_: Exception) { }
        activeDialog = null
    }

    private fun tickEngineAndUi() {
        val now = System.currentTimeMillis()
        val lastMove = fuelRepo.lastMovementTimestamp

        if (fuelRepo.motionState == EngineStateManager.MOTION_MOVING && lastMove > 0 &&
            now - lastMove > EngineStateManager.NO_MOVEMENT_TIMEOUT_MS) {
            engineManager.onNoMovementTimeout()
        }
        engineManager.onTick()

        if (fuelRepo.promptActive && fuelRepo.notifyMode == EngineStateManager.NOTIFY_WIDGET_BUTTONS
            && isAttached && frontSide.visibility == View.VISIBLE) {
            frontSide.visibility = View.GONE; backSide.visibility = View.VISIBLE
        }

        updatePromptUi(); updateTexts()
    }

    private fun updateTexts() {
        val current = fuelRepo.currentOdometerKm
        val refuelOdo = fuelRepo.refuelOdometerKm
        val tank = fuelRepo.tankCapacityLiters
        val cons = fuelRepo.averageConsumptionL100
        val extra = fuelRepo.extraFuelAddedL
        val fuelND = fuelRepo.fuelNoDistanceL

        val mileage = (current - refuelOdo).coerceAtLeast(0f)
        val spent = mileage * cons / 100f + fuelND
        val remaining = (tank - spent + extra).coerceIn(0f, tank)
        val rangeKm = if (cons > 0f) remaining / cons * 100f else 0f

        if (isAttached) {
            textSpent.text = String.format(Locale.US, "%.1f л", spent)
            textRemaining.text = String.format(Locale.US, "%.1f л", remaining)
            textMileage.text = String.format(Locale.US, "%.1f км", mileage)
            textRange.text = String.format(Locale.US, "%.1f км", rangeKm)

            modeJam.text = String.format(Locale.US, "Прб. %.2f", fuelRepo.fuelJamL)
            modeWarmup.text = String.format(Locale.US, "Прг. %.2f", fuelRepo.fuelWarmupL)
            modeParked.text = String.format(Locale.US, "Ост. %.2f", fuelRepo.fuelParkedIdleL)
            modeOff.text = "Выкл."

            val jamActive = fuelRepo.motionState == EngineStateManager.MOTION_JAM &&
                    fuelRepo.engineState != EngineStateManager.ENGINE_OFF
            val warmupActive = fuelRepo.motionState == EngineStateManager.MOTION_PARKED &&
                    fuelRepo.warmupPhase == EngineStateManager.WARMUP_FAST_IDLE
            val parkedActive = fuelRepo.motionState == EngineStateManager.MOTION_PARKED &&
                    fuelRepo.engineState == EngineStateManager.ENGINE_ON &&
                    fuelRepo.warmupPhase != EngineStateManager.WARMUP_FAST_IDLE
            val offActive = fuelRepo.engineState == EngineStateManager.ENGINE_OFF

            val activeColor = Color.parseColor("#FFFFFFFF")
            val inactiveColor = Color.parseColor("#80FFFFFF")
            modeJam.setTextColor(if (jamActive) activeColor else inactiveColor)
            modeWarmup.setTextColor(if (warmupActive) activeColor else inactiveColor)
            modeParked.setTextColor(if (parkedActive) activeColor else inactiveColor)
            modeOff.setTextColor(if (offActive) activeColor else inactiveColor)

            modeJam.setTypeface(null, if (jamActive) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            modeWarmup.setTypeface(null, if (warmupActive) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            modeParked.setTypeface(null, if (parkedActive) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            modeOff.setTypeface(null, if (offActive) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)

            val a = AppearanceRepository.load(this)
            AppearanceApplier.applyScale(floatingView, a, remaining, tank, resources.displayMetrics.density)
        }

        CwgRefresher.refreshAll(this)
    }

    private fun updatePromptUi() {
        if (!isAttached) return
        val kind = fuelRepo.promptKind
        val active = fuelRepo.promptActive && fuelRepo.notifyMode == EngineStateManager.NOTIFY_WIDGET_BUTTONS

        if (active) {
            promptPanel.visibility = View.VISIBLE; normalPanel.visibility = View.GONE
            floatingView.findViewById<TextView>(R.id.tvPromptTitle)?.text = when (kind) {
                EngineStateManager.PROMPT_STATE -> "Что происходит?"
                EngineStateManager.PROMPT_JAM_REPROMPT -> "Ещё в пробке?"
                EngineStateManager.PROMPT_WARMUP_END -> "Прогрев завершён?"
                else -> ""
            }
            show(R.id.btnPromptJam, kind == EngineStateManager.PROMPT_STATE)
            show(R.id.btnPromptParkedOn, kind == EngineStateManager.PROMPT_STATE)
            show(R.id.btnPromptParkedOff, kind == EngineStateManager.PROMPT_STATE)
            show(R.id.btnPromptWarmup, kind == EngineStateManager.PROMPT_STATE)
            show(R.id.btnPromptStillJam, kind == EngineStateManager.PROMPT_JAM_REPROMPT)
            show(R.id.btnPromptJamOver, kind == EngineStateManager.PROMPT_JAM_REPROMPT)
            show(R.id.btnPromptEngineOff, kind == EngineStateManager.PROMPT_JAM_REPROMPT)
            show(R.id.btnPromptWarmupDone, kind == EngineStateManager.PROMPT_WARMUP_END)
        } else {
            promptPanel.visibility = View.GONE; normalPanel.visibility = View.VISIBLE
        }
    }

    private fun show(id: Int, visible: Boolean) {
        floatingView.findViewById<View>(id)?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun buildNotification(): android.app.Notification {
        val tapIntent = Intent(this, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(this, 200, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val showIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_SHOW_WIDGET }
        val showPi = PendingIntent.getForegroundService(this, 201, showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(if (fuelRepo.widgetVisible) getString(R.string.notif_text) else getString(R.string.notif_hidden_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(contentPi)
            .setOngoing(true)
        if (!fuelRepo.widgetVisible) builder.addAction(0, getString(R.string.notif_action_show), showPi)
        return builder.build()
    }

    private fun refreshNotification() {
        getSystemService(NotificationManager::class.java).notify(1, buildNotification())
    }

    @SuppressLint("MissingPermission")
    private fun startGps() {
        if (gpsTracker != null) return
        gpsTracker = GpsTracker(this,
            onDistanceMeters = { m -> fuelRepo.addGpsDistanceMeters(m) },
            onMovementConfirmed = { engineManager.onMovementDetected() }
        ).also { it.start() }
    }

    private fun bindViews() {
        frontSide = floatingView.findViewById(R.id.frontSide)
        backSide = floatingView.findViewById(R.id.backSide)
        normalPanel = floatingView.findViewById(R.id.normalPanel)
        promptPanel = floatingView.findViewById(R.id.promptPanel)
        textSpent = floatingView.findViewById(R.id.textSpent)
        textRemaining = floatingView.findViewById(R.id.textRemaining)
        textMileage = floatingView.findViewById(R.id.textMileage)
        textRange = floatingView.findViewById(R.id.textRange)
        modeJam = floatingView.findViewById(R.id.modeJam)
        modeWarmup = floatingView.findViewById(R.id.modeWarmup)
        modeParked = floatingView.findViewById(R.id.modeParked)
        modeOff = floatingView.findViewById(R.id.modeOff)
    }

    private fun setupButtons() {
        floatingView.findViewById<ImageButton>(R.id.btnFlipToBack).setOnClickListener {
            backSide.visibility = View.VISIBLE; frontSide.visibility = View.GONE
        }
        floatingView.findViewById<ImageButton>(R.id.btnFlipToFront).setOnClickListener {
            frontSide.visibility = View.VISIBLE; backSide.visibility = View.GONE
        }
        floatingView.findViewById<View>(R.id.btnRefuelFull).setOnClickListener { showRefuelFullDialog() }
        floatingView.findViewById<View>(R.id.btnRefuelPlus).setOnClickListener { showRefuelPlusDialog() }
        floatingView.findViewById<Button>(R.id.btnOpenSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
        floatingView.findViewById<Button>(R.id.btnIoCwg).setOnClickListener {
            hideWidget(); Toast.makeText(this, getString(R.string.hint_io_cwg), Toast.LENGTH_LONG).show()
        }
        modeJam.setOnClickListener { engineManager.setJamMode(); updateTexts() }
        modeWarmup.setOnClickListener { engineManager.setWarmupMode(); updateTexts() }
        modeParked.setOnClickListener { engineManager.setParkedMode(); updateTexts() }
        modeOff.setOnClickListener { engineManager.setEngineOff(); updateTexts() }

        floatingView.post {
            val w = frontSide.width
            if (w > 0) {
                backSide.layoutParams = backSide.layoutParams.apply { width = w }
                backSide.requestLayout()
            }
        }
    }

    private fun setupPromptButtons() {
        floatingView.findViewById<View>(R.id.btnPromptJam).setOnClickListener { engineManager.onJamConfirmed(); afterPromptAnswer() }
        floatingView.findViewById<View>(R.id.btnPromptParkedOn).setOnClickListener { engineManager.onParkedEngineOn(); afterPromptAnswer() }
        floatingView.findViewById<View>(R.id.btnPromptParkedOff).setOnClickListener { engineManager.onParkedEngineOff(); afterPromptAnswer() }
        floatingView.findViewById<View>(R.id.btnPromptWarmup).setOnClickListener { engineManager.onWarmupStarted(); afterPromptAnswer() }
        floatingView.findViewById<View>(R.id.btnPromptStillJam).setOnClickListener { engineManager.onStillInJam(); afterPromptAnswer() }
        floatingView.findViewById<View>(R.id.btnPromptJamOver).setOnClickListener { engineManager.onJamOver(); afterPromptAnswer() }
        floatingView.findViewById<View>(R.id.btnPromptEngineOff).setOnClickListener { engineManager.onEngineOff(); afterPromptAnswer() }
        floatingView.findViewById<View>(R.id.btnPromptWarmupDone).setOnClickListener { engineManager.onWarmupEndConfirmed(); afterPromptAnswer() }
    }

    private fun afterPromptAnswer() {
        fuelRepo.promptActive = false; fuelRepo.promptKind = EngineStateManager.PROMPT_NONE
        frontSide.visibility = View.VISIBLE; backSide.visibility = View.GONE
        updatePromptUi(); updateTexts()
    }

    private fun setupIconLongPresses() {
        attachIconLongPress(floatingView.findViewById(R.id.icSpent), "spent")
        attachIconLongPress(floatingView.findViewById(R.id.icRemaining), "remaining")
        attachIconLongPress(floatingView.findViewById(R.id.icMileage), "mileage")
        attachIconLongPress(floatingView.findViewById(R.id.icRange), "range")
    }

    private fun attachIconLongPress(view: View?, category: String) {
        view ?: return
        val localHandler = Handler(Looper.getMainLooper())
        var pending: Runnable? = null
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (iconPickerOpen) return@setOnTouchListener true
                    pending = Runnable { pending = null; showIconPicker(category) }
                    localHandler.postDelayed(pending!!, 3000L); true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pending?.let { localHandler.removeCallbacks(it) }; pending = null; true
                }
                else -> false
            }
        }
    }

    private fun showIconPicker(category: String) {
        if (iconPickerOpen) return
        iconPickerOpen = true
        val appearance = AppearanceRepository.load(this)
        val icons = when (category) {
            "spent" -> VectorIconLibrary.SPENT
            "remaining" -> VectorIconLibrary.REMAINING
            "mileage" -> VectorIconLibrary.MILEAGE
            "range" -> VectorIconLibrary.RANGE
            "jam" -> VectorIconLibrary.JAM
            "warmup" -> VectorIconLibrary.WARMUP
            else -> { iconPickerOpen = false; return }
        }
        val current = when (category) {
            "spent" -> appearance.spentIconIndex
            "remaining" -> appearance.remainingIconIndex
            "mileage" -> appearance.mileageIconIndex
            "range" -> appearance.rangeIconIndex
            "jam" -> appearance.jamIconIndex
            else -> appearance.warmupIconIndex
        }
        val density = resources.displayMetrics.density
        val tc = try { Color.parseColor(appearance.textColor) } catch (_: Exception) { Color.WHITE }
        val selectedBg = 0x66FFFFFF; val normalBg = 0x00000000
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }
        var chosen = current
        val thumbs = mutableListOf<ImageView>()
        icons.forEachIndexed { index, icon ->
            val size = (56 * density).toInt(); val pad = (8 * density).toInt()
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = (8 * density).toInt() }
                setPadding(pad, pad, pad, pad)
                setImageDrawable(VectorIconLibrary.createDrawable(icon.pathData, tc))
                setBackgroundColor(if (index == current) selectedBg else normalBg)
                setOnClickListener {
                    chosen = index
                    thumbs.forEachIndexed { i, v -> v.setBackgroundColor(if (i == index) selectedBg else normalBg) }
                }
            }
            thumbs.add(iv); row.addView(iv)
        }
        AlertDialog.Builder(this).setTitle(R.string.ic_picker_title).setView(row)
            .setPositiveButton(R.string.ok) { _, _ ->
                val newAppearance = when (category) {
                    "spent" -> appearance.copy(spentIconIndex = chosen)
                    "remaining" -> appearance.copy(remainingIconIndex = chosen)
                    "mileage" -> appearance.copy(mileageIconIndex = chosen)
                    "range" -> appearance.copy(rangeIconIndex = chosen)
                    "jam" -> appearance.copy(jamIconIndex = chosen)
                    else -> appearance.copy(warmupIconIndex = chosen)
                }
                AppearanceRepository.save(this, newAppearance)
            }
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener { iconPickerOpen = false }
            .create().also { it.window?.setType(overlayWindowType()) }.show()
    }

    private fun showRefuelPlusDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_refuel_plus, null)
        val etLiters = view.findViewById<EditText>(R.id.etLiters)
        val etPrice = view.findViewById<EditText>(R.id.etPrice)
        AlertDialog.Builder(this).setTitle(R.string.dialog_refuel_plus_title).setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                val liters = etLiters.text.toString().replace(',', '.').toFloatOrNull() ?: 0f
                val price = etPrice.text.toString().replace(',', '.').toFloatOrNull() ?: 0f
                if (liters > 0f) {
                    RefuelJournal.add(this, RefuelEvent(
                        timestamp = System.currentTimeMillis(),
                        odometerKm = fuelRepo.currentOdometerKm,
                        liters = liters,
                        pricePerLiter = price,
                        fullTank = false,
                        avgConsumptionL100 = 0f
                    ))
                    fuelRepo.extraFuelAddedL = fuelRepo.extraFuelAddedL + liters
                    updateTexts()
                }
            }.setNegativeButton(R.string.cancel, null).create()
            .also { it.window?.setType(overlayWindowType()) }.show()
    }

    private fun showRefuelFullDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_refuel_full, null)
        val etLiters = view.findViewById<EditText>(R.id.etLiters)
        val etPrice = view.findViewById<EditText>(R.id.etPrice)
        val etOdo = view.findViewById<EditText>(R.id.etOdometer)
        etLiters.setText(String.format(Locale.US, "%.1f", fuelRepo.tankCapacityLiters))
        etOdo.setText(String.format(Locale.US, "%.1f", fuelRepo.currentOdometerKm))
        AlertDialog.Builder(this).setTitle(R.string.dialog_refuel_full_title).setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                val liters = etLiters.text.toString().replace(',', '.').toFloatOrNull() ?: 0f
                val price = etPrice.text.toString().replace(',', '.').toFloatOrNull() ?: 0f
                val realOdo = etOdo.text.toString().replace(',', '.').toFloatOrNull() ?: fuelRepo.currentOdometerKm
                handleRefuelFull(liters, price, realOdo)
            }.setNegativeButton(R.string.cancel, null).create()
            .also { it.window?.setType(overlayWindowType()) }.show()
    }

    private fun handleRefuelFull(liters: Float, price: Float, realOdometerKm: Float) {
        val now = System.currentTimeMillis()
        val prevFull = RefuelJournal.previousFull(this, now)
        fuelRepo.syncOdometer(realOdometerKm)

        var cycleAvg = 0f
        var cycleDistance = 0f
        var cycleLiters = 0f

        if (prevFull != null) {
            cycleDistance = (realOdometerKm - prevFull.odometerKm).coerceAtLeast(0f)
            val partial = RefuelJournal.litersBetween(this, prevFull.timestamp, now)
            cycleLiters = partial + liters
            val noDistForCycle = fuelRepo.fuelJamL + fuelRepo.fuelWarmupL + fuelRepo.fuelParkedIdleL
            val cleanLiters = (cycleLiters - noDistForCycle).coerceAtLeast(0f)
            if (cycleDistance > 20f && cleanLiters > 5f) {
                cycleAvg = cleanLiters / cycleDistance * 100f
            }
        }

        RefuelJournal.add(this, RefuelEvent(
            timestamp = now,
            odometerKm = realOdometerKm,
            liters = liters,
            pricePerLiter = price,
            fullTank = true,
            avgConsumptionL100 = cycleAvg
        ))

        if (cycleAvg > 0f && prevFull != null) {
            AdaptiveConsumption.addCycle(this, ConsumptionCycle(
                startTimestamp = prevFull.timestamp, endTimestamp = now,
                startOdometerKm = prevFull.odometerKm, endOdometerKm = realOdometerKm,
                distanceKm = cycleDistance, totalLiters = cycleLiters,
                avgConsumptionL100 = cycleAvg
            ))
        }

        fuelRepo.refuelOdometerKm = realOdometerKm
        fuelRepo.extraFuelAddedL = 0f
        fuelRepo.fuelJamL = 0f
        fuelRepo.fuelWarmupL = 0f
        fuelRepo.fuelParkedIdleL = 0f

        updateTexts()
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun applyAppearance() {
        if (!::floatingView.isInitialized) return
        val a = AppearanceRepository.load(this)
        AppearanceApplier.applyStatic(floatingView, a, resources.displayMetrics.density)
    }

    private fun setupTouchListener() {
        var initialX = 0; var initialY = 0; var initialTouchX = 0f; var initialTouchY = 0f
        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x; initialY = layoutParams.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    if (isAttached) windowManager.updateViewLayout(floatingView, layoutParams); true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isAttached) {
                        fuelRepo.overlayX = layoutParams.x; fuelRepo.overlayY = layoutParams.y
                        FuelStateRepository.flush()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Fuel Overlay", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun attachOverlay() {
        if (isAttached) return
        if (!::floatingView.isInitialized) return
        try { windowManager.addView(floatingView, layoutParams); isAttached = true; refreshNotification() } catch (_: Exception) { }
    }

    private fun detachOverlay() {
        if (!isAttached) return
        fuelRepo.overlayX = layoutParams.x; fuelRepo.overlayY = layoutParams.y
        FuelStateRepository.flush()
        try { if (::floatingView.isInitialized && floatingView.isAttachedToWindow) windowManager.removeView(floatingView) } catch (_: Exception) { }
        isAttached = false; refreshNotification()
    }

    fun showWidget() {
        fuelRepo.widgetVisible = true; fuelRepo.startHidden = false
        attachOverlay(); CwgRefresher.refreshAll(this)
    }
    fun hideWidget() {
        fuelRepo.widgetVisible = false; fuelRepo.startHidden = true
        detachOverlay(); CwgRefresher.refreshAll(this)
    }
    fun toggleWidget() { if (isAttached) hideWidget() else showWidget() }

    companion object {
        private const val CHANNEL_ID = "fuel_overlay_channel"
        const val ACTION_REFUEL_FULL = "com.infodevelop54.fueloverlay.ACTION_REFUEL_FULL"
        const val ACTION_REFUEL_PLUS = "com.infodevelop54.fueloverlay.ACTION_REFUEL_PLUS"
        const val ACTION_SHOW_WIDGET = "com.infodevelop54.fueloverlay.ACTION_SHOW_WIDGET"
        const val ACTION_OPEN_SETTINGS = "com.infodevelop54.fueloverlay.ACTION_OPEN_SETTINGS"
        const val ACTION_JAM_CONFIRMED = "com.infodevelop54.fueloverlay.ACTION_JAM_CONFIRMED"
        const val ACTION_PARKED_ENGINE_ON = "com.infodevelop54.fueloverlay.ACTION_PARKED_ENGINE_ON"
        const val ACTION_PARKED_ENGINE_OFF = "com.infodevelop54.fueloverlay.ACTION_PARKED_ENGINE_OFF"
        const val ACTION_WARMUP_STARTED = "com.infodevelop54.fueloverlay.ACTION_WARMUP_STARTED"
        const val ACTION_WARMUP_DONE = "com.infodevelop54.fueloverlay.ACTION_WARMUP_DONE"
        const val ACTION_WARMUP_EXTEND = "com.infodevelop54.fueloverlay.ACTION_WARMUP_EXTEND"
        const val ACTION_STILL_IN_JAM = "com.infodevelop54.fueloverlay.ACTION_STILL_IN_JAM"
        const val ACTION_JAM_OVER = "com.infodevelop54.fueloverlay.ACTION_JAM_OVER"
        const val ACTION_ENGINE_OFF = "com.infodevelop54.fueloverlay.ACTION_ENGINE_OFF"
        const val ACTION_SET_JAM = "com.infodevelop54.fueloverlay.ACTION_SET_JAM"
        const val ACTION_SET_WARMUP = "com.infodevelop54.fueloverlay.ACTION_SET_WARMUP"
        const val ACTION_SET_PARKED = "com.infodevelop54.fueloverlay.ACTION_SET_PARKED"
        const val ACTION_SET_OFF = "com.infodevelop54.fueloverlay.ACTION_SET_OFF"

        @Volatile var instance: OverlayService? = null
            private set
    }
}