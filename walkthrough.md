# Walkthrough & Báo cáo Nghiệm thu: Căn Nhịp TTS Theo Thời Gian Phụ Đề

> **Kế hoạch gốc**: [`PLAN-subtitle-tts-timing-sync.md`](file:///f:/MyGithub/NuvioMobile-Fast/PLAN-subtitle-tts-timing-sync.md)  
> **Trạng thái**: Hoàn tất triển khai đa tác tử (`/orchestrate`) — Pass **512/512** tests, build Release thành công & đã cài sẵn lên `SM-S938B` (`192.168.7.135:46795`)  
> **Người điều phối**: Agent `mon`  
> **Ngày hoàn tất**: 20/11/2026  

---

## 🎯 1. Tóm tắt Tác vụ & Kiến trúc Giải pháp

Mục tiêu của đợt nâng cấp là khắc phục triệt để tình trạng câu phụ đề chưa đọc hết đã bị cắt sang câu khác bằng cách đưa **mốc thời gian thực từ file phụ đề** (`startMs`, `endMs`, `nextCueStartMs`) và **tốc độ phát video (`playbackSpeed`)** vào thẳng bộ điều phối TTS:

1. **Tính quỹ thời gian thực (`SubtitleTtsTimingCalculator`)**:
   - Xác định khoảng trống khả dụng thực sự tới câu kế tiếp (`nextCueStartMs`) — kể cả khoảng im lặng sau khi dòng chữ tắt trên màn hình — chia theo `playbackSpeed`, trừ khoảng đệm an toàn (`80ms`).
   - Tự giữ tốc độ tự nhiên nếu câu có nhiều thời gian, và ép tăng tốc linh hoạt lên tới trần cấu hình `maxAutoSpeed` (`1.4x` – `2.2x`, mặc định `2.1x`) nếu câu thoại ngắn/gấp.
2. **Tiền tổng hợp Lookahead & Khớp PCM 2 Pha (`SubtitleTtsCoordinator`)**:
   - Đệm trước 1–2 câu sắp tới (`Lookahead Pre-Synthesis Cache`, LRU tối đa 4 mục) ngay khi câu hiện tại đang phát để loại bỏ ~150–300ms độ trễ ONNX lúc chuyển câu.
   - Đo trực tiếp độ dài mảng `FloatArray` PCM đầu ra (`actualSpeechMs`) từ Piper ONNX: nếu vẫn vượt quỹ thời gian và chưa chạm trần tốc độ, tự động tổng hợp lại tối đa **1 lần** với hệ số tốc độ đã hiệu chỉnh (`computeRecalibratedSpeed`).
   - Điều chỉnh khoảng đệm cuối `adaptiveTailMs` động (`45ms` khi câu nối sát nhau, `450ms` khi khoảng trống rộng) qua `SubtitleTtsPlayer`.
3. **Thu thập Timeline từ 4 Nguồn Phụ đề + Tùy chọn Giao diện**:
   - Hỗ trợ trích xuất timing/lookahead từ `SidecarSubtitleController` (SRT/VTT/TTML), ExoPlayer embedded/`AutoSyncExtractorsFactory`, phụ đề `.ass` qua `libass`, và `libmpv` (`sub-start`/`sub-end`/`sub-delay`).
   - Đồng bộ 2 chiều giữa Cài đặt Âm thanh (`PlaybackAudioTtsSettings.kt`), Modal trong Player (`SubtitleTtsModal.kt`) và Tìm kiếm cài đặt (`SettingsSearch.kt`): công tắc **"Tự động căn nhịp theo thời gian phụ đề"**, thanh trượt **"Tốc độ tối đa khi bắt nhịp"** (`1.4x`–`2.2x`) và công tắc **"Chờ đọc hết câu trước khi chuyển"** (`waitForCueCompletion`).

---

## 🤖 2. Phân công & Đóng góp của Hội đồng Tác tử (`/orchestrate` — 5 Agents)

| Giai đoạn | Subagent | Nhiệm vụ & Đóng góp | Phạm vi Tệp |
| :--- | :--- | :--- | :--- |
| **Giai đoạn 1 (Foundation)** | `tts-foundation-architect` | Thêm `TimedSpeechCue`, `SubtitleCuePlaybackContext`, mở rộng `SubtitleTtsSettings` & `SubtitleTtsStorage` (Android/iOS), xây dựng `SubtitleTtsTimingCalculator` và bộ test tính toán thời lượng. | [`SubtitleTtsModels.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/tts/SubtitleTtsModels.kt), [`SubtitleTtsStorage.android.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/androidMain/kotlin/com/nuvio/app/features/player/tts/SubtitleTtsStorage.android.kt), [`SubtitleTtsStorage.ios.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/iosMain/kotlin/com/nuvio/app/features/player/tts/SubtitleTtsStorage.ios.kt), [`SubtitleTtsTextCleaner.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/tts/SubtitleTtsTextCleaner.kt), [`SubtitleTtsTimingCalculator.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/tts/SubtitleTtsTimingCalculator.kt), [`SubtitleTtsTimingCalculatorTest.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/commonTest/kotlin/com/nuvio/app/features/player/tts/SubtitleTtsTimingCalculatorTest.kt) |
| **Giai đoạn 2 (Parallel — Core)** | `tts-core-engine-dev` | Mở rộng `SubtitleTtsPlayer` (`adaptiveTailMs`, generation-guarded `onCompleted`), `SystemTtsEngine` (nâng tốc độ tới `2.5f` + `UtteranceProgressListener`), Lookahead Cache, khớp PCM 2 pha & hàng đợi `waitForCueCompletion` trong `SubtitleTtsCoordinator`. | [`SubtitleTtsPlayer.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/tts/SubtitleTtsPlayer.kt), [`SubtitleTtsPlayer.android.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/androidMain/kotlin/com/nuvio/app/features/player/tts/SubtitleTtsPlayer.android.kt), [`SubtitleTtsPlayer.ios.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/iosMain/kotlin/com/nuvio/app/features/player/tts/SubtitleTtsPlayer.ios.kt), [`SystemTtsEngine.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/tts/SystemTtsEngine.kt), [`SystemTtsEngine.android.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/androidMain/kotlin/com/nuvio/app/features/player/tts/SystemTtsEngine.android.kt), [`SystemTtsEngine.ios.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/iosMain/kotlin/com/nuvio/app/features/player/tts/SystemTtsEngine.ios.kt), [`SubtitleTtsCoordinator.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/tts/SubtitleTtsCoordinator.kt), [`SubtitleTtsCoordinatorTimingTest.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/androidHostTest/kotlin/com/nuvio/app/features/player/tts/SubtitleTtsCoordinatorTimingTest.kt) |
| **Giai đoạn 2 (Parallel — Player)** | `tts-player-timeline-dev` | Trích xuất timing & lookahead 2 câu sắp tới từ `SidecarSubtitleController`, ExoPlayer embedded/ASS, `SubtitleCuePresenter`, `libmpv` (Android & iOS) và viết test `SidecarSubtitleTimingLookaheadTest`. | [`SubtitleCuePresenter.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/androidMain/kotlin/com/nuvio/app/features/player/SubtitleCuePresenter.kt), [`PlayerSidecarSubtitles.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/androidMain/kotlin/com/nuvio/app/features/player/PlayerSidecarSubtitles.kt), [`PlayerEngine.android.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/androidMain/kotlin/com/nuvio/app/features/player/PlayerEngine.android.kt), [`PlayerEngine.ios.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/iosMain/kotlin/com/nuvio/app/features/player/PlayerEngine.ios.kt), [`SidecarSubtitleTimingLookaheadTest.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/androidHostTest/kotlin/com/nuvio/app/features/player/SidecarSubtitleTimingLookaheadTest.kt) |
| **Giai đoạn 2 (Parallel — UI)** | `tts-ui-frontend-dev` | Đổi tên nhãn căn nhịp, thêm thanh trượt `"Tốc độ tối đa khi bắt nhịp"` (`1.4x..2.2x`) và công tắc `"Chờ đọc hết câu trước khi chuyển"` đồng bộ 2 chiều + `SettingsSearch`. | [`PlaybackAudioTtsSettings.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings/PlaybackAudioTtsSettings.kt), [`SubtitleTtsModal.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/tts/SubtitleTtsModal.kt), [`SettingsSearch.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings/SettingsSearch.kt) |
| **Giai đoạn 3 (Systemic QA)** | `tts-quality-inspector` | Phát hiện & vá thiếu `SubtitleTtsCoordinator.onSeek()` khi tua cử chỉ/chạm đúp/chuyển chương/Skip Intro/auto-skip; xác nhận ProGuard `mapping.txt`; bổ sung `SubtitleTtsStorageTest` + test mới; chạy full 68 test suites & build Release. | [`PlayerScreenRuntimeGestureActions.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeGestureActions.kt), [`PlayerScreenRuntimeChapters.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeChapters.kt), [`PlayerScreenRuntimeUi.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeUi.kt), [`PlayerScreenRuntimeEffects.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeEffects.kt), [`SubtitleTtsStorageTest.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/androidHostTest/kotlin/com/nuvio/app/features/player/tts/SubtitleTtsStorageTest.kt), [`SubtitleCuePresenterTest.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/androidHostTest/kotlin/com/nuvio/app/features/player/SubtitleCuePresenterTest.kt), [`SettingsPagesTest.kt`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/src/androidHostTest/kotlin/com/nuvio/app/features/settings/SettingsPagesTest.kt) |

---

## ✅ 3. Kết quả Kiểm thử & Đóng gói (Observed Evidence)

1. **Biên dịch Metadata & Kiểm thử Đơn vị / Robolectric** (chạy sau lần sửa mã nguồn cuối):
   - Lệnh: `.\gradlew.bat :composeApp:compileCommonMainKotlinMetadata :composeApp:testAndroidHostTest --tests "com.nuvio.app.features.player.*" --tests "com.nuvio.app.features.settings.SettingsPagesTest" --console=plain`
   - Kết quả: **`BUILD SUCCESSFUL in 3m 36s`**, tổng **68 suites — 512 tests PASS** (`0` fail, `0` error, `0` skip):
     - `SubtitleTtsTextCleanerTest`: `9/9` pass
     - `SubtitleTtsTimingCalculatorTest`: `10/10` pass
     - `SubtitleTtsCoordinatorTimingTest`: `11/11` pass
     - `SubtitleTtsPlayerPcmTest`: `1/1` pass
     - `SubtitleTtsStorageTest`: `1/1` pass
     - `OnnxMetadataInjectorTest`: `4/4` pass
     - `PiperConfigParserTest`: `3/3` pass
     - `SidecarSubtitleTimingLookaheadTest`: `7/7` pass
     - `SubtitleCuePresenterTest`: `6/6` pass
     - `SettingsPagesTest`: `8/8` pass
     - 58 suite `com.nuvio.app.features.player.*` còn lại: `452/452` pass
2. **Đóng gói bản Release (`fullRelease` + R8 Minify)** & **Cài lên Điện thoại Thật**:
   - Lệnh: `.\gradlew.bat :androidApp:assembleFullRelease --console=plain` ➔ **`BUILD SUCCESSFUL in 13s`**.
   - Kiểm tra `androidApp/build/outputs/mapping/fullRelease/mapping.txt`: toàn bộ `com.k2fsa.sherpa.onnx.*` và `com.nuvio.app.features.player.tts.*` (kèm `KSerializer`) được giữ nguyên theo [`proguard-rules.pro`](file:///f:/MyGithub/NuvioMobile-Fast/composeApp/proguard-rules.pro).
   - File đầu ra chính (`arm64-v8a`): [`NuvioMobile-1.2.5-arm64-v8a.apk`](file:///f:/MyGithub/NuvioMobile-Fast/androidApp/build/outputs/apk/full/release/NuvioMobile-1.2.5-arm64-v8a.apk) (`64,334,560 bytes`, `11/20/2026 8:25:08 PM`).
   - Đã chạy `adb -s 192.168.7.135:46795 install -r` ➔ `Performing Streamed Install` -> **`Success`** lên máy `SM-S938B`.
3. **Giới hạn phạm vi trên máy Windows**:
   - Target `iosArm64` và `iosSimulatorArm64` bị skip biên dịch trên Windows host (`Cross Compilation with Cinterop Not Supported` cho `commoncrypto`); cần biên dịch lại trên macOS nếu phát hành iOS.
   - Chưa tự ý điều khiển màn hình để test nghe thực tế trên phim thoại nhanh trên `SM-S938B` nhằm tôn trọng việc bạn tự kiểm thử.
