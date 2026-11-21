package com.karaokelink.presentation

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.text.TextUtils
import com.karaokelink.R
import android.app.AlertDialog
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
    private var firstMode: Mode? = null
    private var lastUrl = PlayerNavigation.HOME
    private var pendingIp: String? = null
    private var pendingMic = false
    private var fx = MicEffects()
    private var micVolume = 1f
    private var musicVolume = 1f
    private var delayMs = 0
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firstMode = savedInstanceState?.getString("mode")?.let { runCatching { Mode.valueOf(it) }.getOrNull() }
        lastUrl = savedInstanceState?.getString("musicUrl") ?: PlayerNavigation.HOME
        repository = createRepository()
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
        hideFullscreen(); destroyBrowser()
        rendered = mode
        hosts = null; hostSignature = "!"
        playerFrame = null; pageNotice = null; pageProgress = null
        statusPanel = null; micConnectSection = null; micControlPanel = null
        micLiveTitle = null; micLiveHint = null; micStopButton = null; changePlayerButton = null
        discoveryExpanded = false
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
            Mode.HOME -> "ร้องเพลงง่ายขึ้น ด้วยมือถือของคุณ"
            Mode.HOST -> "เครื่องเล่นเพลง"
            Mode.MIC -> "ไมโครโฟน"
        }, 12f, muted).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        if (mode != Mode.HOME) button(header, "หน้าแรก", false) { confirmHome() }
        header.addView(AppUi.iconButton(this, R.drawable.ic_help, "วิธีใช้งาน") { showGettingStarted() },
            LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(8) })
        statusPanel = column().apply {
            background = AppUi.shape(this@MainActivity, radius = 16)
            setPadding(dp(12), dp(9), dp(12), dp(9)); tag = "session-status"
            visibility = if (mode == Mode.HOME) View.GONE else View.VISIBLE
        }.also { root.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(8) }) }
        status = AppUi.label(this, "", 14f, accent, true).also { statusPanel!!.addView(it) }
        connection = AppUi.label(this, "", 12f, muted).apply {
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(3), 0, 0)
        }.also { statusPanel!!.addView(it) }
        if (mode == Mode.HOST) {
            statusPanel!!.contentDescription = "สถานะเครื่องเล่น แตะดูวิธีเชื่อมต่อไมค์"
            statusPanel!!.setOnClickListener { showHostConnection() }
        }
        meter = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; progressTintList = ColorStateList.valueOf(accent)
            progressBackgroundTintList = ColorStateList.valueOf(AppUi.border)
            contentDescription = "ระดับเสียงไมโครโฟน"
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
        val copy = SessionPresenter.describe(state, repository.continuesInBackground)
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
            micLiveTitle?.text = if (live) "พร้อมร้องเพลง" else "รอการเชื่อมต่อ"
            micLiveHint?.text = if (live && repository.continuesInBackground) "ปิดหน้าจอได้ • เสียงไมค์ยังทำงาน" else if (live) "พูดใกล้ไมค์ แล้วดูระดับเสียงด้านล่าง" else "เลือกเครื่องเล่นด้านบน แล้วเริ่มร้องได้เลย"
            micControlPanel?.visibility = if (live) View.VISIBLE else View.GONE
            micConnectSection?.visibility = if (!live || discoveryExpanded) View.VISIBLE else View.GONE
            micStopButton?.visibility = if (live || repository.continuesInBackground) View.VISIBLE else View.GONE
            micStopButton?.text = if (live) "หยุดส่งเสียงไมค์" else "ยกเลิกการเชื่อมต่อ"
            changePlayerButton?.visibility = if (live) View.VISIBLE else View.GONE
            changePlayerButton?.text = if (discoveryExpanded) "ซ่อนรายการเครื่องเล่น" else "เปลี่ยนเครื่องเล่น"
        }
    }

    private fun home() {
        text(body, "เลือกหน้าที่ของเครื่องนี้", 27f)
        text(body, "เครื่องหนึ่งเปิดเพลง อีกเครื่องใช้ร้อง\nเริ่มจากเครื่องที่ต่อลำโพงก่อน", 15f, muted)
        val wifi = row(body).apply { setPadding(0, dp(6), 0, dp(4)) }
        wifi.addView(AppUi.icon(this, R.drawable.ic_wifi, muted, 20))
        AppUi.label(this, "ต่อมือถือทั้งสองเครื่องกับ Wi-Fi เดียวกัน", 12f, muted).also {
            wifi.addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
        }
        fun role(title: String, subtitle: String, step: String, icon: Int, tint: Int, actionLabel: String, action: () -> Unit) {
            val panel = card(body).apply { background = AppUi.shape(this@MainActivity, stroke = Color.argb(115, Color.red(tint), Color.green(tint), Color.blue(tint))) }
            val line = row(panel)
            val tile = FrameLayout(this).apply { background = AppUi.shape(this@MainActivity, Color.argb(25, Color.red(tint), Color.green(tint), Color.blue(tint)), 14, null) }
            tile.addView(AppUi.icon(this, icon, tint, 24), FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
            line.addView(tile, LinearLayout.LayoutParams(dp(46), dp(46)).apply { rightMargin = dp(12) })
            val words = column()
            words.addView(AppUi.label(this, step, 11f, tint, true))
            words.addView(AppUi.label(this, title, 19f, bold = true).apply { setPadding(0, dp(3), 0, 0) })
            line.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
            text(panel, subtitle, 13f, muted)
            button(panel, actionLabel, tint = tint, action = action)
        }
        role("เครื่องเล่นเพลง", "ต่อลำโพง เปิดเพลง และรับเสียงจากไมค์", "เครื่องที่ 1 • เริ่มที่นี่", R.drawable.ic_music, accent, "เปิดเครื่องเล่นเพลง") { startHost() }
        role("เครื่องไมโครโฟน", "ใช้มือถืออีกเครื่องถือร้อง พร้อมปิดจอได้", "เครื่องที่ 2 • ใช้ร้องเพลง", R.drawable.ic_mic, AppUi.violet, "ใช้เครื่องนี้เป็นไมค์") { chooseMic(null) }
        button(body, "ดูวิธีเริ่มใช้งาน 3 ขั้นตอน", false) { showGettingStarted() }
        text(body, "สายหรือ USB เหมาะกับการร้องสด\nBluetooth อาจมีเสียงหน่วงตามอุปกรณ์", 12f, muted)
    }

    private fun discovery(parent: LinearLayout) {
        val section = card(parent)
        micConnectSection = section
        section.tag = "mic-discovery"
        text(section, "1  เลือกเครื่องเล่น", 19f)
        text(section, "เปิดเครื่องเล่นบนมือถืออีกเครื่องก่อน", 13f, muted)
        hosts = column().also { section.addView(it) }
        val manual = expandable(section, "เชื่อมต่อด้วย IP")
        text(manual, "ดู IP โดยแตะสถานะบนหน้าเครื่องเล่น", 13f, muted)
        val ip = input(manual, "เช่น 192.168.1.10").apply { inputType = android.text.InputType.TYPE_CLASS_PHONE }
        button(manual, "เชื่อมต่อ IP นี้") {
            val value = ip.text.toString().trim()
            val parts = value.split('.')
            if (parts.size != 4 || parts.any { part -> part.isEmpty() || part.any { !it.isDigit() } || part.toIntOrNull() !in 0..255 }) ip.error = "กรอก IP ให้ครบ เช่น 192.168.1.10"
            else chooseMic(value)
        }
    }

    private fun renderHosts(list: LinearLayout, found: List<Host>) {
        if (found.isEmpty()) {
            val empty = column().apply { setPadding(dp(12), dp(12), dp(12), dp(8)); background = AppUi.shape(this@MainActivity, screenColor, 14) }
            list.addView(empty)
            text(empty, "ยังไม่พบเครื่องเล่น", 16f)
            text(empty, "ตรวจว่าทั้งสองเครื่องใช้ Wi-Fi เดียวกัน\nแล้วเปิดหน้าเครื่องเล่นค้างไว้ แอปค้นหาให้อัตโนมัติ", 13f, muted)
            button(empty, "ค้นหาอีกครั้ง", false) { ui.launch { repository.select(Mode.MIC) } }
            button(empty, "ช่วยเชื่อมต่อ", false) { showConnectionHelp() }
        }
        found.forEach { host ->
            val entry = column().apply { setPadding(dp(10), dp(10), dp(10), dp(8)); background = AppUi.shape(this@MainActivity, screenColor, 14) }
            list.addView(entry, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            text(entry, host.name, 16f)
            text(entry, host.ip, 12f, muted)
            button(entry, "เชื่อมต่อ ${host.name}", tint = AppUi.violet) { chooseMic(host.ip) }
        }
    }

    private fun showConnectionHelp() {
        AlertDialog.Builder(this).setTitle("หาเครื่องเล่นไม่เจอ?")
            .setMessage("1. เปิด «เครื่องเล่นเพลง» บนมือถือที่ต่อลำโพง\n\n2. ต่อมือถือทั้งสองเครื่องกับ Wi-Fi เดียวกัน\n\n3. ถ้ายังไม่พบ แตะสถานะเครื่องเล่นเพื่อดู IP แล้วเลือก «เชื่อมต่อด้วย IP» บนเครื่องไมค์\n\nWi-Fi สำหรับแขกหรือ Hotspot บางรุ่นอาจไม่อนุญาตให้อุปกรณ์ติดต่อกัน")
            .setPositiveButton("เข้าใจแล้ว", null).show()
    }

    private fun showHostConnection() {
        val content = column().apply { setPadding(dp(20), dp(12), dp(20), dp(16)) }
        text(content, "เชื่อมต่อไมค์จากอีกเครื่อง", 21f)
        text(content, "1. ใช้ Wi-Fi เดียวกัน\n2. บนอีกเครื่อง แตะ «ใช้เครื่องนี้เป็นไมค์»\n3. เลือกชื่อเครื่องเล่นนี้จากรายการ", 15f, muted)
        text(content, "ถ้าค้นหาไม่พบ ใช้ IP นี้", 13f, muted)
        text(content, localIps(), 20f, accent).apply { setTextIsSelectable(true) }
        button(content, "คัดลอก IP", false) {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Host IP", localIps()))
            toast("คัดลอก IP แล้ว")
        }
        AlertDialog.Builder(this).setView(content).setPositiveButton("เสร็จแล้ว", null).show()
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
            else toast("ต้องอนุญาตไมโครโฟนเพื่อส่งเสียง")
        }
        if (requestCode == 12 && pendingMic) {
            pendingMic = false
            chooseMic(pendingIp)
            pendingIp = null
        }
        if (requestCode == 11 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) pickOutput(true)
    }

    private fun mic() {
        discovery(body)
        val live = card(body)
        val hero = row(live)
        hero.addView(AppUi.icon(this, R.drawable.ic_mic, AppUi.violet, 34), LinearLayout.LayoutParams(dp(34), dp(42)).apply { rightMargin = dp(12) })
        val titles = column()
        micLiveTitle = AppUi.label(this, "รอการเชื่อมต่อ", 21f, bold = true).also { titles.addView(it) }
        micLiveHint = AppUi.label(this, "เลือกเครื่องเล่นก่อนเริ่มร้อง", 12f, muted).also { titles.addView(it) }
        hero.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        text(live, "ระดับเสียงจากไมค์", 12f, muted)
        meter.progressTintList = ColorStateList.valueOf(AppUi.violet)
        live.addView(meter, LinearLayout.LayoutParams(-1, dp(12)).apply { topMargin = dp(4); bottomMargin = dp(12) })
        micStopButton = button(live, "หยุดส่งเสียงไมค์", false, tint = AppUi.danger) { ui.launch { repository.select(Mode.MIC) } }
        changePlayerButton = button(live, "เปลี่ยนเครื่องเล่น", false) {
            discoveryExpanded = !discoveryExpanded
            updateSessionUi(repository.state.value)
        }
        micControlPanel = card(body).also { controls ->
            controls.tag = "mic-controls"
            text(controls, "2  ปรับเสียงร้อง", 19f)
            text(controls, "เริ่มจากความดัง แล้วค่อยเพิ่มเสียงก้อง", 13f, muted)
            slider(controls, "ความดังไมค์", 0f, 4f, fx.gain) { fx = fx.copy(gain = it); repository.effects(fx) }
            slider(controls, "เสียงก้อง", 0f, 1f, fx.echoMix) { fx = fx.copy(echoMix = it); repository.effects(fx) }
            slider(controls, "เสียงสะท้อนห้อง", 0f, 1f, fx.reverb) { fx = fx.copy(reverb = it); repository.effects(fx) }
        }
        button(body, "ตั้งค่าไมค์และอุปกรณ์", false) { micSettings() }
        text(body, "ถือไมค์ให้ห่างจากลำโพงเพื่อป้องกันเสียงหอน", 12f, muted)
    }

    private fun micSettings() {
        val settings = column().apply { setPadding(dp(20), dp(12), dp(20), dp(16)); setBackgroundColor(screenColor) }
        button(settings, "เลือกไมค์ในเครื่อง / สาย / USB", false) {
            val devices = audio.getDevices(AudioManager.GET_DEVICES_INPUTS).filter { it.type != AudioDeviceInfo.TYPE_TELEPHONY }
            val names = arrayOf("อัตโนมัติ (ระบบ Android)") + devices.map { it.productName.toString() }
            AlertDialog.Builder(this).setTitle("อุปกรณ์รับเสียง").setItems(names) { _, index ->
                repository.inputDevice(if (index == 0) 0 else devices[index - 1].id)
                toast("เลือกแล้ว มีผลเมื่อเชื่อมต่อครั้งถัดไป")
            }.show()
        }
        text(settings, "เลือกอุปกรณ์ก่อนเชื่อมต่อเครื่องเล่น", 13f, muted)
        val advanced = expandable(settings, "ปรับแต่งเสียงเพิ่มเติม")
        slider(advanced, "ลดเสียงรบกวน (0 = ปิด)", 0f, 0.08f, fx.gate) { fx = fx.copy(gate = it); repository.effects(fx) }
        slider(advanced, "ระยะเสียงก้อง (ms)", 5f, 700f, fx.echoMs) { fx = fx.copy(echoMs = it); repository.effects(fx) }
        slider(advanced, "เสียงก้องซ้ำ", 0f, 0.85f, fx.feedback) { fx = fx.copy(feedback = it); repository.effects(fx) }
        slider(advanced, "เสียงทุ้ม", 0f, 2f, fx.low) { fx = fx.copy(low = it); repository.effects(fx) }
        slider(advanced, "เสียงกลาง", 0f, 2f, fx.mid) { fx = fx.copy(mid = it); repository.effects(fx) }
        slider(advanced, "เสียงแหลม", 0f, 2f, fx.high) { fx = fx.copy(high = it); repository.effects(fx) }
        AlertDialog.Builder(this).setTitle("ตั้งค่าไมโครโฟน").setView(ScrollView(this).apply { addView(settings) })
            .setPositiveButton("เสร็จแล้ว", null).show()
    }
    private fun startHost() {
        // WebView owns media audio focus. A second request here can receive LOSS when
        // the embedded player starts; closing the session in that callback erased the player.
        val wired = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { isWired(it) }
        ui.launch { repository.outputDevice(wired?.id ?: 0); repository.select(Mode.HOST) }
    }

    private fun host() {
        val search = row(body)
        val query = input(search, "ชื่อเพลง หรือวางลิงก์เพลง").apply {
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        fun openSong() {
            val target = PlayerNavigation.resolve(query.text.toString())
            if (target == null) { query.error = "ใช้ลิงก์ HTTPS หรือพิมพ์ชื่อเพลง"; return }
            getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(query.windowToken, 0)
            query.clearFocus()
            loadMusic(target)
        }
        query.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) { openSong(); true } else false
        }
        button(search, "ค้นหา") { openSong() }
        val tools = row(body)
        button(tools, "YouTube", false, true) { loadMusic(PlayerNavigation.HOME) }
        button(tools, "โหลดใหม่", false, true) { loadMusic(lastUrl) }
        button(tools, "ตั้งค่าเสียง", false, true) { hostSettings() }
        pageProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progressTintList = ColorStateList.valueOf(accent)
            visibility = View.GONE
        }.also { body.addView(it, LinearLayout.LayoutParams(-1, dp(3))) }
        pageNotice = text(body, "เลือกเพลงแล้วกดเล่นในหน้าเว็บได้เลย", 12f, muted).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        playerFrame = FrameLayout(this).apply {
            background = AppUi.shape(this@MainActivity, Color.WHITE, 18, null)
            clipToOutline = true
            tag = "player-viewport"
        }.also { body.addView(it, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8); bottomMargin = dp(4) }) }
        createBrowser()
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
                    if (blocked && request.isForMainFrame) toast("เปิดลิงก์ HTTPS ภายในแอปได้ กรุณาเลือกเพลงในหน้านี้")
                    return blocked
                }
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    pageNotice?.text = "กำลังเปิดเพลง…"
                }
                override fun onPageFinished(view: WebView, url: String) {
                    if (Uri.parse(url).scheme == "https") lastUrl = url
                    if (pageNotice?.text == "กำลังเปิดเพลง…") pageNotice?.text = "กดเล่นที่วิดีโอ • ปุ่มย้อนกลับใช้กลับหน้าเพลงก่อนหน้า"
                    setMusicVolume()
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) pageNotice?.text = "เปิดหน้าเพลงไม่ได้ ตรวจอินเทอร์เน็ตแล้วกดโหลดใหม่"
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame && response.statusCode >= 400) pageNotice?.text = "เว็บไซต์ไม่พร้อมใช้งาน (${response.statusCode}) • ลองโหลดใหม่หรือเลือกเว็บอื่น"
                }
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    hideFullscreen()
                    if (web === view) web = null
                    (view.parent as? ViewGroup)?.removeView(view)
                    view.destroy()
                    pageProgress?.visibility = View.GONE
                    pageNotice?.text = "ตัวเล่นหยุดทำงาน • กดโหลดใหม่เพื่อเปิดเพลงอีกครั้ง"
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, progress: Int) {
                    pageProgress?.progress = progress
                    pageProgress?.visibility = if (progress < 100) View.VISIBLE else View.GONE
                }
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (fullscreen != null) { callback.onCustomViewHidden(); return }
                    fullscreenCallback = callback
                    fullscreen = FrameLayout(this@MainActivity).apply {
                        setBackgroundColor(Color.BLACK)
                        addView(view, FrameLayout.LayoutParams(-1, -1))
                        addView(Button(this@MainActivity).apply {
                            text = "ย่อวิดีโอ"; isAllCaps = false
                            setOnClickListener { hideFullscreen() }
                        }, FrameLayout.LayoutParams(-2, dp(48), Gravity.TOP or Gravity.END))
                    }.also { stage.addView(it, FrameLayout.LayoutParams(-1, -1)) }
                }
                override fun onHideCustomView() { hideFullscreen() }
            }
        }
        web = browser
        frame.addView(browser, FrameLayout.LayoutParams(-1, -1))
    }

    private fun loadMusic(target: String) {
        lastUrl = target
        if (web == null) createBrowser()
        web?.loadUrl(target)
    }

    private fun hostSettings() {
        val settings = column().apply { setPadding(dp(20), dp(12), dp(20), dp(20)); setBackgroundColor(screenColor) }
        val scroll = ScrollView(this).apply { addView(settings) }
        slider(settings, "ความดังไมค์รวม", 0f, 2f, micVolume) { micVolume = it; repository.hostMix(micVolume, delayMs) }
        slider(settings, "ความดังเพลง (เว็บที่รองรับ)", 0f, 1f, musicVolume) { musicVolume = it; setMusicVolume(true) }
        text(settings, "ถ้าปรับเพลงไม่ได้ ให้ใช้ปุ่มเสียงของเว็บไซต์", 13f, muted)
        button(settings, "เลือกขาออกสาย / USB", false) { pickOutput(false) }
        button(settings, "เลือก Bluetooth", false) {
            if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 11)
            else pickOutput(true)
        }
        text(settings, "เลือกลำโพงในเมนูเสียงสื่อของ Android ให้ตรงกันด้วย\nแนะนำสาย / USB; Bluetooth อาจได้ยินเสียงร้องช้า", 13f, muted)
        val advanced = expandable(settings, "ตั้งค่าเพิ่มเติม / ข้อมูลเชื่อมต่อ")
        slider(advanced, "เพิ่มดีเลย์ไมค์ (ms)", 0f, 500f, delayMs.toFloat()) {
            delayMs = it.roundToInt(); repository.hostMix(micVolume, delayMs)
        }
        text(advanced, "เพิ่มดีเลย์ได้เท่านั้น ไม่สามารถลดความหน่วงของ Bluetooth", 13f, muted)
        val state = repository.state.value
        text(advanced, "RTT ${state.rttMs ?: "—"} ms • คิว ${state.queuedMs} ms\nขาดบัฟเฟอร์ ${state.underruns} ครั้ง\nค่านี้ไม่ใช่เวลาเสียงจากไมค์ถึงลำโพง", 13f, muted)
        button(advanced, "เริ่มรับไมค์ใหม่", false) { ui.launch { repository.select(Mode.HOST) } }
        AlertDialog.Builder(this).setTitle("ตั้งค่าเสียง").setView(scroll).setPositiveButton("เสร็จแล้ว", null).show()
    }

    private fun setMusicVolume(report: Boolean = false) {
        val value = String.format(java.util.Locale.US, "%.3f", musicVolume)
        web?.evaluateJavascript("(function(){var m=document.querySelectorAll('audio,video');m.forEach(function(x){x.volume=$value;});return m.length;})()") { result ->
            if (report && result == "0") toast("หน้านี้ยังไม่มีเพลงที่ปรับระดับเสียงได้ ใช้ปุ่มเสียงของเว็บไซต์")
        }
    }

    private fun isWired(d: AudioDeviceInfo) = d.type in listOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET)
    private fun isBluetooth(d: AudioDeviceInfo) = d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
        (Build.VERSION.SDK_INT >= 31 && d.type in listOf(AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER))

    private fun pickOutput(bluetooth: Boolean) {
        if (bluetooth) AlertDialog.Builder(this).setTitle("Bluetooth อาจมีเสียงหน่วง")
            .setMessage("ถ้าได้ยินเสียงร้องช้า แนะนำเปลี่ยนเป็นสายหรือ USB")
            .setPositiveButton("เลือกอุปกรณ์") { _, _ -> outputList(true) }.setNegativeButton("ยกเลิก", null).show()
        else outputList(false)
    }
    private fun outputList(bluetooth: Boolean) {
        val devices = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { if (bluetooth) isBluetooth(it) else isWired(it) }
        if (devices.isEmpty()) { toast("ไม่พบอุปกรณ์ กรุณาเชื่อมต่อจากการตั้งค่า Android ก่อน"); return }
        AlertDialog.Builder(this).setTitle("เลือกขาออก (เครื่องไมค์ต้องเชื่อมต่อใหม่)")
            .setItems(devices.map { it.productName.toString() }.toTypedArray()) { _, index ->
                ui.launch { repository.outputDevice(devices[index].id) }
            }.show()
    }

    private fun confirmHome() {
        AlertDialog.Builder(this).setTitle("กลับหน้าแรก?")
            .setMessage("เพลงและการเชื่อมต่อไมค์จะหยุด")
            .setPositiveButton("หยุดและกลับหน้าแรก") { _, _ -> ui.launch { repository.select(Mode.HOME) } }
            .setNegativeButton("เล่นต่อ", null).show()
    }
    private fun hideFullscreen() {
        fullscreen?.let { (it.parent as? ViewGroup)?.removeView(it) }
        fullscreen = null
        val callback = fullscreenCallback
        fullscreenCallback = null
        callback?.onCustomViewHidden()
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
    }.getOrDefault("").ifBlank { "ดูในการตั้งค่า Wi-Fi" }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row(parent: LinearLayout) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private fun card(parent: LinearLayout) = column().apply {
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = AppUi.shape(this@MainActivity, radius = 22)
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
            label.contains("ลดเสียงรบกวน") -> if (value <= 0f) "ปิด" else "%.3f".format(value)
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
        when {
            fullscreen != null -> hideFullscreen()
            web?.canGoBack() == true -> web?.goBack()
            repository.state.value.mode != Mode.HOME -> confirmHome()
            else -> super.onBackPressed()
        }
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyPlayerLayout()
        GettingStarted.resize(this, welcomeDialog)
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
        hideFullscreen()
        web?.evaluateJavascript("document.querySelectorAll('audio,video').forEach(function(x){x.pause();});", null)
        web?.onPause()
        // Stop capture and sockets in background while retaining the screen and browser.
        if (!repository.continuesInBackground) ui.launch { repository.pause() }
        super.onStop()
    }
    override fun onDestroy() {
        welcomeDialog?.dismiss(); welcomeDialog = null
        hideFullscreen(); destroyBrowser()
        if (repository is AndroidSessionRepository) SessionRuntime.release(repository as AndroidSessionRepository)
        else if (!repository.continuesInBackground) repository.destroy()
        ui.cancel(); super.onDestroy()
    }
}