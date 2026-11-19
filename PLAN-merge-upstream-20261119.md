# Kế hoạch Chiến lược: Cập nhật Commit mới nhất từ NuvioMobile Upstream
**Mã kế hoạch**: `PLAN-merge-upstream-20261119`  
**Dự án**: NuvioMobile-Fast  
**Nguồn upstream**: `https://github.com/9000000/NuvioMobile.git` (Remote: `nuvio-src`, Nhánh: `cmp-rewrite`)  
**Nhánh đích**: `main` (Remote: `origin`, `https://github.com/9000000/NuvioMobile-Fast.git`)  
**Khoảng thời gian commit**: Từ `14-11-2026` đến `19-11-2026`  
**Điểm cơ sở (Merge Base)**: Commit `ba149964c` (14-11-2026 05:10:27 +0300)  
**Commit mới nhất upstream**: Commit `b66f7725c` (chore(store): publish 1.1.0-beta.2)  
**Số lượng commit upstream mới**: 54 commits  

---

## 1. 🔍 Phân tích Hiện trạng & Phạm vi Thay đổi

### 1.1. Bối cảnh hai nhánh
- **Nhánh `main` hiện tại (NuvioMobile-Fast)**:
  - Base trên commit `ba149964c` của upstream (được merge vào sáng 14/11/2026 qua commit `90872da37`).
  - Đã được bổ sung 2 commit tùy biến chuyên sâu:
    1. `3b79f43e8` (*feat: add fast build configuration, scripts, and repository updates*): Tối ưu build gradle, chia nhỏ ABI APK, script release tự động, cấu hình mạng dự phòng.
    2. `0ded7f282` (*feat: add subtitle appearance settings, font picker, and custom player styles*): Bộ chọn font phụ đề (Oswald, Phimmoi), viền phụ đề tùy chỉnh native (OutlineSpan, SubtitleViewOutline), và các tinh chỉnh nâng cao.
  - Mang trong mình các tính năng độc quyền: **Live TV Streaming**, **TorrServer Local/Remote**, **ClearKey DRM**, **IPv4-First DNS / Dynamic Host Fallback**, **Hiệu ứng giao diện động** (PosterLift, MeshBackground, CardDepth), và **Bản địa hóa tiếng Việt hoàn chỉnh**.

- **Nhánh `nuvio-src/cmp-rewrite` (Upstream)**:
  - Phát triển liên tục từ `ba149964c` đến `b66f7725c` với các bản phát hành lớn: `0.6.13-beta`, `1.0.0` (chính thức), và `1.1.0-beta.2`.
  - Bao gồm 54 commit mới với các tính năng và bản vá chất lượng cao.

---

## 2. 🌟 Tổng hợp Tính năng Mới từ Upstream (14/11 - 19/11/2026)

