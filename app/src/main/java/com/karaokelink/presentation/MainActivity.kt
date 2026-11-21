package com.karaokelink.presentation

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.view.WindowInsets
import android.media.*
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.*
import com.karaokelink.data.AndroidSessionRepository
import com.karaokelink.domain.*
import kotlinx.coroutines.*
import java.net.NetworkInterface
import java.util.Collections
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val ui = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: AndroidSessionRepository
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var status: TextView
    private lateinit var statistics: TextView
    private lateinit var meter: ProgressBar
    private var hosts: LinearLayout? = null
    private var hostSignature = ""
    private var rendered: Mode? = null
    private var web: WebView? = null
    private var pendingIp: String? = null
    private var pendingMic = false
    private var fx = MicEffects()
    private var micVolume = 1f
    private var musicVolume = 1f
    private var delayMs = 0
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private var focus: AudioFocusRequest? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            ui.launch { repository.close() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AndroidSessionRepository(applicationContext)
        ui.launch {
            repository.state.collect { state ->
                if (rendered != state.mode) render(state.mode)
                status.text = state.status + if (state.engineError) "\nอุปกรณ์เสียงเปลี่ยน/ถูกตัด กรุณากลับ Home แล้วเชื่อมต่อใหม่" else ""
                statistics.text = "ไมค์ ${state.peers}/2  •  RTT ${state.rttMs?.let { "$it ms" } ?: "—"}\n" +
                    "คิวรับ ${state.queuedMs} ms  •  ขาดบัฟเฟอร์ ${state.underruns} ครั้ง\nRTT + คิวรับ ไม่ใช่ latency จากไมค์ถึงลำโพง"
                meter.progress = (state.vu.coerceIn(0f, 1f) * 1000).toInt()
                val signature = state.hosts.joinToString { "${it.ip}:${it.name}" }
                if (signature != hostSignature) {
                    hostSignature = signature
                    hosts?.let { list ->
                        list.removeAllViews()
                        if (state.hosts.isEmpty()) text(list, "กำลังค้นหา Host ใน Wi-Fi เดียวกัน…")
                        state.hosts.forEach { h -> button(list, "เชื่อมต่อ ${h.name} • ${h.ip}") { chooseMic(h.ip) } }
                    }
                }
            }
        }
    }
    override fun onStart() {
        super.onStart()
        ui.launch { repository.select(Mode.HOME) }
    }
    private fun render(mode: Mode) {
        rendered = mode
        web?.apply { onPause(); stopLoading(); destroy() }; web = null
        hosts = null; hostSignature = "!"
        if (mode != Mode.HOST) abandonFocus()
        if (mode == Mode.HOME) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(28))
            setBackgroundColor(Color.rgb(14, 23, 38))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(14, 23, 38))
            addView(root)
            setOnApplyWindowInsetsListener { view, insets ->
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
        }
        setContentView(scroll)
        scroll.requestApplyInsets()
        text(root, "KaraokeLink", 30f, Color.rgb(73, 222, 187))
        text(root, when(mode) { Mode.HOME -> "สองเครื่อง • แอปเดียว • Local Wi-Fi"; Mode.MIC -> "โหมดไมโครโฟน"; Mode.HOST -> "โหมดเครื่องเล่น / Host" }, 20f)
        status = text(root, "พร้อมใช้งาน")
        meter = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000; progressTintList = ColorStateList.valueOf(Color.rgb(73, 222, 187)); contentDescription = "ระดับเสียงไมโครโฟน" }
        root.addView(meter, LinearLayout.LayoutParams(-1, dp(8)))
        statistics = text(root, "", 12f, Color.LTGRAY)
        meter.visibility = if (mode == Mode.HOME) View.GONE else View.VISIBLE
        statistics.visibility = meter.visibility
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
            background = panel(Color.rgb(23, 35, 53))
        }
        root.addView(body, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        when(mode) {
            Mode.HOME -> home()
            Mode.MIC -> mic()
            Mode.HOST -> host()
        }
        if (mode != Mode.HOME) button(root, "← กลับ Home / สลับโหมด") { ui.launch { repository.select(Mode.HOME) } }
    }
    private fun home() {
        button(body, "🎙 โหมดไมโครโฟน (Mic Mode)") { chooseMic(null) }
        button(body, "▶ โหมดเครื่องเล่น (Player / Host)") { startHost() }
        text(body, "เปิด Host บนเครื่องที่ต่อลำโพง แล้วเลือกเชื่อมต่อจากเครื่องไมค์", 15f)
        discovery(body)
        text(body, "หากใช้ Hotspot ให้กรอก IP ของ Host ที่แสดงบนเครื่องเล่น\nแอปรุ่นนี้หยุดเสียงเมื่อออกจากแอปหรือปิดหน้าจอ", 14f, Color.LTGRAY)
    }
    private fun discovery(parent: LinearLayout) {
        text(parent, "เครื่องเล่นที่พบ", 18f)
        hosts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        parent.addView(hosts)
        text(hosts!!, "กำลังค้นหา Host…")
        val ip = EditText(this).apply {
            hint = "IP สำรอง เช่น 192.168.1.10"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            inputType = android.text.InputType.TYPE_CLASS_PHONE; setSingleLine(); contentDescription = "IP ของเครื่อง Host"
        }
        parent.addView(ip)
        button(parent, "เชื่อมต่อ IP นี้") {
            val value = ip.text.toString().trim()
            val parts = value.split('.')
            if (parts.size != 4 || parts.any { part -> part.isEmpty() || part.any { !it.isDigit() } || part.toIntOrNull() !in 0..255 }) {
                ip.error = "กรุณากรอก IPv4 เช่น 192.168.1.10"
            } else chooseMic(value)
        }
    }
    private fun chooseMic(ip: String?) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingMic = true; pendingIp = ip
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
            else Toast.makeText(this, "ต้องอนุญาตไมโครโฟนเพื่อส่งเสียง", Toast.LENGTH_LONG).show()
            pendingIp = null
        }
        if (requestCode == 11 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) pickOutput(true)
    }
    private fun mic() {
        discovery(body)
        button(body, "เลือกไมค์: ในเครื่อง / สาย / USB (ก่อนเชื่อมต่อ)") {
            val devices = audio.getDevices(AudioManager.GET_DEVICES_INPUTS).filter { it.type != AudioDeviceInfo.TYPE_TELEPHONY }
            val names = arrayOf("อัตโนมัติ (ระบบ Android)") + devices.map { "${it.productName} • type ${it.type}" }
            AlertDialog.Builder(this).setTitle("อุปกรณ์รับเสียง").setItems(names) { _, index ->
                repository.inputDevice(if(index == 0) 0 else devices[index-1].id)
                Toast.makeText(this,"เลือกแล้ว มีผลเมื่อเชื่อมต่อครั้งถัดไป",Toast.LENGTH_SHORT).show()
            }.show()
        }
        text(body, "เอฟเฟกต์ประมวลผลบนเครื่องไมค์", 18f)
        slider(body, "Mic gain", 0f, 4f, fx.gain) { fx=fx.copy(gain=it); repository.effects(fx) }
        slider(body, "Noise gate", 0f, 0.08f, fx.gate) { fx=fx.copy(gate=it); repository.effects(fx) }
        slider(body, "Echo delay (ms)", 5f, 700f, fx.echoMs) { fx=fx.copy(echoMs=it); repository.effects(fx) }
        slider(body, "Echo feedback", 0f, 0.85f, fx.feedback) { fx=fx.copy(feedback=it); repository.effects(fx) }
        slider(body, "Echo wet", 0f, 1f, fx.echoMix) { fx=fx.copy(echoMix=it); repository.effects(fx) }
        slider(body, "Reverb wet", 0f, 1f, fx.reverb) { fx=fx.copy(reverb=it); repository.effects(fx) }
        slider(body, "EQ low (200 Hz)", 0f, 2f, fx.low) { fx=fx.copy(low=it); repository.effects(fx) }
        slider(body, "EQ mid", 0f, 2f, fx.mid) { fx=fx.copy(mid=it); repository.effects(fx) }
        slider(body, "EQ high (3 kHz)", 0f, 2f, fx.high) { fx=fx.copy(high=it); repository.effects(fx) }
        text(body,"ใช้หูฟัง/แยกไมค์ออกจากลำโพงเพื่อป้องกันเสียงหอน",14f,Color.LTGRAY)
    }
    private fun startHost() {
        abandonFocus()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener(focusListener).build()
        if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Toast.makeText(this,"ไม่สามารถขอ audio focus ได้",Toast.LENGTH_LONG).show(); return
        }
        focus = request
        val wired = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { isWired(it) }
        ui.launch { repository.outputDevice(wired?.id ?: 0); repository.select(Mode.HOST) }
    }
    private fun isWired(d: AudioDeviceInfo) = d.type in listOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET)
    private fun isBluetooth(d: AudioDeviceInfo) = d.type in listOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER)
    private fun host() {
        text(body, "IP เครื่องนี้: ${localIps()}", 14f, Color.rgb(73,222,187))
        val hasWired = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { isWired(it) }
        text(body, if (hasWired) "เลือกสาย / USB เป็นค่าเริ่มต้นสำหรับเสียงไมค์" else "ไม่พบสาย / USB: ระบบใช้อุปกรณ์เริ่มต้น กรุณาเสียบสายแล้วเลือกขาออก",14f)
        button(body, "เลือกขาออกแบบสาย / USB") { pickOutput(false) }
        button(body, "เลือก Bluetooth (มีดีเลย์)") {
            if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT),11)
            else pickOutput(true)
        }
        text(body,"ขาออกที่เลือกมีผลกับ Oboe; เสียง WebView ใช้ media route ของระบบ\nตั้งเส้นทางสื่อของ Android ให้ตรงกันด้วย",13f,Color.LTGRAY)
        slider(body,"ระดับเสียงไมค์รวม",0f,2f,micVolume) { micVolume=it; repository.hostMix(micVolume,delayMs) }
        slider(body,"Audio delay เพิ่มฝั่งไมค์ (ms)",0f,500f,delayMs.toFloat()) {
            delayMs=it.roundToInt(); repository.hostMix(micVolume,delayMs)
        }
        text(body,"เพิ่ม delay ได้เท่านั้น ไม่สามารถลดดีเลย์ Bluetooth หรือแก้การได้ยินเสียงตัวเองช้าได้",13f,Color.LTGRAY)
        text(body,"คาราโอเกะผ่าน WebView",18f)
        val url = EditText(this).apply {
            setText("https://www.youtube.com/"); setTextColor(Color.WHITE); setSingleLine()
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        body.addView(url)
        val browser = WebView(this).apply {
            settings.javaScriptEnabled=true
            settings.domStorageEnabled=true
            settings.allowFileAccess=false
            settings.allowContentAccess=false
            settings.mediaPlaybackRequiresUserGesture=true
            settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient=object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = request.url.scheme != "https"
                override fun onPageFinished(view: WebView, url: String) { setMusicVolume() }
            }
        }
        web=browser
        button(body,"เปิด URL (HTTPS)") {
            val value=url.text.toString().trim()
            val parsed=android.net.Uri.parse(value)
            if (parsed.scheme=="https" && !parsed.host.isNullOrBlank()) browser.loadUrl(value)
            else Toast.makeText(this,"กรุณาใช้ URL แบบ HTTPS",Toast.LENGTH_SHORT).show()
        }
        slider(body,"ระดับเพลง HTML5 (เฉพาะหน้าที่รองรับ)",0f,1f,musicVolume) { musicVolume=it; setMusicVolume(true) }
        text(body,"เพลงและเสียงไมค์รวมกันที่ Android system mixer ในรุ่นนี้\nการปรับเพลงใช้ได้เฉพาะ audio/video ที่เข้าถึงได้ในหน้าเว็บ ไม่รวม iframe ต่างโดเมนหรือ DRM",13f,Color.LTGRAY)
        body.addView(browser, LinearLayout.LayoutParams(-1, (420*resources.displayMetrics.density).toInt()))
    }
    private fun setMusicVolume(report: Boolean = false) {
        val value=String.format(java.util.Locale.US,"%.3f",musicVolume)
        web?.evaluateJavascript("(function(){var m=document.querySelectorAll('audio,video');m.forEach(function(x){x.volume=$value;});return m.length;})()") { result ->
            if(report && result=="0") Toast.makeText(this,"หน้านี้ยังไม่มี HTML5 media ที่ปรับระดับเสียงได้",Toast.LENGTH_SHORT).show()
        }
    }
    private fun pickOutput(bluetooth: Boolean) {
        if(bluetooth) {
            AlertDialog.Builder(this).setTitle("Bluetooth มีความหน่วงเพิ่มเติม")
                .setMessage("เหมาะเป็นทางเลือกสำรอง การเพิ่ม delay ช่วยจัดเวลาได้บางกรณี แต่ลดความหน่วงของ Bluetooth ไม่ได้")
                .setPositiveButton("เลือกอุปกรณ์") { _, _ -> outputList(true) }.setNegativeButton("ยกเลิก",null).show()
        } else outputList(false)
    }
    private fun outputList(bluetooth: Boolean) {
        val devices=audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { if(bluetooth) isBluetooth(it) else isWired(it) }
        if(devices.isEmpty()) { Toast.makeText(this,"ไม่พบอุปกรณ์ กรุณาเชื่อมต่อจากการตั้งค่า Android ก่อน",Toast.LENGTH_LONG).show(); return }
        AlertDialog.Builder(this).setTitle("เลือกขาออกเสียงไมค์ (ไมค์ต้องเชื่อมต่อใหม่)")
            .setItems(devices.map { it.productName.toString() }.toTypedArray()) { _, index ->
                ui.launch { repository.outputDevice(devices[index].id) }
            }.show()
    }
    private fun localIps(): String = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces()).flatMap { Collections.list(it.inetAddresses) }
            .filter { !it.isLoopbackAddress && it is java.net.Inet4Address }.joinToString { it.hostAddress ?: "" }
    }.getOrDefault("").ifBlank { "ดูในการตั้งค่า Wi-Fi" }
    private fun text(parent: LinearLayout, value: String, size: Float=16f, color: Int=Color.WHITE): TextView =
        TextView(this).apply { text=value; textSize=size; setTextColor(color); setPadding(0,dp(8),0,dp(8)); if (size >= 18f) setTypeface(typeface, Typeface.BOLD); parent.addView(this) }
    private fun button(parent: LinearLayout, value: String, action: () -> Unit) {
        parent.addView(Button(this).apply {
            text=value; isAllCaps=false; textSize=15f
            setTextColor(Color.rgb(14, 23, 38))
            backgroundTintList=ColorStateList.valueOf(Color.rgb(73, 222, 187))
            minHeight=dp(52)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(6); bottomMargin=dp(6) })
    }
    private fun slider(parent: LinearLayout,label: String,min: Float,max: Float,initial: Float,change: (Float)->Unit) {
        val caption=text(parent,"$label: ${"%.3f".format(initial)}",14f)
        val seek=SeekBar(this).apply { contentDescription=label; progressTintList=ColorStateList.valueOf(Color.rgb(73, 222, 187)); thumbTintList=progressTintList; this.max=1000; progress=((initial-min)/(max-min)*1000).toInt() }
        seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?,progress: Int,fromUser: Boolean) {
                if(fromUser) { val value=min+(max-min)*progress/1000; caption.text="$label: ${"%.3f".format(value)}"; change(value) }
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        parent.addView(seek)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun panel(color: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius=dp(20).toFloat()
    }
    private fun abandonFocus() { focus?.let { audio.abandonAudioFocusRequest(it) }; focus=null }
    @Deprecated("Legacy back dispatch for framework Activity")
    override fun onBackPressed() {
        if(repository.state.value.mode != Mode.HOME) ui.launch { repository.select(Mode.HOME) }
        else super.onBackPressed()
    }
    override fun onStop() {
        web?.onPause(); web?.stopLoading()
        ui.launch { repository.close() }
        abandonFocus()
        super.onStop()
    }
    override fun onDestroy() {
        web?.destroy(); web=null
        repository.destroy(); ui.cancel(); super.onDestroy()
    }
}