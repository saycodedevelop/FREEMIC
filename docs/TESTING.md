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
| สลับไปเบราว์เซอร์/กด Home/ปิดหน้าจอ แล้วกลับแอป | พักไมค์และการเชื่อมต่อใน background; กลับมาหน้าเดิมและ WebView เดิม; เครื่องไมค์เชื่อมต่อใหม่เมื่อจำเป็น |
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