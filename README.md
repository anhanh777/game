# J2ME Multi-Box Runner & Account Manager

Ứng dụng Android chạy đa tài khoản (Multi-Box) cho game Java (`.jar`/`.jad`) dựa trên kiến trúc lõi **J2ME-Loader**, tối ưu cho treo acc chạy nền (AFK) và bảo mật tài khoản.

## Tính năng chính
1. **Chạy game Java (`.jar`/`.jad`)**: Hỗ trợ cảm ứng trực tiếp, bàn phím ảo trên màn hình, tùy chỉnh độ phân giải riêng cho từng Box.
2. **Box đa tài khoản (Kiến trúc Đa tiến trình `:box_1`..`:box_8`)**:
   - Dữ liệu/RMS của từng Box được cô lập hoàn toàn trong `/files/boxes/{boxId}/rms/`.
   - Trang chủ Dashboard hiển thị tất cả Box -> Click vào Box để phóng to toàn màn hình (`BoxActivity`) -> Bấm Thu nhỏ để quay về Dashboard mà game vẫn chạy ngầm.
3. **Treo acc chạy nền (AFK)**:
   - `BoxRunnerService` (`Foreground Service` + `PARTIAL_WAKE_LOCK` + `WifiLock`).
   - Tách vòng lặp Render & Logic: khi thu nhỏ hoặc tắt màn hình, GPU render về 0 FPS để tiết kiệm pin trong khi luồng mạng vẫn hoạt động.
   - `ReconnectWatchdog` tự khởi động lại sạch (Clean Relaunch) với exponential backoff khi rớt mạng.
4. **Quản lý tài khoản bảo mật & Macro đăng nhập**:
   - Mã hóa AES-256-GCM bằng **Android Keystore** + **SQLCipher** + `EncryptedSharedPreferences`.
   - Mật khẩu chỉ giải mã dạng `CharArray` trong RAM đúng lúc chạy Macro và xóa sạch ngay sau khi xong.
   - 100% cục bộ, không có thư viện theo dõi/analytics/telemetry.
   - Bộ Macro bàn phím dùng chung cho mọi game Java, hỗ trợ tạm dừng thông báo nhập tay khi gặp Captcha.

## Build & Test
Mở thư mục dự án bằng **Android Studio (JDK 17, Android SDK 34)** hoặc chạy:
```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```
GitHub Actions CI đã được cấu hình sẵn trong `.github/workflows/android-ci.yml` để tự động chạy test và build file `app-debug.apk` khi push code.
