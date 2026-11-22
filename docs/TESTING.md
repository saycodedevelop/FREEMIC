# การตรวจสอบ

## Build และ static checks

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

CI ใน `.github/workflows/android.yml` ทำ build/lint และทดสอบ portable C++ core (packet validation, PCM roundtrip, SPSC ownership/order ภายใต้สอง thread, DSP finite/bounds) บน Linux ไม่แทนการทดสอบ Oboe/driver ด้วยเครื่องจริง

## ทดสอบบนมือถือจริง

| สถานการณ์ | ผลที่ต้องตรวจ |
|---|---|
| ติดตั้ง APK เดียวกัน 2–3 เครื่อง | ทุกเครื่องเปิด Home ได้และเลือกได้ทั้งสองบทบาท |
| Host เปิดก่อน/หลัง Mic | ค้นพบในหลายรอบ discovery, stale entry หายหลัง 5 s |
| Hotspot ที่ไม่ส่ง broadcast | ใส่ IPv4 ของ Host แล้วเชื่อมต่อได้หาก unicast ไม่ถูก block |
| ปฏิเสธสิทธิ์ RECORD_AUDIO | ไม่เริ่ม input; Host ยังใช้งานได้ |
| สลับ Home/Mic/Host หลายรอบ | คืน sockets/streams ไม่มี address-in-use หรือไมค์ค้าง |
| ไมค์ 2 เครื่อง และเครื่องที่ 3 | ได้ยินทั้งสองไมค์, เครื่องที่ 3 แจ้ง Host เต็ม |
| ตัด Wi-Fi/ปิด Host | Mic แจ้ง timeout ภายในประมาณ 4–5 s และหยุดส่ง |
| delay/feedback/reverb/EQ | ผลเสียงเปลี่ยนก่อนส่งโดยไม่ freeze UI |
| ปล่อยเงียบ/พูดค่อย/พูดดัง | gate ไม่ปิดคำพูดมากเกินและ peak ไม่ clip รุนแรง |
| USB/สาย input | เลือกก่อน connect แล้วทดสอบว่าใช้ไมค์นั้นจริง |
| USB/สาย output และถอดสาย | ตรวจ route จริง; เมื่อ disconnected แสดง error และ reconnect ได้ |
| Bluetooth A2DP/BLE | ขอ permission, route ตามอุปกรณ์จริง, แสดงข้อความเรื่อง delay |
| เปิดเพลง WebView พร้อมไมค์ | ได้ยินพร้อมกันผ่าน route เดียว, music slider อาจใช้ไม่ได้ตามเว็บไซต์ |
| เปิดเพลงจาก WebView | หน้าเครื่องเล่นและไมค์ยังอยู่ ไม่กลับหน้าเลือกเมื่อเพลงขอ audio focus |
| สลับไปเบราว์เซอร์/กด Home/ปิดหน้าจอ แล้วกลับแอป | เครื่อง Host พักเสียงและคงหน้าเดิม; เครื่อง Mic ที่เชื่อมต่อและมี foreground service ต้องส่งเสียงต่อโดยไม่ reconnect |
| กลับหน้าแรกในแอป | ต้องยืนยันก่อนหยุดเพลงและเปลี่ยนโหมด |
| วิดีโอเต็มจอ/หมุนเครื่อง/ย้อนกลับ | ย่อเต็มจอก่อน, ย้อนประวัติเพลงก่อน, ไม่ปิดโหมดเครื่องเล่นเอง |
| WebView renderer ล้ม | แสดงข้อความและกดโหลดใหม่ได้ โดยคงโหมด Host |
| ใช้ต่อเนื่อง 30 นาที | บันทึก underruns, queue depth, clock drift, CPU/thermal |
| VPN/AP isolation/Wi-Fi ไม่มี Internet | บันทึกข้อจำกัด route/broadcast; manual IP ไม่แก้ client isolation |

วัด latency จริงด้วยสัญญาณ impulse/loopback และบันทึกผลแยก wired, USB, Bluetooth อย่าใช้ RTT/2 เป็นผลวัด end-to-end

ก่อนส่งมอบ production ต้องตรวจบนอย่างน้อยสองผู้ผลิตและ Android หลายรุ่น ขนาด callback และ fast path แตกต่างกันตาม driver
## Player regression tests

