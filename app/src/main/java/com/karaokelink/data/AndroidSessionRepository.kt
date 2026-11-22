package com.karaokelink.data

import android.content.Context
import com.karaokelink.service.MicrophoneService
import android.net.wifi.WifiManager
import android.os.SystemClock
import com.karaokelink.core.audio.NativeAudio
import com.karaokelink.core.network.LanTransport
import com.karaokelink.core.network.HostSessions
import com.karaokelink.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.Inet4Address
import java.net.InetAddress
import java.security.SecureRandom

/** Session transitions, transport events and JNI lifecycle all run on Main. */
class AndroidSessionRepository(context: Context) : SessionRepository {
    private val appContext = context.applicationContext
    private var backgroundRequested = false
    override val continuesInBackground: Boolean get() = backgroundRequested
    private fun stopBackgroundMicrophone() {
        if (backgroundRequested) { backgroundRequested = false; MicrophoneService.stop(appContext) }
    }
    private val mutable = MutableStateFlow(SessionState())
    override val state = mutable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val native = NativeAudio()
    private val lan = LanTransport(appContext)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private var lock: WifiManager.MulticastLock? = null
    private var timer: Job? = null
    @Volatile private var generation = 0
    private var input = 0
    private var output = 0
    private var target: InetAddress? = null
    private var session = 0
    private var nonce = ""
    private var lastReply = 0L
    private var lastConnect = 0L
    private var pendingPing = ""
    private val peers = HostSessions(::now, native::allowPeer, native::removePeer) { message, ip, port -> lan.command(message, ip, port) }
    private val random = SecureRandom()

