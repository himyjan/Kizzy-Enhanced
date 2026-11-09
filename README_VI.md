<div align="center">
  <img width="2048" height="989" alt="17831909936168749184217530060427" src="https://github.com/user-attachments/assets/7a1b7c4d-3610-4541-b400-8a8c7ef91605" />
<h1><img src="https://github.com/user-attachments/assets/02086040-660d-42d8-8830-78053e1e05a4" height="30" style="vertical-align: middle;"> Kizzy Enhanced</h1>
<h4>Một nhánh (fork) cải tiến của <a href="https://github.com/dead8309/Kizzy">Kizzy</a> — trình quản lý Discord Rich Presence cho Android, được viết bằng Kotlin.</h4>
</div>

<div align="center">
<img src="https://img.shields.io/badge/Minimum%20SDK-27-%23?&style=flat-square&color=5b5ef7">
<img src="https://img.shields.io/badge/kotlin-5b5ef7.svg?logo=kotlin&logoColor=white&style=flat-square">
<img src="https://img.shields.io/badge/package-com.my.kizzy.enhanced-5b5ef7?style=flat-square">
<img src="https://img.shields.io/badge/name-Kizzy%20Enhanced-5b5ef7?style=flat-square">
<div align="center">

[![README in English](https://img.shields.io/badge/README-in%20English-blue?style=for-the-badge)](README.md)
[![README bằng tiếng Việt](https://img.shields.io/badge/README-bằng%20tiếng%20Việt-blue?style=for-the-badge)](README_VI.md)

</div>
</div>
<p>
<img src="https://github.com/user-attachments/assets/f8402300-d628-4104-8f86-dc6021c9fbbe" width=60%/>
</p>
</div>

> [!NOTE]
> **Kizzy Enhanced** là một bản fork cá nhân của [Kizzy](https://github.com/dead8309/Kizzy) bởi dead8309. Các tính năng bổ sung riêng của bản fork được đánh dấu bằng ⭐ bên dưới.

## Tính năng

- [x] Nút có thể nhấp
- [x] Phát hiện ứng dụng đang chạy hiện tại
- [x] Phát hiện phương tiện đang phát hiện hiện tại
- [x] Dấu thời gian tùy chọn
- [x] Trạng thái tùy chỉnh
- [x] Lưu/tải cấu hình hiện diện
- [x] Chủ đề Material You
- [x] Bản dịch
- [x] Thiết lập đơn giản
- [x] Giả lập Console RPC
- [x] Tạo cấu hình tùy chỉnh với hình ảnh và liên kết của riêng bạn
- [x] Xem trước RPC ngay trong ứng dụng
- [x] Chạy ngầm ngay cả khi màn hình tắt
- [x] Hỗ trợ GIF
- [x] Hỗ trợ URL bên ngoài (có nghĩa là bạn có thể cung cấp một URL trỏ đến hình ảnh trên web và Discord sẽ hiển thị nó!)
- [x] Sử dụng hình ảnh từ thư viện
- [x] ⭐ Spoofer nền tảng (Desktop / Web / Android / iOS / Xbox / Playstation / VR)
- [x] ⭐ ID ứng dụng tùy chỉnh — sử dụng biểu tượng và tên ứng dụng Discord của riêng bạn
- [x] ⭐ Các biến mẫu bổ sung (`{{media_album}}`, `{{battery}}`, `{{playback}}` và nhiều hơn nữa)
- [x] ⭐ Trình chọn chế độ dấu thời gian (Mặc định phương tiện/Ứng dụng · Thời gian hiện tại trong ngày · ms tùy chỉnh)
- [x] ⭐ Thiết kế lại màn hình chính — thẻ toàn chiều rộng với công tắc bật/tắt
- [x] ⭐ Tự động kết nối lại cổng thông tin với exponential backoff (không còn bị ngắt sau 1–2 giờ)
- [x] ⭐ Vòng lặp phát hiện không thể phá vỡ — lỗi mạng không còn làm dừng phát hiện ứng dụng/trò chơi
- [x] ⭐ Thăm dò chuyển đổi ứng dụng mỗi 2 giây (trước đây là 5s) để phát hiện trò chơi nhanh hơn

## Có gì mới trong bản fork này

Tất cả các bổ sung nằm ở màn hình **Experimental RPC** và màn hình chính.

### ⭐ Spoofer nền tảng
- Chọn nền tảng mà trạng thái hiện diện của bạn kết nối từ — **Desktop, Web, Android, iOS, Xbox, Playstation, VR** (được port từ các nền tảng của platformSpoofer trên Equicord).
- Hoạt động ở cấp độ `IDENTIFY` của gateway (trạng thái client), do đó được nhận diện chính xác bởi các plugin như **platformindicators**.
- Áp dụng **trực tiếp (live)** — dịch vụ tự khởi động lại, không cần bật tắt thủ công.

### ⭐ ID ứng dụng tùy chỉnh (biểu tượng riêng)
- Trình chọn **Built-in / Custom**. Built-in giữ nguyên biểu tượng mặc định; Custom hiển thị một trường để bạn dán **Application ID** Discord của riêng mình, giúp hoạt động hiển thị biểu tượng và tên **của bạn**.

### ⭐ Biến mẫu bổ sung
Ngoài các biến ban đầu, có hỗ trợ tự động hoàn thành:
- `{{media_album}}`, `{{media_album_artist}}`, `{{media_duration}}`, `{{media_writer}}`, `{{media_composer}}`
- `{{battery}}` — phần trăm pin
- `{{playback}}` — trạng thái phát (▶ / ⏸)

### ⭐ Trình chọn chế độ dấu thời gian
- **Phương tiện / Ứng dụng (mặc định)** — dấu thời gian từ bài hát/ứng dụng hiện tại.
- **Giống thời gian hiện tại trong ngày** — Discord hiển thị thời gian hiện tại là thời gian đã trôi qua (ví dụ lúc 15:30 → hiển thị "đã trôi qua 15:30"). Cùng logic với customRPC của Vencord.
- **Tùy chỉnh** — tự nhập **Dấu thời gian Bắt đầu / Kết thúc (tính bằng mili giây)** của riêng bạn.

### ⭐ Thiết kế lại màn hình chính
- Các thẻ tính năng nay là một chồng thẻ toàn chiều rộng theo chiều dọc.
- **Công tắc bật/tắt trên mọi thẻ**, giúp Media / Custom / Experimental RPC có thể được bật và tắt ngay từ màn hình chính.

### ⭐ Khả năng phục hồi của Gateway (được port từ [YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy))
- Tự động kết nối lại với exponential backoff khi ngắt kết nối bất ngờ.
- Khôi phục phiên khi có thể; quay lại identify mới sau 3 lần thử thất bại.
- Các mã đóng gây tử vong (4004, 4010–4014) vẫn dừng vĩnh viễn.
- Giới hạn 60 giây đối với thời gian chờ cập nhật trạng thái (trước đây là vòng lặp vô hạn).
- Vòng lặp phát hiện giờ được bọc trong try/catch — một lỗi mạng đơn lẻ không còn làm dừng phát hiện chuyển đổi ứng dụng cho đến khi khởi động lại thủ công.
- Thăm dò chuyển đổi ứng dụng: **2s** (trước đây là 5s).
- Sửa lỗi: chuyển đổi giữa hai ứng dụng được bật nay cập nhật trạng thái hiện diện ngay lập tức.

### Sửa lỗi
- Sửa lỗi sập khi **"Chia sẻ nhật ký"** (định danh FileProvider giờ tuân theo ID gói).
- Sửa lỗi sập khi chuyển đổi các tính năng RPC từ màn hình chính.

## Yêu cầu hệ thống
- Hệ điều hành: Android 8.1 đến 16
- RAM: Tối thiểu 3 GB

## Tải xuống
Tải tệp `app-release.apk` đã ký mới nhất từ trang [**Releases**](../../releases/latest).

**Kizzy Enhanced** sử dụng ID gói **`com.my.kizzy.enhanced`**, vì vậy nó có thể cài đặt song song với Kizzy gốc nếu bạn đang có sẵn.

> [!WARNING]
> Ứng dụng này sử dụng kết nối Discord Gateway. Hãy tự chịu rủi ro khi sử dụng.

## Xây dựng (Build)
> Yêu cầu tiên quyết: Android Studio và sự quen thuộc với Gradle, Kotlin, và Jetpack Compose.

```console
git clone https://github.com/himyjan/Kizzy-Enhanced.git
```
Mở dự án trong Android Studio, nhập (import) dự án, sau đó Build và Run. Các bản build phát hành được tạo tự động bởi GitHub Actions workflow **Build Release Apk**.

## Tác giả & Đóng góp (Credits)
✨ [Kizzy](https://github.com/dead8309/Kizzy) cho dự án Kizzy gốc.

✨ [YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy) cho các bản vá khả năng phục hồi cổng thông tin.

✨ [Read You](https://github.com/Ashinch/ReadYou) và [Seal](https://github.com/JunkFood02/Seal) cho các thành phần UI.

✨ [Material Color Utilities](https://github.com/material-foundation/material-color-utilities)

✨ [Rich-Presence-U](https://github.com/ninstar/Rich-Presence-U) cho dữ liệu trò chơi Nintendo và Wii U.

✨ [Logra](https://github.com/wingio/Logra) cho giao diện nhật ký.

✨ [Xbox-Rich-Presence-Discord](https://github.com/MrCoolAndroid/Xbox-Rich-Presence-Discord) cho dữ liệu trò chơi Xbox.

✨ [Monet](https://github.com/Kyant0/Monet) cho bảng màu Material3.

## Giấy phép (License)
**Kizzy** là một dự án mã nguồn mở theo Giấy phép Mã nguồn Mở GNU GPL 3.0, cho phép bạn sử dụng, tham khảo và sửa đổi mã nguồn miễn phí, nhưng không cho phép mã đã sửa đổi và phái sinh được phân phối và bán dưới dạng phần mềm thương mại mã nguồn đóng.
