# ERRORS.md - Automatic Error Tracking & Learning

## [2026-11-27 09:30] - Lỗi biên dịch `AutoSyncSyncedSubtitle.url` sau khi merge PR #2732 (`Audio Sync Fallback`)

- **Type**: Integration / Build
- **Severity**: Medium
- **File**: `composeApp/src/androidMain/kotlin/com/nuvio/app/features/player/PlayerEngine.android.kt:1468`
- **Agent**: mon
- **Root Cause**: PR #2732 của upstream thay đổi API của `AutoSyncSyncedSubtitle` từ `url: StateFlow<String?>` sang `status: StateFlow<AutoSyncSyncedSubtitle.Status?>` (gồm `url` và `state: AutoSyncState`). Hook reload timeline phụ đề ngoài cho TTS của nhánh Fast vẫn tham chiếu thuộc tính cũ `AutoSyncSyncedSubtitle.url`.
- **Error Message**:
  ```
  e: PlayerEngine.android.kt:1468:57 Unresolved reference 'url'.
  ```
- **Fix Applied**: Cập nhật `PlayerEngine.android.kt` sang thu thập `AutoSyncSyncedSubtitle.status.collectAsStateWithLifecycle()` và lọc `autoSyncStatus?.takeIf { it.state == AutoSyncState.SYNCED }?.url`.
- **Prevention**: Khi merge các PR có refactor package `autosync`, luôn grep kiểm tra mọi tham chiếu cục bộ từ hook TTS sang `AutoSync*` trước khi chạy build.
- **Status**: Fixed

---

## [2026-11-23 22:18] - `EnglishToVietnameseTransliterator` viết hoa nhầm từ tiếng Việt chứa `ph`/`th`/`tr` (`phim` -> `Phim`)

- **Type**: Logic / Process
- **Severity**: Medium
- **File**: `composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/tts/EnglishToVietnameseTransliterator.kt:81`
- **Agent**: mon
- **Root Cause**: Kế thừa danh sách `ENGLISH_CLUSTERS` từ `NuvioTV-Fast` có chứa các phụ âm chuẩn tiếng Việt (`"ph"`, `"th"`, `"tr"`) kết hợp với `applyRuleBasedPhonetics` luôn ép `titlecase()` ở chữ cái đầu ngay cả khi không có quy tắc chuyển âm nào thay đổi chuỗi, khiến từ tiếng Việt chữ thường như `"phim"` bị biến thành `"Phim"`.
- **Error Message**:
  ```
  EnglishToVietnameseTransliteratorTest > protectsVietnameseStopWordsAndAccentedWords FAILED
  org.junit.ComparisonFailure: expected:<...nào chúng ta đi xem [p]him?> but was:<...nào chúng ta đi xem [P]him?>
  ```
- **Fix Applied**: Loại bỏ `"ph"`, `"th"`, `"tr"` khỏi `ENGLISH_CLUSTERS`; trong `applyRuleBasedPhonetics`, trả `null` nếu không có biến đổi ngữ âm thực tế (`result == lowerName`) và chỉ giữ viết hoa chữ cái đầu khi `originalToken[0].isUpperCase()`.
- **Prevention**: Luôn kiểm thử nghiêm ngặt bằng `assertEquals` toàn câu tiếng Việt (cả từ có dấu và không dấu chứa `ph`, `th`, `tr`) thay vì chỉ `assertTrue(contains(...))`.
- **Status**: Fixed

---

## [2026-11-20 18:44] - Crash JNI `fid == null` trên bản Release APK do R8 obfuscate Sherpa-ONNX

- **Type**: Runtime / Build (ProGuard / R8)
- **Severity**: Critical
- **File**: `composeApp/proguard-rules.pro`
- **Agent**: mon
- **Root Cause**: `androidApp/build.gradle.kts` bật `isMinifyEnabled = true` cho `release` build, trong khi file `proguard.txt` đi kèm `sherpa-onnx-1.13.8.aar` rỗng và `composeApp/proguard-rules.pro` chưa có rule `-keep` cho `com.k2fsa.sherpa.onnx.**`. R8 đã đổi tên `OfflineTtsModelConfig -> y56`, `OfflineTtsVitsModelConfig -> b66`, và đổi tên field `OfflineTtsConfig.model -> a`. Khi `libsherpa-onnx-jni.so` gọi `GetFieldID` trong `OfflineTts.newFromFile(...)` trên điện thoại Samsung SM-S938B (cài `NuvioMobile-1.2.5-arm64-v8a.apk`), JNI nhận `fid == null` và gây `SIGABRT` văng app.
- **Error Message**:
  ```
  Abort message: 'JNI DETECTED ERROR IN APPLICATION: fid == null
      in call to GetObjectField
      from long com.k2fsa.sherpa.onnx.OfflineTts.newFromFile(com.k2fsa.sherpa.onnx.OfflineTtsConfig)'
  ```
