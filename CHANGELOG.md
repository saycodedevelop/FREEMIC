# Changelog

## [Unreleased]

### [th] ไทย

- ค้นหาเครื่องเล่นด้วย Android NSD/mDNS ควบคู่กับ UDP broadcast และยืนยันด้วย unicast พร้อมแสดงยี่ห้อ รุ่น และ IP ของเครื่องเล่น
- แยกพอร์ตค้นหาและเซสชันของไมค์ 2 เครื่อง ให้เชื่อมต่อพร้อมกันและหยุดแต่ละเครื่องได้อย่างอิสระ
- เปิดระบบเครือข่ายกลับมาก่อนเชื่อมต่อซ้ำหลังล้มเหลว และคืนช่องไมค์ที่หมดเวลาทันทีเมื่อมีคำขอเชื่อมต่อใหม่
- เพิ่มการทดสอบวงจร NSD, UDP จริงสำหรับไมค์สองเครื่อง, การลองเชื่อมต่อซ้ำ และการตัดการเชื่อมต่อแยกเครื่อง
- รองรับภาษาไทย อังกฤษ จีนตัวย่อ เวียดนาม และอินโดนีเซียในปุ่ม คู่มือ การแจ้งเตือน และข้อความเชื่อมต่อ โดยใช้ภาษาตามมือถือหรือเลือกเองจากหน้าแรกและการตั้งค่า Android 13 ขึ้นไป
- เพิ่ม README ครบห้าภาษาพร้อมลิงก์สลับภาษา และสร้าง Release จากรายการเปลี่ยนแปลงที่แปลครบทั้งห้าภาษา

### [en] English

- Discover players with Android NSD/mDNS alongside UDP broadcast and confirm them by unicast; show the player's manufacturer, model, and IP.
- Give two microphones independent discovery ports and sessions for simultaneous connections and separate disconnects.
- Reopen transport before retrying a failed connection, and reclaim expired microphone slots immediately on a new connection request.
- Add regression coverage for NSD lifecycle, two microphones over real UDP, retries, and independent disconnects.
- Support Thai, English, Simplified Chinese, Vietnamese, and Indonesian across controls, guidance, notifications, and connection messages; follow the phone language or choose an override on Home or in Android 13+ app settings.
- Add five README translations with language links and generate releases from changes translated into all five languages.

### [zh-CN] 简体中文

- 使用 Android NSD/mDNS 和 UDP 广播搜索播放器，通过单播确认，并显示播放器的品牌、型号和 IP。
- 为两台麦克风分配独立搜索端口和会话，支持同时连接及单独断开。
- 连接失败后重试前重新开启网络传输，并在收到新连接请求时立即回收已超时的麦克风位置。
- 增加 NSD 生命周期、真实 UDP 双麦克风连接、重试及独立断开的回归测试。
- 控件、指南、通知和连接信息支持泰语、英语、简体中文、越南语和印度尼西亚语；可跟随手机语言，或在主页及 Android 13 以上的应用设置中选择语言。
- 提供五种语言的 README 和切换链接，并根据五种语言的更新日志生成发布说明。

### [vi] Tiếng Việt

- Tìm máy phát nhạc bằng Android NSD/mDNS cùng UDP broadcast, xác nhận bằng unicast và hiển thị hãng, mẫu máy cùng IP.
- Hai micro có cổng tìm kiếm và phiên riêng để kết nối đồng thời và ngắt từng máy độc lập.
- Mở lại truyền tải mạng trước khi thử kết nối lại sau lỗi và thu hồi ngay vị trí micro đã hết thời gian khi có yêu cầu kết nối mới.
- Thêm kiểm thử hồi quy cho vòng đời NSD, hai micro qua UDP thật, thử lại và ngắt kết nối độc lập.
- Hỗ trợ tiếng Thái, Anh, Trung giản thể, Việt và Indonesia cho điều khiển, hướng dẫn, thông báo và kết nối; theo ngôn ngữ điện thoại hoặc chọn tại trang chủ hay cài đặt ứng dụng Android 13 trở lên.
- Thêm README năm ngôn ngữ với liên kết chuyển ngôn ngữ và tạo ghi chú phát hành từ nhật ký đã dịch đủ năm ngôn ngữ.

### [id] Bahasa Indonesia

- Temukan pemutar dengan Android NSD/mDNS bersama siaran UDP, konfirmasi lewat unicast, serta tampilkan merek, model, dan IP.
- Berikan port pencarian dan sesi terpisah untuk dua mikrofon agar dapat terhubung bersamaan dan diputus secara mandiri.
- Buka kembali transport jaringan sebelum mencoba ulang koneksi yang gagal dan segera kosongkan slot mikrofon kedaluwarsa saat ada permintaan koneksi baru.
- Tambahkan pengujian regresi untuk siklus NSD, dua mikrofon melalui UDP nyata, percobaan ulang, dan pemutusan terpisah.
- Dukung bahasa Thai, Inggris, Mandarin Sederhana, Vietnam, dan Indonesia pada kontrol, panduan, notifikasi, dan pesan koneksi; ikuti bahasa ponsel atau pilih di beranda maupun pengaturan aplikasi Android 13 ke atas.
- Tambahkan README dalam lima bahasa beserta tautan pilihan bahasa dan buat catatan rilis dari perubahan yang telah diterjemahkan ke kelima bahasa.
## [0.3.1]

