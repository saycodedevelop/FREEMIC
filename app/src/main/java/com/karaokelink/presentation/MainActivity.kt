package com.karaokelink.presentation

import android.Manifest
import android.app.Activity
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
    private val accent = Color.rgb(73, 222, 187)
    private val screenColor = Color.rgb(14, 23, 38)
    private val muted = Color.rgb(171, 187, 207)
    private val audio by lazy { getSystemService(AudioManager::class.java) }

    protected open fun createRepository(): SessionRepository = AndroidSessionRepository(applicationContext)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firstMode = savedInstanceState?.getString("mode")?.let { runCatching { Mode.valueOf(it) }.getOrNull() }
        lastUrl = savedInstanceState?.getString("musicUrl") ?: PlayerNavigation.HOME
        repository = createRepository()
        ui.launch {
            repository.state.collect { state ->
                if (rendered != state.mode) render(state.mode)
                status.text = if (state.engineError) "อุปกรณ์เสียงขัดข้อง • เปิดตั้งค่าเสียงเพื่อลองเชื่อมต่อใหม่" else state.status
                connection.text = when (state.mode) {
                    Mode.HOST -> "ไมค์ ${state.peers}/2 เครื่อง  •  IP ${localIps()}  ⧉"
                    Mode.MIC -> if (state.peers > 0) "เชื่อมต่อแล้ว • พร้อมร้องเพลง" else "เลือกเครื่องเล่นจากรายการด้านล่าง"
                    Mode.HOME -> "ต่อ Wi-Fi เดียวกัน แล้วเลือกหน้าที่ของเครื่องนี้"
                }
                meter.progress = (state.vu.coerceIn(0f, 1f) * 1000).toInt()
                val signature = state.hosts.joinToString { "${it.ip}:${it.name}" }
                if (signature != hostSignature) {
                    hostSignature = signature
                    hosts?.let { list ->
                        list.removeAllViews()
                        if (state.hosts.isEmpty()) text(list, "ยังไม่พบเครื่องเล่น\nเปิดเครื่องเล่นอีกเครื่องและใช้ Wi-Fi เดียวกัน", 14f, muted)
                        state.hosts.forEach { h -> button(list, "เชื่อมต่อ ${h.name}\n${h.ip}") { chooseMic(h.ip) } }
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
        ui.launch { repository.select(mode) }
        web?.onResume()
    }

    private fun render(mode: Mode) {
        hideFullscreen()
        destroyBrowser()
        rendered = mode
        hosts = null
        hostSignature = "!"
        playerFrame = null
        pageNotice = null
        pageProgress = null
        if (mode == Mode.HOME) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stage = FrameLayout(this).apply { setBackgroundColor(screenColor) }
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
        setContentView(stage)
        stage.requestApplyInsets()
        val header = row(root)
        text(header, "KaraokeLink", 23f, accent).layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        if (mode != Mode.HOME) button(header, "หน้าแรก", false) { confirmHome() }
        status = text(root, "พร้อมใช้งาน", 13f, muted)
        connection = text(root, "", 13f, accent)
        if (mode == Mode.HOST) connection.setOnClickListener {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Host IP", localIps()))
            toast("คัดลอก IP แล้ว")
        }
        meter = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(accent)
            contentDescription = "ระดับเสียงไมโครโฟน"
            visibility = if (mode == Mode.HOME) View.GONE else View.VISIBLE
        }
        root.addView(meter, LinearLayout.LayoutParams(-1, dp(5)))
        body = column()
        if (mode == Mode.HOST) root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        else root.addView(ScrollView(this).apply { isFillViewport = true; addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        when (mode) { Mode.HOME -> home(); Mode.MIC -> mic(); Mode.HOST -> host() }
        applyPlayerLayout()
    }

    private fun home() {
        text(body, "พร้อมร้องเพลงแล้วหรือยัง?", 27f)
        text(body, "ใช้เครื่องหนึ่งเปิดเพลง อีกเครื่องเป็นไมค์\nเริ่มจากเครื่องที่ต่อลำโพงก่อน", 16f, muted)
        val player = card(body)
        text(player, "1  เครื่องเปิดเพลง", 21f)
        text(player, "ต่อเครื่องนี้เข้าลำโพง แล้วค้นหาเพลงที่ต้องการ", 15f, muted)
        button(player, "เปิดเครื่องเล่นเพลง") { startHost() }
        val microphone = card(body)
        text(microphone, "2  เครื่องไมโครโฟน", 21f)
        text(microphone, "เลือกเครื่องเล่น แล้วใช้มือถือเครื่องนี้ร้องเพลง", 15f, muted)
        button(microphone, "ใช้เครื่องนี้เป็นไมค์") { chooseMic(null) }
        text(body, "แนะนำลำโพงแบบสายหรือ USB เพื่อลดเสียงหน่วง\nแอปจะพักไมค์เมื่อสลับแอปหรือปิดหน้าจอ", 13f, muted)
    }

    private fun discovery(parent: LinearLayout) {
        text(parent, "เลือกเครื่องเล่น", 21f)
        hosts = column().also { parent.addView(it) }
        text(hosts!!, "กำลังค้นหาเครื่องเล่น…", 14f, muted)
        val manual = expandable(parent, "หาไม่เจอ? เชื่อมต่อด้วย IP")
        text(manual, "ดู IP บนหน้าเครื่องเล่น แล้วกรอกด้านล่าง", 14f, muted)
        val ip = input(manual, "เช่น 192.168.1.10").apply {
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        button(manual, "เชื่อมต่อ IP นี้") {
            val value = ip.text.toString().trim()
            val parts = value.split('.')
            if (parts.size != 4 || parts.any { part -> part.isEmpty() || part.any { !it.isDigit() } || part.toIntOrNull() !in 0..255 }) {
                ip.error = "กรุณากรอก IPv4 เช่น 192.168.1.10"
            } else chooseMic(value)
        }
    }

    private fun chooseMic(ip: String?) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingMic = true
            pendingIp = ip
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)
        } else ui.launch {
            if (repository.state.value.mode != Mode.MIC) repository.select(Mode.MIC)
            ip?.let { repository.connect(it) }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && pendingMic) {
            pendingMic = false
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) chooseMic(pendingIp)
            else toast("ต้องอนุญาตไมโครโฟนเพื่อส่งเสียง")
            pendingIp = null
        }
        if (requestCode == 11 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) pickOutput(true)
    }

    private fun mic() {
        discovery(body)
        text(body, "ระดับเสียงร้อง", 21f)
        slider(body, "ความดังไมค์", 0f, 4f, fx.gain) { fx = fx.copy(gain = it); repository.effects(fx) }
        slider(body, "เสียงก้อง", 0f, 1f, fx.echoMix) { fx = fx.copy(echoMix = it); repository.effects(fx) }
        slider(body, "เสียงสะท้อนห้อง", 0f, 1f, fx.reverb) { fx = fx.copy(reverb = it); repository.effects(fx) }
        val advanced = expandable(body, "ตั้งค่าไมค์เพิ่มเติม")
        button(advanced, "เลือกไมค์ในเครื่อง / สาย / USB", false) {
            val devices = audio.getDevices(AudioManager.GET_DEVICES_INPUTS).filter { it.type != AudioDeviceInfo.TYPE_TELEPHONY }
            val names = arrayOf("อัตโนมัติ (ระบบ Android)") + devices.map { it.productName.toString() }
            AlertDialog.Builder(this).setTitle("อุปกรณ์รับเสียง").setItems(names) { _, index ->
                repository.inputDevice(if (index == 0) 0 else devices[index - 1].id)
                toast("เลือกแล้ว มีผลเมื่อเชื่อมต่อครั้งถัดไป")
            }.show()
        }
        slider(advanced, "ลดเสียงรบกวน", 0f, 0.08f, fx.gate) { fx = fx.copy(gate = it); repository.effects(fx) }
        slider(advanced, "ระยะเสียงก้อง (ms)", 5f, 700f, fx.echoMs) { fx = fx.copy(echoMs = it); repository.effects(fx) }
        slider(advanced, "เสียงก้องซ้ำ", 0f, 0.85f, fx.feedback) { fx = fx.copy(feedback = it); repository.effects(fx) }
        slider(advanced, "เสียงทุ้ม", 0f, 2f, fx.low) { fx = fx.copy(low = it); repository.effects(fx) }
        slider(advanced, "เสียงกลาง", 0f, 2f, fx.mid) { fx = fx.copy(mid = it); repository.effects(fx) }
        slider(advanced, "เสียงแหลม", 0f, 2f, fx.high) { fx = fx.copy(high = it); repository.effects(fx) }
        text(body, "ถือไมค์ให้ห่างจากลำโพงเพื่อป้องกันเสียงหอน", 13f, muted)
    }

    private fun startHost() {
        // WebView owns media audio focus. A second request here can receive LOSS when
        // the embedded player starts; closing the session in that callback erased the player.
        val wired = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { isWired(it) }
        ui.launch { repository.outputDevice(wired?.id ?: 0); repository.select(Mode.HOST) }
    }

    private fun host() {
        val search = row(body)
        val query = input(search, "ค้นหาชื่อเพลง หรือวางลิงก์ HTTPS").apply {
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
        pageNotice = text(body, "เลือกเพลงแล้วกดเล่นในหน้าเว็บได้เลย", 12f, muted)
        playerFrame = FrameLayout(this).also { body.addView(it, LinearLayout.LayoutParams(-1, 0, 1f)) }
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
        setPadding(dp(16), dp(12), dp(16), dp(16))
        background = GradientDrawable().apply { setColor(Color.rgb(23, 35, 53)); cornerRadius = dp(20).toFloat() }
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
    }
    private fun text(parent: LinearLayout, value: String, size: Float = 16f, color: Int = Color.WHITE) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setPadding(0, dp(6), 0, dp(6))
        if (size >= 20f) setTypeface(typeface, Typeface.BOLD)
        parent.addView(this)
    }
    private fun input(parent: LinearLayout, hintText: String) = EditText(this).apply {
        hint = hintText; contentDescription = hintText
        setTextColor(Color.WHITE); setHintTextColor(muted); textSize = 15f
        setSingleLine(); minHeight = dp(48)
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private fun button(parent: LinearLayout, value: String, primary: Boolean = true, weighted: Boolean = false, action: () -> Unit): Button {
        return Button(this).apply {
            text = value; isAllCaps = false; textSize = 14f; minHeight = dp(48)
            setTextColor(if (primary) screenColor else accent)
            backgroundTintList = ColorStateList.valueOf(if (primary) accent else Color.rgb(29, 45, 64))
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { action() }
            val horizontal = parent.orientation == LinearLayout.HORIZONTAL
            parent.addView(this, LinearLayout.LayoutParams(if (weighted) 0 else if (horizontal) -2 else -1, -2,
                if (weighted) 1f else 0f).apply { topMargin = dp(4); bottomMargin = dp(4) })
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
        fun caption(value: Float) = "$label: ${if (max >= 100) value.roundToInt().toString() else "%.2f".format(value)}"
        val text = text(parent, caption(initial), 14f)
        parent.addView(SeekBar(this).apply {
            contentDescription = label; this.max = 1000
            progressTintList = ColorStateList.valueOf(accent); thumbTintList = progressTintList
            progress = ((initial - min) / (max - min) * 1000).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) { val value = min + (max - min) * progress / 1000; text.text = caption(value); change(value) }
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
    }
    private fun applyPlayerLayout() {
        if (rendered != Mode.HOST) return
        val compact = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val visibility = if (compact) View.GONE else View.VISIBLE
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
        ui.launch { repository.pause() }
        super.onStop()
    }
    override fun onDestroy() {
        hideFullscreen(); destroyBrowser()
        repository.destroy(); ui.cancel(); super.onDestroy()
    }
}