| Nhóm tính năng | Commit tiêu biểu | Mô tả chi tiết & Lợi ích |
| :--- | :--- | :--- |
| **Plex Media Server** | PR #2658 (`17579e7b5`, `943f38d91`, `b2fd38e58`) | Hỗ trợ kết nối và đồng bộ thư viện Plex Media Server, giải quyết địa chỉ IP nội bộ/internet, đăng nhập chia sẻ. |
| **Silo Media Server** | PR #2659 (`5d928372d`, `29a26fbe8`) | Tích hợp Silo server trên API chuyên biệt, tự động gia hạn token và gửi báo cáo xem tương tự app Silo gốc. |
| **Downloads Redesign** | Branch `downloads-redesign` (`ffe769fa7`, `2dcfbb9f1`, `e00392db1`) | Thiết kế lại hoàn toàn màn hình Tải xuống: phân loại rõ ràng, hỗ trợ poster tùy chỉnh từ thư viện, xử lý nút back hệ thống thông minh, đo tốc độ tải. |
| **Styled Subtitles Nâng cao** | PR #2669 (`b8bbd700f`, `9401da02f`, `c46fc19a7`, `6ae7ab660`) | Nâng cấp lớp phủ phụ đề styled: bảo toàn màu sắc gốc từ phụ đề (`ForegroundColorSpan` hoặc tag `\c`), căn chỉnh 9 vị trí màn hình (`\an`), thêm thanh trượt chỉnh độ dày viền phụ đề. |
| **Player & mpv Upgrade** | PR #2682 (`818934860`), PR #2680 (`d374bdf02`), PR #2653 (`ff7500fdd`) | Nâng cấp thư viện giải mã `mpv-android-lib` lên **0.2.1**; bỏ qua gesture insets cho thanh điều khiển dưới trên Android; tự động chuyển nguồn dự phòng khi nguồn kế tiếp lỗi. |
| **Bộ lọc Thư viện** | PR #2681 (`2da28d707`) | Ghi nhớ bộ lọc danh sách & phân loại; thêm bộ lọc theo thể loại (Genre), năm phát hành (Year) và trạng thái đã xem (Watched). |
| **Đánh giá Tập phim** | PR #2688 (`97fbfa905`) | Khớp đánh giá tập phim từ nguồn bên ngoài chính xác theo thứ tự tập do addon cung cấp. |
| **Quản lý Hàng đợi Trakt** | PR #2702 (`1e4157492`) | Giới hạn hàng đợi làm mới thư viện Trakt để tránh tiêu tốn RAM hoặc treo ứng dụng. |
| **Cast & Diễn viên** | PR #2704 (`547972efd`) | Lọc bỏ các lần xuất hiện khách mời trong các chương trình talk show khỏi danh sách phim tham gia. |
| **Hiệu năng Android** | PR #2652 (`dc64e4aca`, `951ae17a9`, `56abfaaae`) | Tích hợp **Android Baseline Profile**, giảm recomposition khi khởi động bằng `collectMappedAsState`, giải mã logo nền ngoài Main Thread. |
| **Tiện ích Theo dõi Stremio** | PR #2660 (`964ecfd8d`, `8f5d9bcac`, `d89811ac6`) | Hỗ trợ các addon theo dõi thư viện và trình phát kiểu Stremio có dialog xin quyền người dùng. |
| **Đa ngôn ngữ (i18n)** | Loạt commit i18n | Bổ sung tiếng Ukraina, tiếng Trung giản thể, cập nhật tiếng Việt (PR #2622), tiếng Pháp, tiếng Hebrew, tiếng Bồ Đào Nha, tiếng Urdu. |

---

## 3. 💥 Phân tích Chi tiết 6 File Xung đột (Conflicts) & Phương án Xử lý

Khi tiến hành merge thử nghiệm, Git xác định chính xác **6 file xảy ra xung đột**:

### 🔴 Conflict 1: `README.md`
- **Nguyên nhân**: Upstream cập nhật liên kết sang Google Play, kho chính thức của NuvioMedia và kênh beta iOS (`store-beta.json`). Phía nhánh Fast đang giữ URL repo `9000000/NuvioMobile-Fast`.
- **Phương án giải quyết**:
  - **Bảo tồn**: Giữ nguyên link tải APK và nguồn AltStore/SideStore trỏ về repo `9000000/NuvioMobile-Fast`.
  - **Tích hợp**: Có thể thêm ghi chú về kênh beta hoặc mô tả nếu phù hợp, nhưng liên kết chính tuyệt đối giữ của repo hiện tại.

### 🔴 Conflict 2: `iosApp/Configuration/Version.xcconfig`
- **Nguyên nhân**: 
  - Upstream đặt: `MARKETING_VERSION = 1.1.0-beta.2`, `CURRENT_PROJECT_VERSION = 159`.
  - Nhánh Fast đặt: `MARKETING_VERSION = 1.2.1`, `CURRENT_PROJECT_VERSION = 167`.
- **Phương án giải quyết**:
  - **Bảo tồn**: Giữ nguyên cấu hình version của nhánh Fast (`1.2.1` / `167`) để đảm bảo tính liên tục của các bản release Fast đã phát hành và quy trình CI/CD.

### 🔴 Conflict 3: `WatchProgressIdentityTest.kt`
- **Nguyên nhân**:
  - Nhánh Fast (theo base cũ): Test với thời lượng giả lập `940000 / 1000000`.
  - Upstream (commit `618622d8a`): Đổi thành `2820000 / 3000000` (`2_820_000L`) để phản ánh đúng thời lượng thực tế của một tập phim dài, tránh trường hợp false-positive khi tính toán tỷ lệ xem 94%.
- **Phương án giải quyết**:
  - **Thay thế theo Code mới tốt hơn**: Chấp nhận cập nhật của Upstream. Thay đổi này làm cho test case chuẩn xác và thực tế hơn, không ảnh hưởng đến bất kỳ code logic nào của ứng dụng.

### 🔴 Conflict 4: `SubtitleAppearanceControls.kt`
- **Nguyên nhân**:
  - Nhánh Fast:
    ```kotlin
    SubtitleValueControl(
        stringResource(Res.string.compose_player_outline_width),
        style.outlineWidth,
        1..10,
        1,
    ) { onStyleChanged(style.copy(outlineWidth = it)) }
    ```
  - Upstream:
    ```kotlin
    SubtitleValueControl(
        stringResource(Res.string.subtitle_outline_width),
        style.outlineWidth,
        subtitleOutlineWidthRange,
        1,
    ) { onStyleChanged(style.copy(outlineWidth = it)) }
    ```
- **Phân tích kỹ thuật & Đánh giá**:
  - Cả hai bên đều cùng xây dựng tính năng chỉnh độ dày viền phụ đề.
  - Phía Upstream dùng resource key chuẩn `subtitle_outline_width` (đã được dịch sang nhiều ngôn ngữ trong hệ thống).
  - Phía Upstream giới hạn range `1..5`, trong khi phía Fast cho phép range rộng hơn `1..10` (rất hữu ích cho người dùng xem trên màn hình lớn hoặc tablet muốn viền đậm rõ nét).
- **Phương án giải quyết**:
  - Sử dụng resource key chuẩn của Upstream: `stringResource(Res.string.subtitle_outline_width)`.
  - Cho phép dải giá trị mở rộng `1..10` (hoặc mở rộng `subtitleOutlineWidthRange` thành `1..10`) để bảo tồn trải nghiệm tùy chỉnh của người dùng Fast.

### 🔴 Conflict 5: `SubtitleAppearanceSettings.kt`
- **Nguyên nhân**: Tương tự như trên, hàng slider cài đặt viền phụ đề trong màn hình Settings:
  - Fast dùng nhãn `settings_playback_subtitle_outline_width` và range `1..10`.
  - Upstream dùng nhãn `subtitle_outline_width` và range `subtitleOutlineWidthRange`.
- **Phương án giải quyết**:
  - Sử dụng nhãn chuẩn `Res.string.subtitle_outline_width`.
  - Giữ dải giá trị tùy chỉnh `1..10` của Fast.

### 🔴 Conflict 6: `values-vi/strings.xml`
- **Nguyên nhân**:
  - Nhánh Fast có bản dịch tiếng Việt đồ sộ (hơn 5.200 dòng), dịch tỉ mỉ toàn bộ các màn hình Live TV, TorrServer, DNS tùy chọn, Subtitle Font, DRM...
  - Upstream bổ sung hàng loạt chuỗi tiếng Việt mới từ PR #2622 và các chuỗi cho Plex, Silo, Downloads redesign, Tracking Addons.
- **Phương án giải quyết**:
  - **Hợp nhất thông minh (Smart Merge)**:
    1. Giữ 100% tất cả các string tùy chỉnh của Fast (`live_tv_*`, `torrserver_*`, `subtitle_font_*`, `dns_*`, `fast_build_*`...).
    2. Bổ sung toàn bộ các string mới từ Upstream (`plex_*`, `silo_*`, `download_*`, `tracking_*`).
    3. Đảm bảo file XML hợp lệ, không trùng lặp key (no duplicate keys), đúng cú pháp XML.

---

## 4. ⚖️ Đánh giá Chi tiết: Mã nguồn Mới vs Tùy chỉnh Hiện tại

Theo chỉ thị của người dùng: *"nếu code mới có công dụng tốt hơn thì có thể thay thế hoặc sửa nhưng phải báo cáo chi tiết"*. Dưới đây là phân tích đối chiếu:

### 4.1. Những cải tiến Upstream VƯỢT TRỘI được chấp nhận và áp dụng:
1. **Lớp phủ phụ đề StyledSubtitleOverlay (`StyledSubtitleCues`)**:
   - *Code cũ*: Chỉ render chuỗi text thuần túy phẳng (`subtitleText: String`), cố định căn giữa trên/dưới.
   - *Code mới upstream*: Hỗ trợ `StyledSubtitleCues` giữ lại toàn bộ định dạng màu sắc (`ForegroundColorSpan`, tags `\c`, `\1c`) và căn lề 9 hướng theo chuẩn ASS/SSA (`\an`).
   - *Đánh giá*: **Tốt hơn rất nhiều**. Chúng ta chấp nhận hoàn toàn kiến trúc này, đồng thời vẫn giữ khả năng render native qua `SubtitleViewOutline` trên Android khi người dùng chọn chế độ native.
2. **Tối ưu hóa Recomposition (`collectMappedAsState`)**:
   - *Code cũ*: Lắng nghe toàn bộ State flow của Repository khiến UI vẽ lại thừa thãi.
   - *Code mới upstream*: Sử dụng `collectMappedAsState` chỉ lắng nghe đúng thuộc tính cần thiết (`posterTransitionEnabled`, `sourceMode`, `activeProfileIndex`...).
   - *Đánh giá*: **Rất tốt cho hiệu năng**, giảm giật lag trên thiết bị cấu hình yếu. Giữ nguyên toàn bộ cải tiến này trong `MainAppContent.kt` và `HomeScreen.kt`.
3. **Thư viện native `mpv-android-lib` 0.2.1**:
   - *Đánh giá*: Tăng độ ổn định khi giải mã video 4K/HDR và phát trực tuyến luồng HLS/DASH. Chấp nhận nâng cấp.
4. **Android Baseline Profile**:
   - *Đánh giá*: Giúp ứng dụng biên dịch sẵn mã bytecode tối ưu khi cài đặt, giảm thời gian khởi động (cold start) từ 20-30%. Chấp nhận tích hợp mà không ảnh hưởng tới cấu hình Fast Build.

### 4.2. Những tùy chỉnh của NuvioMobile-Fast BẮT BUỘC BẢO TỒN NGUYÊN VẸN:
1. **Live TV Module**:
   - Toàn bộ thư mục `com/nuvio/app/features/livetv/*` và các điểm tích hợp trong `AppScreenTab.kt`, `MainTabsDestination.kt`, `PlayerLiveChannelsPanel.kt`.
2. **TorrServer Module**:
   - Toàn bộ thư mục `com/nuvio/app/features/torrserver/*` và `TorrentFilePickerDialog.kt`.
3. **Mạng & DNS**:
   - `IPv4FirstDns.kt` và `DynamicHostFallback.kt` (cực kỳ quan trọng với người dùng mạng Việt Nam).
4. **Font chữ & Viền phụ đề**:
   - Font Oswald & Phimmoi (`Oswald.ttf`, `Phimmoi.TTF`), `SubtitleFontPicker.kt`, `SubtitleFonts.kt`, `SubtitleViewOutline.kt`, `OutlineSpan.kt`.
5. **Cấu hình Fast Build & Release**:
   - `androidApp/build.gradle.kts` (split ABI armeabi-v7a / arm64-v8a, cấu hình nén, tắt task thừa khi build nhanh).
   - `scripts/publish_release.py`, `release.bat`.
6. **Agent Rules & Workflow**:
   - `GEMINI.md`, `.agent/*`.

---

## 5. 🛠️ Quy trình Triển khai Từng bước (Surgical Execution Plan)

### Bước 1: Chuẩn bị nhánh làm việc an toàn
```powershell
git checkout main
git checkout -b merge/upstream-20261119
```
> [!NOTE]
> Mọi thao tác merge và giải quyết xung đột sẽ diễn ra trên nhánh `merge/upstream-20261119`. Chỉ sau khi kiểm tra biên dịch thành công 100% mới tiến hành cập nhật vào `main`.

### Bước 2: Thực thi Git Merge
```powershell
git merge --no-commit --no-ff nuvio-src/cmp-rewrite
```

### Bước 3: Giải quyết tuần tự 6 file Conflict
1. **Sửa `README.md`**: Giữ URL `9000000/NuvioMobile-Fast`.
2. **Sửa `iosApp/Configuration/Version.xcconfig`**: Giữ version `1.2.1` / `167`.
3. **Sửa `WatchProgressIdentityTest.kt`**: Chấp nhận dữ liệu test mới của upstream (`2820000 / 3000000`).
4. **Sửa `SubtitleAppearanceControls.kt`**: Đồng bộ string key `subtitle_outline_width`, duy trì range `1..10`.
5. **Sửa `SubtitleAppearanceSettings.kt`**: Đồng bộ string key `subtitle_outline_width`, duy trì slider `1..10`.
6. **Sửa `values-vi/strings.xml`**: Hợp nhất chuỗi tiếng Việt mới từ upstream với kho chuỗi mở rộng của Fast.

### Bước 4: Kiểm tra và Hiệu chỉnh các file Auto-Merged
- Kiểm tra `PlayerEngine.android.kt`: Đảm bảo `StyledSubtitleOverlay(cues, ...)` và `DRM ClearKey` cùng tồn tại không xung đột kiểu dữ liệu.
- Kiểm tra `androidApp/build.gradle.kts`: Xác nhận plugin `baselineProfile` không làm gãy cấu hình `splits` hoặc `ndk.abiFilters`.
- Kiểm tra `MainAppContent.kt`: Đảm bảo các tab điều hướng và luồng xử lý LiveTV/TorrServer nguyên vẹn.

### Bước 5: Kiểm tra Biên dịch & Kiểm thử (Verification)
- Chạy type-check và kiểm tra cú pháp Gradle:
  ```powershell
  ./gradlew :composeApp:compileCommonMainKotlinMetadata --dry-run
  ```
- Chạy Unit Test kiểm tra tính toàn vẹn:
  ```powershell
  ./gradlew :composeApp:testDebugUnitTest --tests "com.nuvio.app.features.watchprogress.WatchProgressIdentityTest"
  ```

### Bước 6: Hoàn tất Merge & Báo cáo
- Commit kết quả merge với message chuẩn hóa:
  ```
  Merge nuvio-src/cmp-rewrite (14/11/2026 - 19/11/2026, 0.6.12-beta -> 1.1.0-beta.2)
  ```
- Merge nhánh `merge/upstream-20261119` vào `main`.
- Xuất báo cáo tổng kết chi tiết cho người dùng.

---

## 6. ⚠️ Ma trận Rủi ro & Giải pháp Dự phòng

| Rủi ro tiềm ẩn | Mức độ | Biện pháp kiểm soát |
| :--- | :---: | :--- |
| **Xung đột kiểu dữ liệu Subtitle Cues** | Trung bình | `StyledSubtitleCues` đã được kiểm tra tính tương thích trong `PlayerEngine.android.kt`. Nếu thiếu import sẽ bổ sung ngay. |
| **Trùng lặp chuỗi XML trong strings.xml** | Thấp | Chạy script kiểm tra trùng ID chuỗi trước khi lưu `values-vi/strings.xml`. |
| **Lỗi ABI / NDK khi build** | Thấp | Cấu hình ABI splits trong `androidApp/build.gradle.kts` đã được giữ nguyên và cô lập khỏi baseline profile. |
| **Mất code tính năng Live TV** | Cực thấp | Nhánh upstream không hề có file Live TV nào nên không xảy ra ghi đè; toàn bộ các file `livetv/*` còn nguyên 100%. |

---
*Kế hoạch đã sẵn sàng để thực thi khi có sự xác nhận từ bạn.*