- **Fix Applied**: Thêm `-keep class/interface/enum com.k2fsa.sherpa.onnx.** { *; }` và giữ package `com.nuvio.app.features.player.tts.**` vào `composeApp/proguard-rules.pro`.
- **Prevention**: Mọi thư viện native AAR mới thêm vào dự án phải kiểm tra `proguard.txt` đi kèm và xác minh trên bản `assembleFullRelease` (kèm đọc `mapping.txt`) trước khi phát hành.
- **Status**: Fixed

---

## [2026-11-20 17:15] - Crash khi chọn ONNX Piper TTS do thiếu Protobuf metadata sample_rate

- **Type**: Runtime
- **Severity**: Critical
- **File**: `composeApp/src/androidMain/kotlin/com/nuvio/app/features/player/tts/PiperOnnxEngine.android.kt:52`
- **Agent**: mon
- **Root Cause**: Các model Piper gốc xuất từ PyTorch (như `Lạc Phi✓™®.onnx` và `Ngọc Huyền™®.onnx`) không có metadata protobuf `sample_rate` nhúng sẵn trong file `.onnx`. Khi `sherpa-onnx` khởi tạo `OfflineTts`, native C++ cố đọc `sample_rate` từ metadata, gây ra C++ exception/SIGABRT làm crash toàn bộ tiến trình ứng dụng. Ngoài ra, thư mục `espeak-ng-data` bị lồng cấu trúc `espeak-ng-data/espeak-ng-data/` khiến `sherpa-onnx` không tìm thấy `phontab`.
- **Error Message**: 
  ```
  11-20 17:00:22.825 16205 16349 W sherpa-onnx: 'sample_rate' does not exist in the metadata
  11-20 16:59:49.079 16205 16349 W sherpa-onnx: phoneme_codepoints is empty, no lexicon, and no frontend available.
  ```
- **Fix Applied**: 
  1. Xây dựng `OnnxMetadataInjector.kt` tiêm Protobuf varint metadata (`sample_rate=22050`, `model_type=vits`, `comment=piper`, `has_espeak=1`, `voice=vi`, `language=Vietnamese`, `n_speakers=1`) trực tiếp vào file `.onnx` in-place, kiểm tra khớp chuẩn entry Protobuf `0x72` (`StringStringEntryProto`) tránh false-positive.
  2. Hạ cấp thư viện từ `sherpa-onnx:v2.0.2` (đã loại bỏ `espeak-ng` và `piper-phonemize` từ v2.0.0 PR #4055) về `composeApp/libs/sherpa-onnx-1.13.8.aar` còn tích hợp đầy đủ `espeak-ng` cho tiếng Việt.
  3. Khóa luồng đồng bộ `synchronized(engineLock)` trong `PiperOnnxEngine.android.kt` và `piperMutex` trong `SubtitleTtsCoordinator.kt` chống race-condition C++ đa luồng; dọn phẳng cấu trúc `espeak-ng-data` và kiểm tra đủ 6 file (`phontab`, `phonindex`, `phondata`, `intonations`, `vi_dict`, `lang/aav/vi`).
  4. Bổ sung cơ chế phòng vệ an toàn & fallback về System TTS (Google Speech) nếu chưa có model hoặc model lỗi, đồng thời sửa `LaunchedEffect` trong `PlaybackAudioTtsSettings.kt` để nạp sẵn `SubtitleTtsCoordinator.refreshInstalledPiperModels()` ngay khi mở Cài đặt Âm thanh.
- **Prevention**: Luôn kiểm tra tính toàn vẹn metadata nhị phân trước khi chuyển giao file cho native C++ library, khóa luồng khi gọi JNI `OfflineTts`, xác minh thực tế trên MuMu (`127.0.0.1:16416`) và giữ Unit test `OnnxMetadataInjectorTest` + `PiperConfigParserTest`.
- **Status**: Fixed

---
