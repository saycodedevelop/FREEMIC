package com.karaokelink.presentation

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.text.TextUtils
import com.karaokelink.R
import android.app.AlertDialog
import android.content.Intent
import android.provider.Settings
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import com.karaokelink.core.network.DeviceName
import com.karaokelink.data.AndroidSessionRepository
import com.karaokelink.data.SessionRuntime
import com.karaokelink.domain.*
import kotlinx.coroutines.*
import java.net.NetworkInterface
import java.util.Collections
import kotlin.math.roundToInt

open class MainActivity : Activity() {
    private val ui = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: SessionRepository
    private lateinit var stage: FrameLayout
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var status: TextView
    private lateinit var connection: TextView
    private lateinit var meter: ProgressBar
    private var statusPanel: LinearLayout? = null
    private var micConnectSection: LinearLayout? = null
    private var micControlPanel: LinearLayout? = null
    private var micLivePanel: LinearLayout? = null
    private var settingsDialog: Dialog? = null
    private var micLiveTitle: TextView? = null
    private var micLiveHint: TextView? = null
    private var micStopButton: Button? = null
    private var changePlayerButton: Button? = null
    private var discoveryExpanded = false
    private var welcomeDialog: Dialog? = null
    private var welcomeOffered = false
    private var hosts: LinearLayout? = null
    private var hostSignature = ""
    private var rendered: Mode? = null
    private var web: WebView? = null
    private var playerFrame: FrameLayout? = null
    private var pageNotice: TextView? = null
    private var pageProgress: ProgressBar? = null
    private var fullscreen: FrameLayout? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var browserFullscreen = false
    private var fullscreenSystemUi = 0
    private var touchLock: FrameLayout? = null
    private var systemBackCallback: android.window.OnBackInvokedCallback? = null
    private var firstMode: Mode? = null
    private var lastUrl = PlayerNavigation.HOME
    private var pendingIp: String? = null
    private var pendingMic = false
    private var fx = MicEffects()
    private var micVolume = 1f
    private var musicVolume = 1f
    private var delayMs = 0
    private val hotspot by lazy { SessionRuntime.hotspot(applicationContext) }
    private var hotspotStatus: TextView? = null
    private var hotspotButton: Button? = null
    private var hotspotQr: WifiQrView? = null
    private var hotspotQrHint: TextView? = null
    private val accent = AppUi.mint
    private val screenColor = AppUi.background
    private val muted = AppUi.muted
    private val audio by lazy { getSystemService(AudioManager::class.java) }

    protected open fun createRepository(): SessionRepository = SessionRuntime.get(applicationContext)

    protected open fun automaticWelcome(): Boolean = !getSharedPreferences("karaokelink-ui", MODE_PRIVATE).getBoolean("welcomeComplete", false)