### เก็บรายละเอียดหน้าตาและการใช้งาน

- ปรับโทนสี พื้นผิว ปุ่ม และระยะห่างให้สอดคล้องกัน โดยให้ปุ่มหลักเด่นกว่าตัวเลือกเสริม
- รวมสถานะไมค์ไว้จุดเดียว และจัดปุ่มหยุด/เปลี่ยนเครื่องเล่นไว้ด้วยกัน
- จัดหน้ามิกเซอร์ อุปกรณ์ไมค์ และข้อมูลการเชื่อมต่อเป็นแผงด้านล่างแบบเดียวกัน พร้อมปุ่มปิดและเสร็จแล้ว
- แยกระดับเสียง อุปกรณ์ และการตั้งค่าขั้นสูงให้สแกนหาได้ง่าย
- ปรับคู่มือเริ่มต้นเป็นแผงด้านล่าง และให้ปุ่มถัดไปเต็มความกว้างในขั้นตอนแรก
- เพิ่มการทดสอบเปิด/ปิดและหมุนจอขณะตั้งค่าโดยไม่รีเซ็ตเพลงหรือไมค์
- ปิดแผงตั้งค่าของโหมดเดิมอัตโนมัติเมื่อเปลี่ยนหน้าที่เครื่อง

## [0.3.0]

### ออกแบบ UI/UX ใหม่

- แยกเครื่องเล่นเพลงและเครื่องไมค์ด้วยการ์ดหลักสองใบ สี และไอคอนที่ชัดเจน
- ใช้ปุ่มขนาดสัมผัสอย่างน้อย 48 dp ตัวหนังสืออ่านง่าย และดีไซน์พื้นหลังเข้มแบบเดียวกันทั้งแอป
- แสดงสถานะพร้อมใช้งาน กำลังเชื่อมต่อ และข้อผิดพลาดด้วยคำแนะนำที่เข้าใจง่าย
- หน้าไมค์ยุบรายการค้นหาเมื่อเชื่อมต่อแล้ว พร้อมปุ่มหยุดและเปลี่ยนเครื่องเล่น
- แสดงค่าระดับเสียงเป็นเปอร์เซ็นต์ และแยกการตั้งค่าขั้นสูงออกจากหน้าหลัก

### คู่มือผู้ใช้ใหม่

- เพิ่มคู่มือเริ่มต้นสามขั้นตอน พร้อมภาพประกอบ เครื่องเล่น → เครื่องไมค์
- เปิดครั้งแรกอัตโนมัติ ข้าม/ย้อนกลับได้ และเปิดซ้ำจากปุ่มวิธีใช้ทุกหน้า
- เพิ่มปุ่มค้นหาเครื่องเล่นซ้ำ คำแนะนำเมื่อหาไม่เจอ และวิธีเชื่อมต่อด้วย IP
- ปรับคู่มือให้รองรับการหมุนจอ โดยไม่หยุดเพลงหรือไมค์ที่กำลังทำงาน

## [0.2.0]

### ปรับปรุงเสียงร้อง

- เรียงแพ็กเก็ตเสียงที่มาสลับลำดับ แทนการทิ้งทันที
- ปรับบัฟเฟอร์รับเสียงตามการขาดช่วงและขนาด callback รวมถึงขาออก Bluetooth
- ชดเชยความต่างของนาฬิกาเสียงระหว่างเครื่อง ลดบัฟเฟอร์หมด/สะสมเมื่อใช้ต่อเนื่อง
- ผ่อนเสียงอย่างต่อเนื่องเมื่อแพ็กเก็ตหายระยะสั้น และปิด noise gate เป็นค่าเริ่มต้นเพื่อไม่ตัดปลายคำ

### ใช้ไมค์ขณะปิดหน้าจอ

- ส่งเสียงต่อผ่าน foreground microphone service เมื่อเชื่อมต่อเครื่องเล่นแล้ว
- แสดงสถานะไมค์พร้อมปุ่มหยุดในการแจ้งเตือน และปุ่มหยุดในแอป
- รักษาการเชื่อมต่อเมื่อสลับแอปหรือสร้าง Activity ใหม่ พร้อมคืน CPU/Wi-Fi locks เมื่อหยุด
- ไม่เริ่มบันทึกเองหลังรีบูตหรือ process ถูกปิด

### การเผยแพร่

- ใช้ชื่อรุ่นตาม Semantic Versioning เช่น KaraokeLink v0.2.0
- แจก APK แบบ release ที่เซ็นด้วยกุญแจถาวร พร้อม SHA-256 checksum
- ออกรุ่นอัตโนมัติเมื่อ push main และการตรวจสอบทั้งหมดผ่าน

## [0.1.0] — Preview

- เปิดเพลงในแอป ค้นหาเพลง และใช้โทรศัพท์อีกเครื่องเป็นไมค์ผ่าน LAN
- แก้หน้าเครื่องเล่นกลับหน้าแรกเมื่อเริ่มเพลงหรือกลับจากเบราว์เซอร์