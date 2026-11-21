# สถาปัตยกรรม

```text
:app (หนึ่ง applicationId / หนึ่ง APK)
  presentation/MainActivity          Single Activity + framework Views + WebView
             ↓
  domain/SessionRepository           SessionState, Mode, MicEffects (ไม่อ้าง Android)
             ↑ implementation
  data/AndroidSessionRepository      session lifecycle, permission-independent orchestration
             ↓                     ↓
  core/network/LanTransport          core/audio/NativeAudio (JNI)
  discovery + control UDP            cpp/Engine.cpp + AudioCore.h
                                    Oboe / DSP / native audio UDP
```

แยกตาม layer ในโมดูลเดียวเพื่อให้เริ่ม build ได้ง่าย เมื่อขนาดโตสามารถย้าย domain, transport, audio เป็น Gradle library modules ได้โดยคง interface เดิม

## Data path

```text
Mic input / USB → Oboe 48k float callback → gain + gate + EQ + echo + reverb
       → PCM frame accumulator → SPSC queue → network worker → UDP
Host UDP worker → validate IP/session/header → per-peer SPSC queue
       → Oboe callback: 2 mic sum → master gain → extra delay → clip → output
WebView media → Android media stack → Android system mixer → selected system media route
```

Callbacks ไม่มี socket calls, JVM calls, locks, dynamic allocations หรือ disk IO ใช้ preallocated queue และ atomics ส่วน open/close/เปลี่ยนบทบาทถูก serialize บน Main และมีการ join network worker เมื่อจบ session การเปิดอุปกรณ์อาจทำให้ Main รอช่วงสั้น ควรย้าย lifecycle ไป dedicated serialized dispatcher ก่อน production

Oboe ขอ LowLatency และ Exclusive ก่อน มี Shared fallback; 48 kHz conversion เปิดไว้เมื่อ hardware rate ต่างกัน การได้ fast path หรือ exclusive ขึ้นกับอุปกรณ์จริง แพ็กเก็ต 5 ms ไม่ได้หมายความว่า callback หรือ physical latency เท่ากับ 5 ms

รุ่น v0.2 ใช้ SPSC queue และ reorder window ขนาด 64 แพ็กเก็ต บัฟเฟอร์เริ่มประมาณ 30 ms และปรับตามข้อมูลขาด/ขนาด callback มีการผ่อนเสียงเมื่อแพ็กเก็ตหาย และ fractional resampling ชดเชย clock drift โดยไม่ retransmit เสียง ดูรายละเอียดท้ายเอกสาร

DSP เป็น reference implementation ไม่ใช่ studio plugin: reverb เป็นสาม comb filters, EQ ใช้ crossover สองจุด 200 Hz/3 kHz และลดการกระชากของ gain ด้วย smoothing echo delay และ delay compensation เปลี่ยนได้ทันทีจึงอาจมีเสียงคลิกระหว่างเลื่อน ปลายทางมี hard clip ป้องกันค่าเกินช่วง ควรเพิ่ม limiter และ crossfade delay ก่อน production

## การนำเพลงมามิกซ์อย่างถูกต้อง

### รุ่นเริ่มต้น: system mixer

WebView และ Oboe เล่นเป็น media streams พร้อมกัน Android จัดการรวมเสียงที่ขาออก ไม่สามารถบันทึกผลรวม / ทำ master DSP กับเพลงใน C++ ด้วยเส้นทางนี้ ความแม่นของ route และการ sync มีข้อจำกัด UI จึงแยก mic gain ออกจาก HTML5 volume ที่เป็น best effort

### ทางหลักสำหรับ native PCM mixer

ใช้สื่อที่มีสิทธิ์เข้าถึง (ไฟล์/URL/สตรีมที่ player รองรับ) กับ Media3 หรือ MediaExtractor + MediaCodec แล้วส่ง decoded PCM ผ่าน bounded queue เข้า C++:

1. แปลง sample rate เป็น 48 kHz พร้อมคง music stereo และ timestamp
2. เพิ่ม music bus stereo ใน Engine และทำ per-mic pan/gain, music gain, limiter บน clock ของ output stream
3. ใช้ video timeline เดียวกับ audio; ปรับ music/video delay ตามค่าที่วัด ไม่ลด latency ด้วยค่าติดลบ
4. ถ้าใช้ Media3 ต้องทำ audio sink ที่ควบคุมเองและหยุด default sink เพื่อไม่เล่นเพลงซ้ำ

ไม่ควรดึง stream ที่ถูกจำกัดสิทธิ์จากหน้าเว็บ หรืออ้างว่า evaluateJavascript ส่ง PCM ของ arbitrary WebView ได้

### ทางเลือก: AudioPlaybackCapture (Android 10+)

ใช้ MediaProjection ขอ consent ของผู้ใช้ แล้วสร้าง AudioRecord ด้วย AudioPlaybackCaptureConfiguration กรอง UID/usage ของแหล่งเสียงที่อนุญาต จากนั้นส่ง PCM เข้า music bus **ซึ่งยังไม่ได้ implement ใน starter นี้**

ข้อจำกัด: policy ของแหล่งเสียงต้องอนุญาต capture, usage ต้องอยู่ในกลุ่มที่รองรับ, UID อยู่ใน user profile เดียวกัน และ WebView renderer/DRM/นโยบายเว็บไซต์อาจไม่อนุญาต ต้องทดสอบจริงและจัดการ token revoke รุ่นใหม่ต้องมี foreground service type mediaProjection และทำตามลำดับ consent/startForeground ของ OS นั้น