    init {
        lan.onHost = { ip, name ->
            val epoch = generation
            scope.launch {
                if (epoch == generation && state.value.mode != Mode.HOST) mutable.update { old ->
                    old.copy(hosts = (old.hosts.filterNot { it.ip == ip } + Host(ip, name, now())).sortedBy { it.name })
                }
            }
        }
        lan.onControl = { message, ip, port ->
            val epoch = generation
            scope.launch { if (epoch == generation) control(message, ip, port) }
        }
        lan.onError = { error -> scope.launch { mutable.update { it.copy(status = "เครือข่าย: $error") } } }
    }
    private fun now() = SystemClock.elapsedRealtime()
    override suspend fun select(mode: Mode) {
        reset()
        mutable.value = SessionState(mode = mode, status = if (mode == Mode.HOST) "กำลังเปิด Host" else "กำลังค้นหา Host ใน Wi-Fi")
        if (mode == Mode.HOME) { mutable.value = SessionState(); return }
        try {
            lock = wifi.createMulticastLock("KaraokeLink.discovery").apply { setReferenceCounted(false); acquire() }
            if (mode == Mode.HOST) check(native.startHost(output)) { "เปิด audio output ไม่สำเร็จ" }
            lan.start(mode == Mode.HOST)
            mutable.update { it.copy(status = if (mode == Mode.HOST) "Host พร้อมรับไมค์ 2 เครื่อง • UDP 40102" else "เลือก Host หรือกรอก IPv4") }
            timer = scope.launch {
                while (isActive) { tick(); delay(500) }
            }
        } catch (e: Exception) {
            reset()
            mutable.value = SessionState(mode = mode, status = e.message ?: "เริ่มโหมดไม่สำเร็จ")
        }
    }
    override suspend fun connect(ip: String) {
        if (state.value.mode != Mode.MIC) return
        // Numeric IPv4 only: no blocking DNS lookup on the UI thread.
        val parts = ip.trim().split('.')
        if (parts.size != 4 || parts.any { part -> part.isEmpty() || part.any { !it.isDigit() } || part.toIntOrNull() !in 0..255 }) {
            mutable.update { it.copy(status = "กรุณากรอก IPv4 เช่น 192.168.1.10") }; return
        }
        // A failed/ended microphone session closes transport. Reopen it before retrying.
        if (timer == null) select(Mode.MIC)
        if (timer == null) return
        if (!backgroundRequested || !MicrophoneService.running) {
            backgroundRequested = true
            try { MicrophoneService.start(appContext, ip.trim()) }
            catch (_: RuntimeException) {
                backgroundRequested = false
                mutable.update { it.copy(status = "เปิดไมค์ปิดจอไม่ได้ กรุณาเปิดแอปและอนุญาตไมโครโฟนแล้วลองใหม่") }
            }
            return
        }
        disconnectMic(false)
        target = InetAddress.getByAddress(parts.map { it.toInt().toByte() }.toByteArray()) as Inet4Address
        nonce = java.lang.Long.toUnsignedString(random.nextLong(), 16)
        lastConnect = now(); lastReply = now()
        mutable.update { it.copy(status = "กำลังเชื่อมต่อ ${target!!.hostAddress}", rttMs = null) }
        sendConnect()
    }
    private fun sendConnect() { target?.let { lan.command("KL1 CONNECT $nonce", it) } }
    private fun control(message: String, ip: InetAddress, port: Int) {
        val p = message.split(' ')
        if (p.size < 3 || p[0] != "KL1") return
        if (state.value.mode == Mode.HOST) {
            peers.receive(p, ip, port)
            mutable.update { it.copy(peers = peers.size) }
        } else if (state.value.mode == Mode.MIC && ip == target && port == LanTransport.CONTROL) {
            when (p[1]) {
                "ACCEPT" -> if (p.size == 4 && p[2] == nonce && session == 0) {
                    val id = p[3].toIntOrNull()?.takeIf { it > 0 } ?: return
                    native.setMicNetwork(lan.networkHandle)
                    if (native.startMic(ip.hostAddress!!, id, input)) {
                        session = id; lastReply = now()
                        mutable.update { it.copy(status = "ส่งเสียงไปยัง ${ip.hostAddress}", peers = 1) }
                    } else {
                        lan.command("KL1 BYE $id", ip)
                        target = null; stopBackgroundMicrophone()
                        mutable.update { it.copy(status = "เปิดไมค์ไม่ได้ ตรวจสิทธิ์หรืออุปกรณ์เสียง") }
                    }
                }
                "FULL" -> if (p[2] == nonce) { disconnectMic(); mutable.update { it.copy(peers = 0, rttMs = null, status = "Host มีไมค์ครบ 2 เครื่องแล้ว") } }
                "PONG" -> if (p.size == 4 && p[2].toIntOrNull() == session && p[3] == pendingPing) {
                    lastReply = now()
                    mutable.update { it.copy(rttMs = now() - (p[3].toLongOrNull() ?: now())) }
                }
            }
        }
    }
    private fun tick() {
        val time = now()
        peers.expire()
        if (target != null) {
            if (time - lastReply > 4000) {
                disconnectMic(); mutable.update { it.copy(status = "Host ไม่ตอบกลับ กรุณาเชื่อมต่อใหม่", peers = 0, rttMs = null) }
            } else if (session != 0) {
                pendingPing = time.toString(); lan.command("KL1 PING $session $pendingPing", target!!)
            } else if (time - lastConnect > 500) { lastConnect = time; sendConnect() }
        }
        val m = native.metrics()
        if (backgroundRequested && m[3] != 0f) {
            disconnectMic()
            mutable.update { it.copy(peers = 0, engineError = true, status = "อุปกรณ์ไมค์หยุดทำงาน กรุณาเปิดแอปแล้วเชื่อมต่อใหม่") }
            return
        }
        mutable.update { it.copy(hosts = it.hosts.filter { h -> time - h.seenAt < 5000 },
            vu = m[0], underruns = m[1].toInt(), queuedMs = m[2].toInt(),
            peers = if (it.mode == Mode.HOST) peers.size else if (session != 0) 1 else 0,
            engineError = m[3] != 0f) }
    }
    private fun disconnectMic(stopService: Boolean = true) {
        if (session != 0) target?.let { lan.command("KL1 BYE $session", it) }
        native.stop(); target = null; session = 0
        if (stopService) {
            stopBackgroundMicrophone()
            timer?.cancel(); timer = null
            lan.stop()
            lock?.let { if (it.isHeld) it.release() }; lock = null
        }
    }
    private fun reset() {
        stopBackgroundMicrophone()
        generation++; timer?.cancel(); timer = null
        if (session != 0) target?.let { lan.command("KL1 BYE $session", it) }
        lan.stop(); native.stop(); peers.clear(); target = null; session = 0
        lock?.let { if (it.isHeld) it.release() }; lock = null
    }
    override fun effects(value: MicEffects) = native.effects(value)
    override fun lowLatency(enabled: Boolean) = native.setLowLatency(enabled)
    override fun hostMix(gain: Float, delayMs: Int) = native.setHostMix(gain, delayMs)
    override fun inputDevice(id: Int) { input = id }
    override suspend fun outputDevice(id: Int) {
        output = id
        // Restart host deliberately: removes old sessions and recreates AAudio on the route.
        if (state.value.mode == Mode.HOST) select(Mode.HOST)
    }
    override suspend fun close() { reset(); mutable.value = SessionState() }
    override suspend fun pause() {
        val mode = state.value.mode
        reset()
        mutable.value = SessionState(mode = mode, status = if (mode == Mode.MIC) "หยุดส่งเสียงไมค์แล้ว เลือกเครื่องเล่นเพื่อเชื่อมต่อใหม่" else "พักการเชื่อมต่อเมื่อออกจากแอป")
    }
    override fun destroy() { reset(); scope.cancel() }
}