# KaraokeLink

โปรเจกต์ Android แบบ **Single APK** สำหรับติดตั้งไฟล์เดียวกันบนมือถือทุกเครื่อง เลือก Mic หรือ Player/Host ที่หน้า Home ได้ทันที ไม่มี product flavor แยกบทบาท

## เริ่มใช้งาน

เปิดโฟลเดอร์นี้ด้วย Android Studio และ Sync Gradle ใช้ JDK 17 หรือ 21 (Gradle รุ่นนี้ยังไม่รองรับ JDK 25), Android SDK 35, NDK 27.0.12077973 และ CMake 3.22.1 ติดตั้ง SDK components ผ่าน SDK Manager หากยังไม่มี

```powershell
$env:JAVA_HOME = 'C:\Users\USER\.jdks\jbr-21.0.11'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk` มี arm64-v8a, armeabi-v7a และ x86_64 อยู่ในไฟล์เดียว (minSdk 28 / Android 9, targetSdk 35)

1. ต่อมือถือกับ Wi-Fi เดียวกัน หรือ Hotspot ที่อนุญาตให้ client ติดต่อกัน
2. ติดตั้ง APK เดียวกันทั้งสองเครื่อง เครื่องที่ต่อลำโพงเลือก **Player / Host**
3. แนะนำต่อสาย 3.5mm / USB DAC ก่อนเปิด Host หากไม่มีสายจะใช้ media route เริ่มต้นของระบบและแสดงข้อความแจ้ง
4. เครื่องไมค์เลือก Host จาก Home ได้ในคลิกเดียว ระบบจะขอสิทธิ์ไมค์เมื่อจำเป็น หรือเข้า Mic Mode แล้วกรอก IPv4 สำรอง
5. เพิ่มไมค์เครื่องที่สองด้วยวิธีเดียวกัน ปรับ gain อย่างระมัดระวังเพื่อไม่ให้ clipping / feedback
6. กลับ Home เพื่อคืน stream, socket และ multicast lock ก่อนสลับโหมด รุ่นเริ่มต้นหยุด session เมื่อแอปออกจาก foreground / หน้าจอดับ

## สิ่งที่มีในรุ่นเริ่มต้น

- Single Activity, Kotlin domain/repository boundary, StateFlow และ shared JNI audio engine
- UDP broadcast discovery ทุก 1.5 s, รายชื่อหมดอายุ 5 s และ IPv4 manual fallback
- Control handshake มี nonce, session ID, retry, peer limit 2, heartbeat, RTT และ timeout
- Oboe input/output 48 kHz, float DSP ภายใน, ส่ง PCM16 mono แพ็กเก็ต 240 frames / 5 ms
- Mic gain, soft noise gate, echo delay/feedback/wet, reverb แบบ comb และ EQ สามย่าน crossover
- Host รับไมค์สองเครื่อง, bounded SPSC queues, initial jitter target 15 ms, silence concealment และตัด backlog
- VU meter, network RTT, receive queue และ underrun counters
- เลือก external input / output จากอุปกรณ์ที่ Android มองเห็น, Bluetooth permission และข้อความแจ้ง delay
- WebView HTTPS พร้อมเปิด URL และทดลองปรับระดับ HTML5 audio/video ที่เข้าถึงได้

## ขอบเขตที่ต้องเข้าใจ

**WebView ในรุ่นนี้ไม่ได้ส่ง PCM เข้า C++ mixer** เพลงออกจาก WebView ผ่าน Android media stack ส่วนไมค์ถูก mix ใน C++ แล้ว Android system mixer จึงรวมทั้งสอง stream ที่ขาออก UI ระบุวิธีนี้ไว้ชัดเจน

การปรับระดับเพลงใช้ JavaScript กับ HTML5 media ใน top document เท่านั้น เว็บไซต์บางแห่งเปลี่ยน element แบบ dynamic, ใช้ iframe ต่างโดเมน, ไม่อนุญาต autoplay หรือไม่รองรับ embedded browser ต้องใช้ตัวควบคุมของเว็บ/แหล่งเพลงที่รองรับ ไม่รับประกันว่าเว็บไซต์ใดเว็บไซต์หนึ่งจะเล่นได้ การเลือก Oboe device ไม่ได้บังคับขาออก WebView ต้องเลือก media output ใน Android ให้ตรงกัน

Bluetooth ช้ากว่าสายตามอุปกรณ์และ codec แถบ Audio delay เป็นการ **เพิ่ม delay ให้เสียงไมค์ 0–500 ms** ไม่ใช่ค่าชดเชยติดลบ ไม่ลด hardware latency และไม่แก้การได้ยินเสียงตัวเองช้า ถ้าจะเลื่อนเพลงแทนไมค์ต้องใช้ player ที่ควบคุม PCM/timeline ได้

ค่าบนหน้าจอเป็น RTT เครือข่ายและ queue depth ไม่ใช่ one-way หรือ mouth-to-speaker latency ต้องวัดจริงด้วย loopback/อุปกรณ์วัดเสียง ไม่มีการรับประกันตัวเลข latency

ดู [สถาปัตยกรรมและ WebView audio](docs/ARCHITECTURE.md), [wire protocol](docs/PROTOCOL.md), [คู่มือทดสอบสองเครื่อง](docs/TESTING.md)
## Automatic releases

See [automatic preview releases](docs/RELEASING.md). Pushes to main publish an installable development APK only after build, lint, and portable audio tests pass.

## เปิดเพลงให้ง่ายขึ้น

เลือก **เปิดเครื่องเล่นเพลง** แล้วพิมพ์ชื่อเพลงหรือวางลิงก์ HTTPS ในช่องด้านบน
กด **ค้นหา** เพื่อเปิดเพลงในแอป ปุ่ม **ตั้งค่าเสียง** รวมระดับไมค์ เพลง และขาออกไว้ด้วยกัน
เครื่องที่ใช้ร้องเลือก **ใช้เครื่องนี้เป็นไมค์** แล้วเลือกเครื่องเล่นที่ค้นพบ
เมื่อสลับไปเบราว์เซอร์แล้วกลับแอป จะคงหน้าเครื่องเล่นเดิม โดยพักไมค์ระหว่างอยู่เบื้องหลัง