Capture ไม่ได้ mute เสียงต้นฉบับอัตโนมัติ จึงอาจเล่นซ้ำหรือเกิด feedback ถ้าจับ output ของแอปตัวเองด้วย การตั้ง HTMLMediaElement.volume=0 อาจทำให้จับได้แต่ silence ห้ามใช้วิธี mute แบบเดาสุ่ม ต้องออกแบบ topology ให้จับเฉพาะ source และมีการเล่นออกเพียงครั้งเดียว Native decoded media จึงเป็นทางที่ควบคุมได้ดีกว่า

## Permissions และ lifecycle

- RECORD_AUDIO: runtime เฉพาะ Mic, ไม่เปิด output stream บนเครื่องไมค์เพื่อเลี่ยงการ monitor ซ้ำ
- INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, CHANGE_WIFI_MULTICAST_STATE: LAN และ discovery
- BLUETOOTH_CONNECT: runtime บน API 31+ เมื่อผู้ใช้เลือก Bluetooth ไม่ใช้ communication mode/SCO เป็นค่าเริ่มต้น
- MulticastLock ใช้ระหว่าง discovery แล้ว release ทุกครั้งที่ออกจาก session
- โหมด Mic ใช้ microphone foreground service หลังได้รับสิทธิ์และผู้ใช้เชื่อมต่อ เครื่อง Host/WebView ยังทำงานเฉพาะ foreground; ไม่มี MediaProjection capture
- targetSdk 35 ใช้ permission behavior ปัจจุบันของ target นี้ หากยกระดับ targetSdk เป็น 37+ ต้องเพิ่ม ACCESS_LOCAL_NETWORK runtime flow ตามเอกสาร Android; บนเครื่องที่เปิด local-network restrictions แบบทดสอบต้องตรวจเงื่อนไขเพิ่มเติม
- route เปลี่ยน/ถอด USB: Oboe error ถูกแสดงบน UI แล้วให้เริ่ม session ใหม่ ไม่ reopen stream บน audio error callback

## งานต่อก่อน production

การปรับจูน jitter/drift จากการวัดจริง, native music bus, measured route reporting, per-mic gain, smoother limiter/delay, audio focus integration ที่ทดสอบร่วมกับเว็บ, ทดสอบ foreground microphone บนหลายผู้ผลิต, network selection/binding สำหรับ Wi-Fi ที่ไม่มี Internet พร้อม VPN/หลาย interface, NSD alternative และการทดสอบ target SDK ใหม่

Control และ PCM เป็น plaintext LAN prototype: nonce/session ใช้แยก session และตรวจ source ไม่ใช่ authentication/encryption ใช้เฉพาะเครือข่ายที่เชื่อถือได้ เพิ่ม pairing code/key exchange และ authenticated packets ก่อนรองรับเครือข่ายสาธารณะ Broadcast อาจถูก AP isolation, VPN, firewall หรือ Hotspot block; manual IP ช่วยเฉพาะกรณี unicast ติดต่อกันได้

## แหล่งอ้างอิง

- Android Oboe low latency: https://developer.android.com/games/sdk/oboe/low-latency-audio
- Oboe source/release 1.9.3: https://github.com/google/oboe/releases/tag/1.9.3
- Playback capture และ capture policy: https://developer.android.com/media/platform/av-capture
- NSD (ทางเลือกสำหรับ discovery): https://developer.android.com/develop/connectivity/wifi/use-nsd
- Local network permission: https://developer.android.com/privacy-and-security/local-network-permission
- AGP 8.9 compatibility / Gradle 8.11.1 / JDK17: https://developer.android.com/build/releases/agp-8-9-0-release-notes
## Player lifecycle and focus

The embedded WebView is the music audio-focus owner. The native host does not register
a competing focus request that closes the session when the web player starts audio.
Only an explicit, confirmed Home action changes the selected role to Home.
On Host stop, the app pauses top-level HTML5 media, WebView, native output, sockets, and discovery locks while retaining the selected role and browser instance.
An active microphone foreground service retains capture and transport across Activity stop/start/destruction.
On Host start it resumes that role and WebView; Host microphone peers reconnect as needed.
WebView renderer failures offer reload within the same Host screen. Fullscreen video
uses an overlay, preserving the underlying player and session.
## v0.2 microphone service and playout

`SessionRuntime` owns the single process-wide native repository. An explicit, permission-granted
Mic connection starts `MicrophoneService` while the Activity is visible. The service promotes itself
to microphone-type foreground before beginning capture. The Activity retains the session across stop,
start, and destruction while the microphone service owns it. A persistent notification exposes Stop.
The service holds a refreshed, timed partial CPU wake lock and best-effort high-performance Wi-Fi lock,
releasing them on stop, connection failure, device failure, or service destruction. It is not sticky
and never restarts microphone capture automatically after a process kill. Host/WebView remains foreground-only.

The audio callback now owns `PlayoutBuffer`: a bounded 64-slot sequence window fed from a bounded SPSC
queue. It reorders datagrams, rejects duplicates/foreign sessions, fades short losses, adapts a normal
30-60 ms reserve (larger for large device callbacks), and uses bounded fractional resampling with a
filtered PI fill controller to compensate clock drift. Long gaps re-prime; large backlogs are bounded
and faded. No locks, heap allocation, JVM calls, or socket calls run in the audio callback.
Output stream buffering is four hardware bursts; input remains two. Bluetooth radio/driver latency
and quality still depend on actual devices. Noise gate defaults to off to preserve quiet syllables.