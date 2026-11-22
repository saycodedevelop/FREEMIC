Micro không dây và trình phát karaoke cho điện thoại Android trên cùng Wi-Fi.

### Thay đổi

{changes}

### Tải và cài đặt

- Tải `KaraokeLink-{version}.apk` từ các tệp đính kèm bên dưới. Yêu cầu Android 9 trở lên.
- Cài cùng APK trên máy phát nhạc và mọi máy micro. Một máy phát nhạc hỗ trợ hai micro cùng lúc.
- Bản xem trước cũ dùng khóa ký khác nên cần gỡ một lần; các bản chính thức sau có thể cập nhật trực tiếp.

### Kiểm tra

Quy trình phát hành kiểm tra bản dựng Android/Lint, giao diện phát nhạc, dịch vụ micro tiền cảnh, tìm thiết bị và hai micro, cùng kiểm thử C++ bằng sanitizer và mô phỏng jitter/trôi đồng hồ.
Chất lượng Bluetooth, quản lý pin và âm thanh đồng thời vẫn cần kiểm tra trên thiết bị thật.

Dùng `SHA256SUMS.txt` để kiểm tra tính toàn vẹn của APK.