    private fun showGettingStarted() {
        if (welcomeDialog?.isShowing == true) return
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(currentFocus?.windowToken, 0)
        welcomeDialog = GettingStarted.show(this) {
            getSharedPreferences("karaokelink-ui", MODE_PRIVATE).edit().putBoolean("welcomeComplete", true).apply()
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            systemBackCallback = android.window.OnBackInvokedCallback { if (!handlePlayerBack()) moveTaskToBack(true) }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, systemBackCallback!!)
        }
        firstMode = savedInstanceState?.getString("mode")?.let { runCatching { Mode.valueOf(it) }.getOrNull() }
        lastUrl = savedInstanceState?.getString("musicUrl") ?: PlayerNavigation.HOME
        repository = createRepository()
        repository.lowLatency(getSharedPreferences("karaokelink-ui", MODE_PRIVATE).getBoolean("lowLatency", true))
        hotspot.onChanged = { updateHotspotUi() }
        ui.launch {
            repository.state.collect { state ->
                if (rendered != state.mode) render(state.mode)
                updateSessionUi(state)
                val signature = state.hosts.joinToString { "${it.ip}:${it.name}" }
                if (signature != hostSignature) {
                    hostSignature = signature
                    hosts?.let { list ->
                        list.removeAllViews()
                        renderHosts(list, state.hosts)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val mode = firstMode ?: repository.state.value.mode
        firstMode = null
        // Returning from a browser or another app resumes the role, not the Home screen.
        if (!repository.continuesInBackground) ui.launch { repository.select(mode) }
        web?.onResume()
    }

    private fun render(mode: Mode) {
        settingsDialog?.dismiss(); settingsDialog = null
        hideTouchLock(); hideFullscreen(); destroyBrowser()
        rendered = mode
        hosts = null; hostSignature = "!"
        playerFrame = null; pageNotice = null; pageProgress = null
        statusPanel = null; micConnectSection = null; micControlPanel = null; micLivePanel = null
        micLiveTitle = null; micLiveHint = null; micStopButton = null; changePlayerButton = null
        discoveryExpanded = false
        hotspotStatus = null; hotspotButton = null; hotspotQr = null; hotspotQrHint = null
        if (mode != Mode.HOST) hotspot.stop()
        if (mode != Mode.HOST) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stage = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(screenColor, Color.rgb(17, 23, 42)))
        }
        root = column().apply { setPadding(dp(16), dp(10), dp(16), dp(12)) }
        stage.addView(root, FrameLayout.LayoutParams(-1, -1))
        stage.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(stage); stage.requestApplyInsets()
        val header = row(root).apply { minimumHeight = dp(52); tag = "app-header" }
        val brand = column()
        brand.addView(AppUi.label(this, "KaraokeLink", 20f, bold = true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        brand.addView(AppUi.label(this, when (mode) {
            Mode.HOME -> getString(R.string.tagline)
            Mode.HOST -> getString(R.string.player)
            Mode.MIC -> getString(R.string.microphone)
        }, 12f, muted).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        if (mode != Mode.HOME) button(header, getString(R.string.home), false) { confirmHome() }
        header.addView(AppUi.iconButton(this, R.drawable.ic_help, getString(R.string.help)) { showGettingStarted() },
            LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(8) })
        statusPanel = column().apply {
            background = AppUi.shape(this@MainActivity, radius = 16)
            setPadding(dp(12), dp(9), dp(12), dp(9)); tag = "session-status"
            visibility = if (mode == Mode.HOST) View.VISIBLE else View.GONE
        }.also { root.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(8) }) }
        status = AppUi.label(this, "", 14f, accent, true).also { statusPanel!!.addView(it) }
        connection = AppUi.label(this, "", 12f, muted).apply {
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(3), 0, 0)
        }.also { statusPanel!!.addView(it) }
        if (mode == Mode.HOST) {
            statusPanel!!.contentDescription = getString(R.string.player_status_hint)
            statusPanel!!.setOnClickListener { showHostConnection() }
        }
        meter = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; progressTintList = ColorStateList.valueOf(accent)
            progressBackgroundTintList = ColorStateList.valueOf(AppUi.border)
            contentDescription = getString(R.string.mic_meter)
        }
        body = column()
        if (mode == Mode.HOST) root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        else root.addView(ScrollView(this).apply { isFillViewport = true; clipToPadding = false; addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        when (mode) { Mode.HOME -> home(); Mode.MIC -> mic(); Mode.HOST -> host() }
        applyPlayerLayout()
        if (mode == Mode.HOME && !welcomeOffered) stage.post {
            if (rendered == Mode.HOME && !isFinishing && automaticWelcome()) {
                welcomeOffered = true
                showGettingStarted()
            }
        }
    }

    private fun updateSessionUi(state: SessionState) {
        val copy = SessionPresenter.describe(this, state, repository.continuesInBackground)
        val tint = when (copy.tone) { StatusTone.READY -> accent; StatusTone.WAITING -> AppUi.amber; StatusTone.ERROR -> AppUi.danger }
        val title = "●  ${copy.title}"
        if (status.text.toString() != title) status.text = title
        status.contentDescription = copy.title
        status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        status.setTextColor(tint)
        if (state.mode == Mode.HOST) statusPanel?.contentDescription = "${copy.title}. ${copy.detail}"
        connection.text = copy.detail
        meter.progress = (state.vu.coerceIn(0f, 1f) * 1000).toInt()
        if (state.mode == Mode.MIC) {
            val live = state.peers > 0
            micLivePanel?.visibility = if (live || repository.continuesInBackground || copy.tone == StatusTone.ERROR) View.VISIBLE else View.GONE
            micLiveTitle?.text = copy.title
            micLiveTitle?.setTextColor(tint)
            micLiveHint?.text = copy.detail
            micControlPanel?.visibility = if (live) View.VISIBLE else View.GONE
            micConnectSection?.visibility = if (!live || discoveryExpanded) View.VISIBLE else View.GONE
            micStopButton?.visibility = if (live || repository.continuesInBackground) View.VISIBLE else View.GONE
            micStopButton?.text = if (live) getString(R.string.stop_stream) else getString(R.string.cancel_connection)
            changePlayerButton?.visibility = if (live) View.VISIBLE else View.GONE
            changePlayerButton?.text = if (discoveryExpanded) getString(R.string.hide_players) else getString(R.string.change_player)
        }
    }

    private fun home() {
        body.addView(AppUi.textAction(this, getString(R.string.language), accent).apply {
            tag = "language-picker"
            setOnClickListener { AppLanguage.show(this@MainActivity) }
        }, LinearLayout.LayoutParams(-1, -2))
        text(body, getString(R.string.home_title), 25f)
        text(body, getString(R.string.home_detail), 14f, muted)
        val wifi = row(body).apply { setPadding(0, dp(6), 0, dp(4)) }
        wifi.addView(AppUi.icon(this, R.drawable.ic_wifi, muted, 20))
        AppUi.label(this, getString(R.string.same_wifi), 12f, muted).also {
            wifi.addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
        }
        fun role(title: String, subtitle: String, step: String, icon: Int, tint: Int, actionLabel: String, action: () -> Unit) {
            val panel = card(body)
            val line = row(panel)
            val tile = FrameLayout(this).apply { background = AppUi.shape(this@MainActivity, Color.argb(25, Color.red(tint), Color.green(tint), Color.blue(tint)), 14, null) }
            tile.addView(AppUi.icon(this, icon, tint, 24), FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
            line.addView(tile, LinearLayout.LayoutParams(dp(46), dp(46)).apply { rightMargin = dp(12) })
            val words = column()
            words.addView(AppUi.label(this, step, 11f, tint, true))
            words.addView(AppUi.label(this, title, 19f, bold = true).apply { setPadding(0, dp(3), 0, 0) })
            line.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
            text(panel, subtitle, 13f, muted)
            button(panel, actionLabel, primary = icon == R.drawable.ic_music, tint = tint, action = action)
        }
        role(getString(R.string.player), getString(R.string.player_detail), getString(R.string.player_step), R.drawable.ic_music, accent, getString(R.string.start_player)) { startHost() }
        role(getString(R.string.mic_role), getString(R.string.mic_detail), getString(R.string.mic_step), R.drawable.ic_mic, AppUi.violet, getString(R.string.use_mic)) { chooseMic(null) }
        body.addView(AppUi.textAction(this, getString(R.string.guide_open), accent).apply { setOnClickListener { showGettingStarted() } }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        text(body, getString(R.string.latency_hint), 12f, muted)
    }

    private fun discovery(parent: LinearLayout) {
        val section = card(parent)
        micConnectSection = section
        section.tag = "mic-discovery"
        text(section, getString(R.string.select_player), 19f)
        text(section, getString(R.string.open_other_player), 13f, muted)
        hosts = column().also { section.addView(it) }
        val manual = expandable(section, getString(R.string.connect_ip))
        text(manual, getString(R.string.ip_hint), 13f, muted)
        val ip = input(manual, getString(R.string.ip_example)).apply { inputType = android.text.InputType.TYPE_CLASS_PHONE }
        button(manual, getString(R.string.connect_this_ip)) {
            val value = ip.text.toString().trim()
            val parts = value.split('.')
            if (parts.size != 4 || parts.any { part -> part.isEmpty() || part.any { !it.isDigit() } || part.toIntOrNull() !in 0..255 }) ip.error = getString(R.string.ip_invalid)
            else chooseMic(value)
        }
    }

    private fun renderHosts(list: LinearLayout, found: List<Host>) {
        if (found.isEmpty()) {
            val empty = column().apply { setPadding(dp(12), dp(12), dp(12), dp(8)); background = AppUi.shape(this@MainActivity, screenColor, 14) }
            list.addView(empty)
            text(empty, getString(R.string.no_players), 16f)
            text(empty, getString(R.string.discovery_hint), 13f, muted)
            button(empty, getString(R.string.search_again), false) { ui.launch { repository.select(Mode.MIC) } }
            button(empty, getString(R.string.connection_help), false) { showConnectionHelp() }
        }
        found.forEach { host ->
            val entry = column().apply { setPadding(dp(10), dp(10), dp(10), dp(8)); background = AppUi.shape(this@MainActivity, screenColor, 14) }
            list.addView(entry, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            text(entry, host.name, 16f)
            text(entry, host.ip, 12f, muted)
            button(entry, getString(R.string.connect_host, host.name), tint = AppUi.violet) { chooseMic(host.ip) }
        }
    }

    private fun showConnectionHelp() {
        AlertDialog.Builder(this).setTitle(getString(R.string.player_not_found))
            .setMessage(getString(R.string.connection_steps))
            .setPositiveButton(getString(R.string.got_it), null).show()
    }

    private fun showHostConnection() {
        val content = column().apply { setPadding(0, 0, 0, dp(8)) }
        text(content, DeviceName.current(), 20f, accent)
        button(content, getString(R.string.hotspot_title), false) { showHotspotSetup() }
        text(content, getString(R.string.host_steps), 15f, muted)
        text(content, getString(R.string.use_this_ip), 13f, muted)
        text(content, localIps(), 20f, accent).apply { setTextIsSelectable(true) }
        button(content, getString(R.string.copy_ip), false) {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Host IP", localIps()))
            toast(getString(R.string.ip_copied))
        }
        showSettingsSheet(getString(R.string.connect_microphone), getString(R.string.two_phones_wifi), content)
    }
    private fun showHotspotSetup() {
        val content = column().apply { setPadding(0, 0, 0, dp(8)) }
        text(content, getString(R.string.hotspot_hint), 14f, muted)
        hotspotQr = WifiQrView(this).apply {
            visibility = View.GONE; tag = "hotspot-qr"
            contentDescription = getString(R.string.hotspot_qr_label)
        }.also { content.addView(it, LinearLayout.LayoutParams(-1, dp(240)).apply { topMargin = dp(8); bottomMargin = dp(8) }) }
        hotspotQrHint = text(content, getString(R.string.hotspot_qr_hint), 14f, muted).apply { visibility = View.GONE }
        hotspotStatus = text(content, "", 16f, accent).apply { setTextIsSelectable(true) }
        hotspotButton = button(content, getString(R.string.hotspot_start)) {
            if (hotspot.state.ssid != null) hotspot.stop() else startPlayerHotspot()
        }
        button(content, getString(R.string.hotspot_system), false) {
            runCatching { startActivity(Intent("android.settings.TETHER_SETTINGS")) }
                .onFailure { runCatching { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) } }
        }
        updateHotspotUi()
        showSettingsSheet(getString(R.string.hotspot_title), DeviceName.current(), content)
    }

    private fun updateHotspotUi() {
        val state = hotspot.state
        hotspotStatus?.text = when {
            state.starting -> getString(R.string.hotspot_starting)
            state.failed -> getString(R.string.hotspot_failed)
            state.ssid != null -> getString(R.string.hotspot_details, state.ssid, state.password ?: "—")
            else -> getString(R.string.hotspot_idle)
        }
        hotspotButton?.text = getString(if (state.ssid != null) R.string.hotspot_stop else R.string.hotspot_start)
        hotspotButton?.isEnabled = !state.starting
        hotspotQr?.show(state.ssid, state.password)
        hotspotQrHint?.visibility = if (state.ssid != null) View.VISIBLE else View.GONE
    }

    private fun startPlayerHotspot() {
        if (repository.state.value.mode != Mode.HOST) return
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES else Manifest.permission.ACCESS_FINE_LOCATION
        val permissions = if (Build.VERSION.SDK_INT >= 33) arrayOf(permission)
            else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) requestPermissions(permissions, 13)
        else hotspot.start()
    }

    private fun chooseMic(ip: String?) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingMic = true
            pendingIp = ip
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)
        } else if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !getPreferences(MODE_PRIVATE).getBoolean("notificationAsked", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("notificationAsked", true).apply()
            pendingMic = true; pendingIp = ip
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 12)
        } else ui.launch {
            if (repository.state.value.mode != Mode.MIC) repository.select(Mode.MIC)
            ip?.let { repository.connect(it) }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && pendingMic) {
            pendingMic = false
            val ip = pendingIp
            pendingIp = null
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) chooseMic(ip)
            else toast(getString(R.string.mic_permission))
        }
        if (requestCode == 12 && pendingMic) {
            pendingMic = false
            chooseMic(pendingIp)
            pendingIp = null
        }
        if (requestCode == 13) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startPlayerHotspot()
            else toast(getString(R.string.hotspot_permission))
        }
        if (requestCode == 11 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) pickOutput(true)
    }

    private fun mic() {
        discovery(body)
        val live = card(body).also { micLivePanel = it; it.tag = "microphone-session" }
        val hero = row(live)
        hero.addView(AppUi.icon(this, R.drawable.ic_mic, AppUi.violet, 34), LinearLayout.LayoutParams(dp(34), dp(42)).apply { rightMargin = dp(12) })
        val titles = column()
        micLiveTitle = AppUi.label(this, getString(R.string.waiting_connection), 21f, bold = true).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }.also { titles.addView(it) }
        micLiveHint = AppUi.label(this, getString(R.string.choose_before_singing), 12f, muted).also { titles.addView(it) }
        hero.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        text(live, getString(R.string.mic_level), 12f, muted)
        meter.progressTintList = ColorStateList.valueOf(AppUi.violet)
        live.addView(meter, LinearLayout.LayoutParams(-1, dp(12)).apply { topMargin = dp(4); bottomMargin = dp(12) })
        val actions = row(live)
        micStopButton = button(actions, getString(R.string.stop_stream), false, true, tint = AppUi.danger) { ui.launch { repository.select(Mode.MIC) } }
        changePlayerButton = button(actions, getString(R.string.change_player), false, true) {
            discoveryExpanded = !discoveryExpanded
            updateSessionUi(repository.state.value)
        }
        micControlPanel = card(body).also { controls ->
            controls.tag = "mic-controls"
            text(controls, getString(R.string.your_voice), 19f)
            text(controls, getString(R.string.balance_hint), 13f, muted)
            slider(controls, getString(R.string.mic_gain), 0f, 4f, fx.gain) { fx = fx.copy(gain = it); repository.effects(fx) }
            slider(controls, getString(R.string.echo), 0f, 1f, fx.echoMix) { fx = fx.copy(echoMix = it); repository.effects(fx) }
            slider(controls, getString(R.string.reverb), 0f, 1f, fx.reverb) { fx = fx.copy(reverb = it); repository.effects(fx) }
        }
        button(body, getString(R.string.mic_devices), false) { micSettings() }
        text(body, getString(R.string.feedback_hint), 12f, muted)
    }

    private fun micSettings() {
        val settings = column().apply { setPadding(0, 0, 0, dp(8)) }
        button(settings, getString(R.string.select_input), false) {
            val devices = audio.getDevices(AudioManager.GET_DEVICES_INPUTS).filter { it.type != AudioDeviceInfo.TYPE_TELEPHONY }
            val names = arrayOf(getString(R.string.automatic_audio)) + devices.map { it.productName.toString() }
            AlertDialog.Builder(this).setTitle(getString(R.string.input_device)).setItems(names) { _, index ->
                repository.inputDevice(if (index == 0) 0 else devices[index - 1].id)
                toast(getString(R.string.input_selected))
            }.show()
        }
        text(settings, getString(R.string.input_hint), 13f, muted)
        val advanced = expandable(settings, getString(R.string.advanced_voice))
        slider(advanced, getString(R.string.noise_gate), 0f, 0.08f, fx.gate) { fx = fx.copy(gate = it); repository.effects(fx) }
        slider(advanced, getString(R.string.echo_delay), 5f, 700f, fx.echoMs) { fx = fx.copy(echoMs = it); repository.effects(fx) }
        slider(advanced, getString(R.string.echo_feedback), 0f, 0.85f, fx.feedback) { fx = fx.copy(feedback = it); repository.effects(fx) }
        slider(advanced, getString(R.string.bass), 0f, 2f, fx.low) { fx = fx.copy(low = it); repository.effects(fx) }
        slider(advanced, getString(R.string.mid), 0f, 2f, fx.mid) { fx = fx.copy(mid = it); repository.effects(fx) }
        slider(advanced, getString(R.string.treble), 0f, 2f, fx.high) { fx = fx.copy(high = it); repository.effects(fx) }
        showSettingsSheet(getString(R.string.mic_settings), getString(R.string.mic_settings_hint), settings)
    }
    private fun startHost() {
        // WebView owns media audio focus. A second request here can receive LOSS when
        // the embedded player starts; closing the session in that callback erased the player.
        val wired = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { isWired(it) }
        ui.launch { repository.outputDevice(wired?.id ?: 0); repository.select(Mode.HOST) }
    }

    private fun host() {
        val search = row(body)
        val query = input(search, getString(R.string.song_hint)).apply {
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        fun openSong() {
            val target = PlayerNavigation.resolve(query.text.toString(), getString(R.string.karaoke_search))
            if (target == null) { query.error = getString(R.string.https_hint); return }
            getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(query.windowToken, 0)
            query.clearFocus()
            loadMusic(target)
        }
        query.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) { openSong(); true } else false
        }
        button(search, getString(R.string.search)) { openSong() }
        val tools = row(body)
        button(tools, "YouTube", false, true) { loadMusic(PlayerNavigation.HOME) }
        button(tools, getString(R.string.reload), false, true) { loadMusic(lastUrl) }
        button(tools, getString(R.string.sound_settings), false, true) { hostSettings() }
        pageProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progressTintList = ColorStateList.valueOf(accent)
            visibility = View.GONE
        }.also { body.addView(it, LinearLayout.LayoutParams(-1, dp(3))) }
        pageNotice = text(body, getString(R.string.play_hint), 12f, muted).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        playerFrame = FrameLayout(this).apply {
            background = AppUi.shape(this@MainActivity, Color.WHITE, 18, null)
            clipToOutline = true
            tag = "player-viewport"
        }.also { body.addView(it, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8); bottomMargin = dp(4) }) }
        createBrowser()
        val displayTools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; tag = "player-display-controls"
            button(this, getString(R.string.player_fullscreen), false) { web?.let { showPlayerFullscreen(it, browser = true) } }
            button(this, getString(R.string.lock_touch), false) { showTouchLock() }
        }
        playerFrame?.addView(displayTools, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END))
        loadMusic(lastUrl)
    }

    private fun createBrowser() {
        val frame = playerFrame ?: return
        val browser = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mediaPlaybackRequiresUserGesture = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.setSupportMultipleWindows(false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val blocked = request.url.scheme != "https"
                    if (blocked && request.isForMainFrame) toast(getString(R.string.https_only))
                    return blocked
                }
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    pageNotice?.text = getString(R.string.loading_song)
                }
                override fun onPageFinished(view: WebView, url: String) {
                    if (Uri.parse(url).scheme == "https") lastUrl = url
                    if (pageNotice?.text == getString(R.string.loading_song)) pageNotice?.text = getString(R.string.video_play_hint)
                    setMusicVolume()
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) pageNotice?.text = getString(R.string.page_error)
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame && response.statusCode >= 400) pageNotice?.text = getString(R.string.http_error, response.statusCode)
                }
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    hideFullscreen()
                    if (web === view) web = null
                    (view.parent as? ViewGroup)?.removeView(view)
                    view.destroy()
                    pageProgress?.visibility = View.GONE
                    pageNotice?.text = getString(R.string.renderer_error)
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, progress: Int) {
                    pageProgress?.progress = progress
                    pageProgress?.visibility = if (progress < 100) View.VISIBLE else View.GONE
                }
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (fullscreen != null && !browserFullscreen) { callback.onCustomViewHidden(); return }
                    if (browserFullscreen) hideFullscreen()
                    showPlayerFullscreen(view, callback)
                }
                override fun onHideCustomView() { hideFullscreen() }
            }
        }
        web = browser
        frame.addView(browser, 0, FrameLayout.LayoutParams(-1, -1))
    }

    private fun loadMusic(target: String) {
        lastUrl = target
        if (web == null) createBrowser()
        web?.loadUrl(target)
    }

    private fun hostSettings() {
        val settings = column().apply { setPadding(0, 0, 0, dp(8)) }
        settings.addView(Switch(this).apply {
            text = getString(R.string.low_latency)
            setTextColor(AppUi.text); minHeight = dp(52)
            isChecked = getSharedPreferences("karaokelink-ui", MODE_PRIVATE).getBoolean("lowLatency", true)
            setOnCheckedChangeListener { _, checked ->
                getSharedPreferences("karaokelink-ui", MODE_PRIVATE).edit().putBoolean("lowLatency", checked).apply()
                repository.lowLatency(checked)
            }
        }, LinearLayout.LayoutParams(-1, -2))
        text(settings, getString(R.string.player_background_hint), 13f, muted)
        text(settings, getString(R.string.low_latency_hint), 13f, muted)
        text(settings, getString(R.string.latency_tips), 13f, muted)
        button(settings, getString(R.string.hotspot_title), false) { showHotspotSetup() }
        text(settings, getString(R.string.volume), 17f)
        slider(settings, getString(R.string.master_mic), 0f, 2f, micVolume) { micVolume = it; repository.hostMix(micVolume, delayMs) }
        slider(settings, getString(R.string.music_volume), 0f, 1f, musicVolume) { musicVolume = it; setMusicVolume(true) }
        text(settings, getString(R.string.volume_hint), 13f, muted)
        text(settings, getString(R.string.audio_devices), 17f)
        button(settings, getString(R.string.wired_output), false) { pickOutput(false) }
        button(settings, getString(R.string.bluetooth_output), false) {
            if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 11)
            else pickOutput(true)
        }
        text(settings, getString(R.string.output_hint), 13f, muted)
        val advanced = expandable(settings, getString(R.string.advanced_connection))
        slider(advanced, getString(R.string.added_delay), 0f, 500f, delayMs.toFloat()) {
            delayMs = it.roundToInt(); repository.hostMix(micVolume, delayMs)
        }
        text(advanced, getString(R.string.delay_hint), 13f, muted)
        val state = repository.state.value
        text(advanced, getString(R.string.network_stats, state.rttMs?.toString() ?: "—", state.queuedMs, state.underruns), 13f, muted)
        button(advanced, getString(R.string.restart_receiver), false) { ui.launch { repository.select(Mode.HOST) } }
        showSettingsSheet(getString(R.string.mixer), getString(R.string.mixer_hint), settings)
    }

    private fun showSettingsSheet(title: String, subtitle: String, content: View) {
        settingsDialog?.dismiss()
        val dialog = AppUi.sheet(this, title, subtitle, content)
        settingsDialog = dialog
        dialog.setOnDismissListener {
            if (settingsDialog === dialog) settingsDialog = null
        }
    }

    private fun setMusicVolume(report: Boolean = false) {
        val value = String.format(java.util.Locale.US, "%.3f", musicVolume)
        web?.evaluateJavascript("(function(){var m=document.querySelectorAll('audio,video');m.forEach(function(x){x.volume=$value;});return m.length;})()") { result ->
            if (report && result == "0") toast(getString(R.string.no_media_volume))
        }
    }

    private fun isWired(d: AudioDeviceInfo) = d.type in listOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET)
    private fun isBluetooth(d: AudioDeviceInfo) = d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
        (Build.VERSION.SDK_INT >= 31 && d.type in listOf(AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER))

    private fun pickOutput(bluetooth: Boolean) {
        if (bluetooth) AlertDialog.Builder(this).setTitle(getString(R.string.bluetooth_delay))
            .setMessage(getString(R.string.bluetooth_hint))
            .setPositiveButton(getString(R.string.select_device)) { _, _ -> outputList(true) }.setNegativeButton(getString(R.string.cancel), null).show()
        else outputList(false)
    }
    private fun outputList(bluetooth: Boolean) {
        val devices = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { if (bluetooth) isBluetooth(it) else isWired(it) }
        if (devices.isEmpty()) { toast(getString(R.string.no_devices)); return }
        AlertDialog.Builder(this).setTitle(getString(R.string.select_output))
            .setItems(devices.map { it.productName.toString() }.toTypedArray()) { _, index ->
                ui.launch { repository.outputDevice(devices[index].id) }
            }.show()
    }

    private fun confirmHome() {
        AlertDialog.Builder(this).setTitle(getString(R.string.return_home))
            .setMessage(getString(R.string.stop_warning))
            .setPositiveButton(getString(R.string.stop_home)) { _, _ -> ui.launch { repository.select(Mode.HOME) } }
            .setNegativeButton(getString(R.string.keep_playing), null).show()
    }
    @Suppress("DEPRECATION")
    private fun showPlayerFullscreen(view: View, callback: WebChromeClient.CustomViewCallback? = null, browser: Boolean = false) {
        if (fullscreen != null) return
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(currentFocus?.windowToken, 0)
        currentFocus?.clearFocus()
        (view.parent as? ViewGroup)?.removeView(view)
        browserFullscreen = browser
        fullscreenCallback = callback
        fullscreenSystemUi = window.decorView.systemUiVisibility
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.hide(WindowInsets.Type.systemBars())
            window.insetsController?.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
        fullscreen = FrameLayout(this).apply {
            tag = "player-fullscreen"; setBackgroundColor(Color.BLACK)
            addView(view, FrameLayout.LayoutParams(-1, -1))
            val tools = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                button(this, getString(R.string.lock_touch), false) { showTouchLock() }
                button(this, getString(R.string.exit_fullscreen), false) { hideFullscreen() }
            }
            addView(tools, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END))
        }.also { stage.addView(it, FrameLayout.LayoutParams(-1, -1)) }
    }

    @Suppress("DEPRECATION")
    private fun hideFullscreen() {
        val overlay = fullscreen ?: return
        fullscreen = null
        if (browserFullscreen) web?.let { browser ->
            (browser.parent as? ViewGroup)?.removeView(browser)
            playerFrame?.addView(browser, 0, FrameLayout.LayoutParams(-1, -1))
        }
        browserFullscreen = false
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = fullscreenSystemUi
        if (Build.VERSION.SDK_INT >= 30) window.insetsController?.show(WindowInsets.Type.systemBars())
        val callback = fullscreenCallback
        fullscreenCallback = null
        callback?.onCustomViewHidden()
    }

    private fun showTouchLock() {
        if (touchLock != null || repository.state.value.mode != Mode.HOST) return
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(currentFocus?.windowToken, 0)
        touchLock = FrameLayout(this).apply {
            tag = "player-touch-lock"; setBackgroundColor(Color.BLACK)
            isClickable = true; isFocusable = true
            val controls = column().apply { gravity = Gravity.CENTER; setPadding(dp(24), dp(24), dp(24), dp(24)) }
            text(controls, getString(R.string.touch_locked), 20f, accent)
            text(controls, getString(R.string.unlock_touch_hint), 15f, muted)
            button(controls, getString(R.string.unlock_touch), false) {}.apply {
                setOnLongClickListener { hideTouchLock(); true }
            }
            addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        }.also { stage.addView(it, FrameLayout.LayoutParams(-1, -1)); it.requestFocus() }
    }

    private fun hideTouchLock() {
        touchLock?.let { (it.parent as? ViewGroup)?.removeView(it) }
        touchLock = null
    }
    private fun destroyBrowser() {
        web?.let { browser ->
            (browser.parent as? ViewGroup)?.removeView(browser)
            browser.onPause(); browser.stopLoading(); browser.destroy()
        }
        web = null
    }
    private fun localIps(): String = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces()).flatMap { Collections.list(it.inetAddresses) }
            .filter { !it.isLoopbackAddress && it is java.net.Inet4Address }.joinToString { it.hostAddress ?: "" }
    }.getOrDefault("").ifBlank { getString(R.string.wifi_settings) }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row(parent: LinearLayout) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private fun card(parent: LinearLayout) = column().apply {
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = AppUi.shape(this@MainActivity, radius = 18)
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
    }
    private fun text(parent: LinearLayout, value: String, size: Float = 16f, color: Int = AppUi.text) = AppUi.label(this, value, size, color, size >= 18f).apply {
        setPadding(0, dp(6), 0, dp(6)); parent.addView(this)
    }
    private fun input(parent: LinearLayout, hintText: String) = EditText(this).apply {
        hint = hintText; contentDescription = hintText
        setTextColor(AppUi.text); setHintTextColor(muted); textSize = 15f
        background = AppUi.shape(this@MainActivity, radius = 14)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        setSingleLine(); minHeight = dp(52)
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(4) })
    }
    private fun button(parent: LinearLayout, value: String, primary: Boolean = true, weighted: Boolean = false, tint: Int = accent, action: () -> Unit): Button {
        return AppUi.action(this, value, primary, tint).apply {
            setOnClickListener { action() }
            val horizontal = parent.orientation == LinearLayout.HORIZONTAL
            parent.addView(this, LinearLayout.LayoutParams(if (weighted) 0 else if (horizontal) -2 else -1, -2,
                if (weighted) 1f else 0f).apply {
                topMargin = dp(4); bottomMargin = dp(4)
                if (horizontal) leftMargin = dp(4)
            })
        }
    }

    private fun expandable(parent: LinearLayout, title: String): LinearLayout {
        val contents = column().apply { visibility = View.GONE }
        val toggle = button(parent, "＋ $title", false) {
            contents.visibility = if (contents.visibility == View.GONE) View.VISIBLE else View.GONE
        }
        toggle.setOnClickListener {
            val expand = contents.visibility == View.GONE
            contents.visibility = if (expand) View.VISIBLE else View.GONE
            toggle.text = "${if (expand) "−" else "＋"} $title"
        }
        parent.addView(contents)
        return contents
    }
    private fun slider(parent: LinearLayout, label: String, min: Float, max: Float, initial: Float, change: (Float) -> Unit) {
        fun format(value: Float): String = when {
            label.contains("(ms)") -> "${value.roundToInt()} ms"
            label == getString(R.string.noise_gate) -> if (value <= 0f) getString(R.string.off) else "%.3f".format(value)
            else -> "${(value * 100).roundToInt()}%"
        }
        val caption = row(parent).apply { setPadding(0, dp(10), 0, 0) }
        caption.addView(AppUi.label(this, label, 14f), LinearLayout.LayoutParams(0, -2, 1f))
        val valueText = AppUi.label(this, format(initial), 13f, accent, true)
        caption.addView(valueText)
        parent.addView(SeekBar(this).apply {
            contentDescription = "$label ${format(initial)}"; this.max = 1000
            if (Build.VERSION.SDK_INT >= 30) { contentDescription = label; stateDescription = format(initial) }
            progressTintList = ColorStateList.valueOf(accent); thumbTintList = progressTintList
            progressBackgroundTintList = ColorStateList.valueOf(AppUi.border)
            progress = ((initial - min) / (max - min) * 1000).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val value = min + (max - min) * progress / 1000
                        valueText.text = format(value)
                        if (Build.VERSION.SDK_INT >= 30) stateDescription = format(value) else contentDescription = "$label ${format(value)}"
                        change(value)
                    }
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }, LinearLayout.LayoutParams(-1, dp(48)))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    @Deprecated("Legacy back dispatch for framework Activity")
    override fun onBackPressed() {
        if (!handlePlayerBack()) super.onBackPressed()
    }
    private fun handlePlayerBack(): Boolean {
        when {
            touchLock != null -> Unit
            fullscreen != null -> hideFullscreen()
            web?.canGoBack() == true -> web?.goBack()
            repository.state.value.mode != Mode.HOME -> confirmHome()
            else -> return false
        }
        return true
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyPlayerLayout()
        GettingStarted.resize(this, welcomeDialog)
        AppUi.resizeSheet(this, settingsDialog)
    }
    private fun applyPlayerLayout() {
        if (rendered != Mode.HOST) return
        val compact = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val visibility = if (compact) View.GONE else View.VISIBLE
        statusPanel?.visibility = visibility
        status.visibility = visibility
        connection.visibility = visibility
        meter.visibility = visibility
        pageNotice?.visibility = visibility
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("mode", repository.state.value.mode.name)
        outState.putString("musicUrl", lastUrl)
        super.onSaveInstanceState(outState)
    }
    override fun onStop() {
        // Active player and microphone sessions are owned by their foreground services.
        // Do not inject a pause into the website or close native sockets on screen-off.
        if (!repository.continuesInBackground) {
            hideFullscreen()
            web?.evaluateJavascript("document.querySelectorAll('audio,video').forEach(function(x){x.pause();});", null)
            web?.onPause()
            ui.launch { repository.pause() }
        }
        super.onStop()
    }
    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 33) systemBackCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        systemBackCallback = null
        welcomeDialog?.dismiss(); welcomeDialog = null
        settingsDialog?.dismiss(); settingsDialog = null
        hideTouchLock(); hideFullscreen(); destroyBrowser()
        if (repository is AndroidSessionRepository) SessionRuntime.release(repository as AndroidSessionRepository)
        else if (!repository.continuesInBackground) repository.destroy()
        hotspot.onChanged = {}
        if (!repository.continuesInBackground) hotspot.stop()
        hotspotStatus = null; hotspotButton = null; hotspotQr = null; hotspotQrHint = null
        ui.cancel(); super.onDestroy()
    }
}