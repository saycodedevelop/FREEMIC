# KaraokeLink

[ไทย](README.md) · [English](README.en.md) · [简体中文](README.zh-CN.md) · [Tiếng Việt](README.vi.md) · [Bahasa Indonesia](README.id.md)

Biến điện thoại thành micro không dây và phát karaoke trên máy khác qua cùng Wi-Fi. Một máy phát nhạc hỗ trợ **hai micro cùng lúc**. Cài cùng APK trên mọi máy. Yêu cầu Android 9 trở lên.

**[Tải bản mới nhất](https://github.com/saycodedevelop/FREEMIC/releases/latest)** · [Nhật ký thay đổi đa ngôn ngữ](CHANGELOG.md)

## Giao diện

![Chọn máy phát nhạc hoặc micro](docs/screenshots/home.png)

[Micro](docs/screenshots/microphone.png) · [Bộ trộn](docs/screenshots/mixer.png) · [Hướng dẫn](docs/screenshots/getting-started.png)

Ảnh dựng bằng Android với trạng thái mô phỏng và chữ tiếng Thái. [Hướng dẫn sử dụng (tiếng Thái)](docs/USER_GUIDE.md).

## Bắt đầu

1. Cài APK trên máy phát nhạc và từng máy micro; kết nối tất cả vào cùng Wi-Fi.
2. Trên điện thoại nối loa, chọn **Mở trình phát nhạc**, rồi tìm bài hát hoặc dán liên kết HTTPS.
3. Trên từng máy dùng để hát, chọn **Dùng điện thoại này làm mic**, cấp quyền micro/thông báo và chọn cùng máy phát nhạc.

Có thể bỏ qua hướng dẫn lần đầu và mở lại bằng nút **?** trên mọi màn hình.

## Tìm máy phát nhạc và kết nối hai micro

Ứng dụng dùng Android NSD/mDNS cùng UDP broadcast và xác nhận máy phát nhạc bằng thăm dò unicast. Kết quả hiển thị hãng, mẫu máy và IP. Hai micro kết nối được hiển thị là **2/2**; dừng một micro không ngắt máy còn lại.

Nếu không tìm thấy, chạm trạng thái máy phát nhạc để xem IP, rồi chọn **Kết nối bằng IP** trên micro. Wi-Fi khách, cách ly AP/thiết bị, một số điểm phát sóng và VPN có thể chặn liên lạc; mạng phải cho phép các thiết bị truy cập nhau.

## Hát khi màn hình micro tắt

Micro đã kết nối tiếp tục gửi âm thanh khi tắt màn hình hoặc chuyển ứng dụng. Dừng từ thông báo hoặc nút **Dừng micro** trong ứng dụng. Không tự ghi âm lại sau khi khởi động lại máy hoặc tiến trình bị đóng.

Máy phát nhạc tạm dừng âm thanh khi chạy nền hoặc tắt màn hình; hãy giữ màn hình phát nhạc mở.

## Âm thanh và mạng

- Mỗi micro có gain và hiệu ứng riêng. Noise gate mặc định tắt; có thể bật trong cài đặt nâng cao.
- Có sắp xếp lại gói tin, bộ đệm thích ứng, giảm âm khi mất gói ngắn và bù trôi đồng hồ âm thanh.
- Nên dùng loa có dây hoặc USB để hát trực tiếp. Bluetooth có độ trễ tùy thiết bị và có thể giật âm.
- Âm lượng nhạc HTML5 chỉ điều chỉnh được trên trang hỗ trợ. Android trộn nhạc và âm thanh micro.
- RTT và hàng đợi không đo độ trễ từ miệng đến loa. Thêm độ trễ không loại bỏ độ trễ Bluetooth.
- Âm thanh micro trong LAN hiện chưa mã hóa; hãy dùng mạng tin cậy.

## Ngôn ngữ

Hỗ trợ tiếng Thái, Anh, Trung giản thể, Việt và Indonesia. Ứng dụng theo ngôn ngữ điện thoại, dùng tiếng Anh nếu không hỗ trợ. Chọn **Ngôn ngữ** tại trang chủ hoặc dùng cài đặt ngôn ngữ ứng dụng trên Android 13 trở lên. Đổi ngôn ngữ trước khi bắt đầu phiên. Ghi chú phát hành có đủ năm ngôn ngữ.

## Cài đặt và phát triển

Tệp chính thức là `KaraokeLink-<version>.apk` ký bằng khóa phát hành cố định, kèm `SHA256SUMS.txt`. Bản xem trước cũ dùng khóa khác nên cần gỡ một lần; các bản chính thức sau có thể cập nhật trực tiếp.

Dùng JDK 17 hoặc 21, Android SDK 35, NDK 27.0.12077973 và CMake 3.22.1. Đặt `JAVA_HOME` và `ANDROID_HOME`, rồi chạy:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

APK gỡ lỗi: `app/build/outputs/apk/debug/app-debug.apk`.

[Kiến trúc (Thái/Anh)](docs/ARCHITECTURE.md) · [Giao thức LAN (Thái)](docs/PROTOCOL.md) · [Kiểm thử thiết bị (Thái/Anh)](docs/TESTING.md) · [Quy trình phát hành (Anh)](docs/RELEASING.md)