`PlayerRegressionTest` uses Robolectric with a fake session repository to check
foreground/background role preservation, WebView identity, explicit Home confirmation,
in-app song search, renderer recovery, fullscreen Back behavior, and small-screen layout.
`PlayerNavigationTest` covers Thai query encoding and rejected non-HTTPS links.
These tests do not execute Chromium media playback or native audio drivers; use the
real-device table above to verify actual websites and microphone playback.
## v0.2 regression coverage and device acceptance

Portable `audio_playout_test.cpp` verifies ordering, duplicates, foreign sessions, sequence wrap,
short loss concealment, long-gap recovery, and 60-second simulations with jitter and +/-1500 ppm clocks.
`MicrophoneServiceTest` verifies foreground-before-capture, permission denial, notification Stop,
CPU-lock cleanup, connection failure, manifest service type, and non-sticky restart behavior.
The Activity regression verifies that a live microphone is retained on stop/start/destruction and
that Mic mode does not force the screen on. Release-version tests cover semantic ordering and reruns.

On real devices, connect Mic first, lock its screen for 10 and then 30 minutes, and verify audible
continuous input at Host. Unlock without reconnecting. Stop from the notification and check that
microphone access and notification disappear. Repeat with two microphones, denied notification
permission, revoked microphone access, Wi-Fi loss, USB removal, and Bluetooth playback. Compare
wired/USB against Bluetooth and record observed interruptions and actual acoustic latency.
## v0.3 UI and onboarding

`GettingStartedTest` covers first-run completion, remembered Skip, replaying help during playback,
and showing/hiding microphone controls without stopping a live session. `SessionPresenterTest`
checks that connection errors and background microphone readiness use accurate user-facing language.
`UiPreviewTest` uses native Android/Skia rendering to produce Home, microphone, and onboarding PNGs
in `app/build/reports/ui-preview`; it also verifies visible role choices and minimum touch height.
Review Thai text, large font settings, portrait/landscape player area, guide scrolling, and TalkBack
headings/state announcements on a real device. UI previews use fake session state and do not play audio.
## LAN discovery and two-microphone regression

`NsdDiscoveryTest` checks advertisement cleanup, serialized resolution and retry, IPv4 filtering,
service loss, refresh, and stale callbacks on API 32 and 35 with a fake NSD daemon.
`LanTransportTest` disables all broadcasts and uses real loopback UDP sockets to verify two independent
microphones discover and connect to one host using NSD candidates and unicast probes.
`HostSessionsTest` verifies two distinct sessions, independent heartbeats/disconnects, duplicate CONNECT,
full capacity, source validation, and immediate reclamation of expired slots.

Device acceptance: install the updated APK on the player and both microphone phones, connect all three
to the same non-guest Wi-Fi, and keep the player open. Both microphones must list the player, connect,
and produce simultaneous audible input; the player should show 2/2. Stop one microphone and verify the
other continues. Reconnect it without restarting the player. Repeat with broadcast filtered but mDNS
and unicast allowed, an unsuccessful connection followed by retry, and a Wi-Fi reconnect. AP/client
isolation still requires changing router settings; manual IP also needs working device-to-device traffic.

## Hotspot and low latency

PlayerHotspotTest covers credentials, duplicate starts, stop/late-start cleanup, permission errors,
platform failure/retry, and system stop on API 32 and 35 with a fake Wi-Fi manager.
WifiRoutingTest verifies microphone sockets use Wi-Fi without Internet rather than cellular, exclude
VPN transports, and leave Host listening sockets unbound. ControlPanelTest verifies the latency switch
is remembered and does not restart the player. Native playout tests cover 15 ms startup, profile
switching, large callbacks, and clean/stressed 60-second +/-1500 ppm clock simulations with sanitizers.
The low-latency stress case permits at most 24 briefly concealed packets per 12,000 generated (0.2%);
stable and clean low-latency cases require zero underruns. All simulated cases require no sustained silence.

On real Samsung A56 (microphone) and vivo V29 (player), follow LATENCY.md. Record Android versions,
permission decisions, hotspot credentials being usable, a second microphone, discovery/manual IP,
Internet behavior, audible delay, dropouts, and battery effects. Verify leaving Host or closing the app
stops its hotspot while a system-created hotspot remains user-controlled. Compare normal Wi-Fi and
player hotspot with added delay at zero and echo/reverb off. No device results are claimed by unit tests.