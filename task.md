# Task Management: NuvioMobile Fast

## 📋 Trạng thái Hiện tại: HOÀN TẤT (100% SUCCESS)

---

### 📺 Tính năng: Cải tiến & Tối ưu hóa Live TV (`PLAN-live-tv-improvements`)
*Kế hoạch chi tiết: [`PLAN-live-tv-improvements.md`](file:///f:/MyGithub/NuvioMobile-Fast/PLAN-live-tv-improvements.md)*

- [x] **TASK-LTV-01**: Bổ sung hàm lưu/nạp `lastSelectedPlaylistId` vào `LiveTvStorage` (KMP expect/actual: `LiveTvStorage.kt`, `LiveTvStorage.android.kt`, `LiveTvStorage.ios.kt`). *(Phụ trách: storage_dev - HOÀN TẤT)*
- [x] **TASK-LTV-02**: Đồng bộ `lastSelectedPlaylistId` trong `LiveTvRepository` và xử lý xóa playlist. *(Phụ trách: storage_dev - HOÀN TẤT)*
- [x] **TASK-LTV-03**: Cập nhật chuỗi giao diện trong `strings.xml` (values-vi & values): "Chọn danh sách phát" ➔ "List Kênh", "Chọn danh mục" ➔ "Danh mục", nhãn yêu thích ➔ "❤️ ⭐". *(Phụ trách: ui_dev - HOÀN TẤT)*
- [x] **TASK-LTV-04**: Tái cấu trúc hàng chip trong `LiveTvFilterPanelRow.kt`:
  - [x] Bỏ chip "Tất cả kênh" ở hàng ngoài.
  - [x] Giữ tùy chọn "Tất cả kênh" bên trong BottomSheet "Danh mục".
  - [x] Hiển thị chip Yêu thích bằng biểu tượng Trái tim và Ngôi sao (`❤️ ⭐`).
  - [x] Đổi tên chip "Chọn danh mục" thành "Danh mục". *(Phụ trách: ui_dev - HOÀN TẤT)*
- [x] **TASK-LTV-05**: Cập nhật `LiveTvScreen.kt` để tự động phục hồi playlist đã lưu, mặc định khi mở luôn hiển thị và trỏ vào mục Yêu thích (`❤️ ⭐`). *(Phụ trách: ui_dev - HOÀN TẤT)*
- [x] **TASK-LTV-06**: Chạy biên dịch kiểm thử `compileCommonMainKotlinMetadata` và Unit Test `LiveTvRepositoryTest` xác nhận hoạt động ổn định. *(Phụ trách: verifier - BUILD SUCCESSFUL)*
- [x] **TASK-LTV-07**: Bổ sung mục "Xem gần đây" (hiển thị tất cả kênh vừa xem ở mọi list, lưu trữ `recentChannelIds`), kèm chuỗi điều hướng thông minh khi mở app: Ưu tiên Yêu thích ➔ Nếu trống trỏ vào Xem gần đây ➔ Nếu cả 2 đều trống trỏ vào Tất cả kênh. *(HOÀN TẤT)*

---

### 📦 Lưu trữ: Các đợt công việc đã hoàn tất

#### Upstream Merge (14/11/2026 - 19/11/2026) - HOÀN TẤT (100%)
- [x] **TASK-01**: Khởi tạo nhánh làm việc an toàn (`merge/upstream-20261119`) từ `main`
- [x] **TASK-02**: Thực hiện lệnh `git merge --no-commit --no-ff nuvio-src/cmp-rewrite`
- [x] **TASK-03**: Giải quyết Conflict 1 - `README.md`
- [x] **TASK-04**: Giải quyết Conflict 2 - `iosApp/Configuration/Version.xcconfig`
- [x] **TASK-05**: Giải quyết Conflict 3 - `WatchProgressIdentityTest.kt`
- [x] **TASK-06**: Giải quyết Conflict 4 - `SubtitleAppearanceControls.kt`
- [x] **TASK-07**: Giải quyết Conflict 5 - `SubtitleAppearanceSettings.kt`
- [x] **TASK-08**: Giải quyết Conflict 6 - `values-vi/strings.xml`
- [x] **TASK-09**: Kiểm tra toàn diện các file Auto-Merged
- [x] **TASK-10**: Chạy biên dịch kiểm tra thành công
- [x] **TASK-11**: Hoàn tất commit merge (`c9898a79b`) và fast-forward vào nhánh chính `main`
