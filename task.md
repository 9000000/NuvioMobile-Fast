# Task Management: NuvioMobile Fast

## 📋 Trạng thái Hiện tại: ĐÃ LẬP KẾ HOẠCH (`PLAN-merge-upstream-20261127` - Chờ duyệt để thực thi)

---

### 🔄 Đợt công việc Mới: Đồng bộ Upstream `9000000/NuvioMobile` (`21/11/2026` ➔ `27/11/2026`)
*Kế hoạch chi tiết: [`PLAN-merge-upstream-20261127.md`](file:///f:/MyGithub/NuvioMobile-Fast/PLAN-merge-upstream-20261127.md) | Merge Base: `4ec93635a` ➔ Upstream HEAD: `6d2fdab63` (32 commits)*

- [ ] **TASK-SYNC-261127-01** (`git-specialist`): Commit `iosApp/Configuration/Version.xcconfig` (`1.3.0` / `176`), tạo nhánh sao lưu `backup/pre-upstream-merge-20261127` và nhánh làm việc `merge/upstream-20261127`.
- [ ] **TASK-SYNC-261127-02** (`backend-specialist`): Chạy `git merge --no-commit --no-ff nuvio-src/cmp-rewrite` và giải quyết 2 Hard Conflicts: `AutoSyncPlatform.android.kt` (nhận bản `PlayerPlaybackNetworking.playbackHttpClient` vượt trội) và `PlayerEngine.android.kt` (3 hunks: imports, tách `AudioSyncTaps` không gây overhead cho TTS, hợp nhất ASS styling + cờ `hideVisual` TTS trên libmpv).
- [ ] **TASK-SYNC-261127-03** (`backend-specialist` / `quality-inspector`): Xử lý Critical Semantic Conflict trùng `sherpa-onnx-1.13.8.aar` trong `composeApp/build.gradle.kts` (dùng chung 1 file cục bộ SHA-256 khớp 100% cho cả Piper TTS & Audio Sync), dọn lặp `composeApp/proguard-rules.pro`, và rà soát toàn bộ file auto-merged (`SubtitleAppearance`, `SubtitleModal`, `PlayerScreenRuntimeUi`, `PlayerEngine.ios.kt`, `MainActivity.kt`, `androidApp/build.gradle.kts`).
- [ ] **TASK-SYNC-261127-04** (`frontend-specialist`): Bổ sung bản dịch Tiếng Việt chuẩn cho Audio Sync Fallback (`values-vi/audio_sync_strings.xml`, `values-vi/autosync_toast_strings.xml`) và `subtitle_customize_ass` (`values-vi/strings.xml`), xác minh `0` duplicate keys XML.
- [ ] **TASK-SYNC-261127-05** (`test-engineer`): Chạy toàn bộ `:composeApp:testAndroidHostTest` (bao gồm 19 suite mới của AudioSync/AutoSync + 9 suite TTS) và biên dịch `:androidApp:assembleFullDebug` (xác minh không lỗi duplicate class/native lib, tương thích Nuvio Engine `0.1.7`, có đủ `tts_en_vi_dict.tsv` & `silero_vad_v5_16k.bin`).
- [ ] **TASK-SYNC-261127-06** (`orchestrator`): Commit merge chuẩn hóa, fast-forward vào `main` và xuất báo cáo nghiệm thu chi tiết.

---

### ✅ Đợt công việc Trước: Đồng bộ Upstream `9000000/NuvioMobile` (`22/11/2026` ➔ `24/11/2026`) — HOÀN TẤT 100%
*Kế hoạch chi tiết: [`PLAN-merge-upstream-20261124.md`](file:///f:/MyGithub/NuvioMobile-Fast/PLAN-merge-upstream-20261124.md) | Merge Base: `642f6e031` ➔ Upstream HEAD: `4ec93635a` (33 commits) | Merge Commit: `830161c70`*

- [x] **TASK-SYNC-261124-01** (`git-specialist`): Đã commit `iosApp/Configuration/Version.xcconfig` (`1.2.9` / `175` — `b7b7fe0b3`), tạo nhánh sao lưu `backup/pre-upstream-merge-20261124` (`9629b78e1`) và nhánh làm việc `merge/upstream-20261124`.
- [x] **TASK-SYNC-261124-02** (`backend-specialist`): Đã chạy `git merge --no-commit --no-ff nuvio-src/cmp-rewrite` và giải quyết sạch 2 file conflict: `Version.xcconfig` (giữ `1.2.9` / `175`) và Smart XML Merge `values-vi/strings.xml` (`2.665` strings, `6` plurals, `0` dups — bảo tồn trọn 125 key riêng của Fast, thêm 2 `<plurals>` Downloads, sửa typo dấu `"` thừa, dọn 5 key Downloads cũ không còn dùng).
- [x] **TASK-SYNC-261124-03** (`quality-inspector`): Đã rà soát 4 file auto-merged (`SubtitleAudioModels.kt`, `SubtitleModal.kt`, `PlayerEngine.android.kt`, `PlayerEngine.ios.kt`), xác nhận giữ nguyên `subtitleOutlineWidthRange = 1..10`, `subtitleFontFamily`, nút `+ Thêm phụ đề`, toàn bộ hook TTS, và toàn bộ 11 file XML hợp lệ (`0` dups).
- [x] **TASK-SYNC-261124-04** (`test-engineer`): Hoàn tất `:composeApp:testAndroidHostTest` (**336 suites / 2.338 tests PASS, 0 fail** — gồm 3 suite mới từ upstream: `AddonHttpTextLimitTest`, `AddonSyncEligibilityTest`, `PluginMediaTypeTest` + toàn bộ 9 suite TTS) và `:androidApp:assembleFullDebug` (`BUILD SUCCESSFUL`, tương thích 100% với 2 Media3 AAR mới).
- [x] **TASK-SYNC-261124-05** (`orchestrator`): Đã commit merge `830161c70`, fast-forward vào `main` và xuất báo cáo nghiệm thu chi tiết.

---

### ✅ Đợt công việc Trước: Đồng bộ Upstream `NuvioMedia/NuvioMobile` (`18/11/2026` ➔ `23/11/2026`) — HOÀN TẤT 100%
*Kế hoạch chi tiết: [`PLAN-merge-upstream-20261123.md`](file:///f:/MyGithub/NuvioMobile-Fast/PLAN-merge-upstream-20261123.md) | Merge Base: `b66f7725c` ➔ Upstream HEAD: `642f6e031` (40 commits) | Merge Commit: `e31731649`*

- [x] **TASK-SYNC-261123-01** (`git-specialist`): Đã commit sạch tính năng Phiên âm TTS (`db71cc1f7`), tạo nhánh sao lưu `backup/pre-upstream-merge-20261123` (`d87bd1985`) và nhánh làm việc `merge/upstream-20261123`.
- [x] **TASK-SYNC-261123-02** (`backend-specialist`): Đã chạy `git merge --no-commit --no-ff upstream/cmp-rewrite` và giải quyết conflict duy nhất tại `androidApp/src/main/AndroidManifest.xml` (giữ trọn cả `<uses-feature android:name="android.hardware.wifi" android:required="false" />` và khối `<queries>` TTS).
- [x] **TASK-SYNC-261123-03** (`quality-inspector`): Đã rà soát 4 file auto-merged (`SubtitleAudioModels.kt`, `SubtitleModal.kt`, `values/strings.xml`, `store.json`), xác nhận `0` duplicate keys XML, JSON hợp lệ, và 100% tùy chỉnh độc quyền của Fast còn nguyên vẹn.
- [x] **TASK-SYNC-261123-04** (`test-engineer`): Hoàn tất `:composeApp:testAndroidHostTest` (**333 suites / 2.327 tests PASS, 0 fail** — gồm cả `ServerProfileDeviceIdTest` mới & toàn bộ 9 suite TTS) và `:androidApp:assembleFullDebug` (`BUILD SUCCESSFUL`), xác minh Merged Manifest chứa đủ Wi-Fi optional + `<queries>` TTS.
- [x] **TASK-SYNC-261123-05** (`orchestrator`): Đã commit merge `e31731649`, fast-forward vào `main` và xuất báo cáo nghiệm thu chi tiết.

---

### 🗣️ Đợt công việc Trước: Phiên âm Tên riêng & Từ tiếng Anh cho TTS Tiếng Việt (`PLAN-tts-english-proper-name-transliteration` - HOÀN TẤT, commit `db71cc1f7`)
*Kế hoạch chi tiết: [`PLAN-tts-english-proper-name-transliteration.md`](file:///f:/MyGithub/NuvioMobile-Fast/PLAN-tts-english-proper-name-transliteration.md) | Nguồn tham chiếu: `NuvioTV-Fast`*

- [x] **TASK-TTSTRANS-01** (`backend-specialist`): Đóng gói từ điển `tts_en_vi_dict.tsv` (1.015 mục) vào `composeApp/src/commonMain/composeResources/files/tts_en_vi_dict.tsv` và xây dựng `TtsPhoneticDictionary.kt` thuần KMP (nạp nền trên `Dispatchers.Default`, sửa lập chỉ mục cho từ ghép gạch nối/dấu chấm như `spider-man`, `obi-wan`, `wi-fi`, `middle-earth`, `k.o`) kèm `TtsPhoneticDictionaryAssetTest.kt`.
- [x] **TASK-TTSTRANS-02** (`backend-specialist`): Xây dựng `EnglishToVietnameseTransliterator.kt` thuần KMP (single-pass zero-regex scanner, hỗ trợ nháy cong `’`, bảo vệ URL/email giữa câu, stop words tiếng Việt, loại bỏ phụ âm tiếng Việt `ph`/`th`/`tr` khỏi `ENGLISH_CLUSTERS`, từ viết tắt, rule-based fallback) kèm `EnglishToVietnameseTransliteratorTest.kt` trong `commonTest` và benchmark 500 cues `< 500ms`.
- [x] **TASK-TTSTRANS-03** (`backend-specialist`): Mở rộng `SubtitleTtsModels.kt` (`transliterateEnglishNames = true`, `isVietnameseTtsTarget(...)`) và cập nhật lưu trữ `SubtitleTtsStorage` (Android `SharedPreferences` & iOS `NSUserDefaults`) kèm test trong `SubtitleTtsStorageTest.kt`.
- [x] **TASK-TTSTRANS-04** (`backend-specialist`): Tích hợp `prepareTtsSpeechText(...)` vào `SubtitleTtsCoordinator.kt` cho cả Speak, Queue/Carry-Over, Preview, và **Piper Lookahead Pre-Synthesis** (đồng bộ `PcmCacheKey`, xóa `pcmCache` khi bật/tắt tùy chọn, preload từ điển khi `ensureLoaded()`) kèm test trong `SubtitleTtsCoordinatorTimingTest.kt`.
- [x] **TASK-TTSTRANS-05** (`frontend-specialist`): Thêm công tắc `"Phiên âm tên riêng & từ tiếng Anh"` vào `PlaybackAudioTtsSettings.kt`, `SubtitleTtsModal.kt` và đăng ký từ khóa trong `SettingsSearch.kt`.
- [x] **TASK-TTSTRANS-06** (`test-engineer`): Hoàn tất `:composeApp:testAndroidHostTest` (**332 suites / 2.325 tests PASS, 0 fail**), biên dịch `:androidApp:assembleFullDebug` (`BUILD SUCCESSFUL`), xác minh `tts_en_vi_dict.tsv` (`18.389` bytes) đã đóng gói bên trong APK và rule ProGuard sẵn sàng bảo vệ.

---

### ⏱️ Đợt công việc Trước: Căn Nhịp TTS Theo Thời Gian Phụ Đề (`PLAN-subtitle-tts-timing-sync` - Đã hoàn tất triển khai)
*Kế hoạch chi tiết: [`PLAN-subtitle-tts-timing-sync.md`](file:///f:/MyGithub/NuvioMobile-Fast/PLAN-subtitle-tts-timing-sync.md) | Báo cáo nghiệm thu: [`walkthrough.md`](file:///f:/MyGithub/NuvioMobile-Fast/walkthrough.md)*

- [x] **TASK-TTSSYNC-01**: Mở rộng `SubtitleTtsModels.kt` & `SubtitleTtsStorage` (Android/iOS) thêm `TimedSpeechCue`, `SubtitleCuePlaybackContext`, `maxAutoSpeed` (`1.4f..3.0f`, mặc định `2.1f`) và `waitForCueCompletion` (`Boolean = false`).
- [x] **TASK-TTSSYNC-02**: Xây dựng `SubtitleTtsTimingCalculator` & cập nhật `SubtitleTtsTextCleaner.kt` (tính quỹ thời gian theo `nextCueStartMs` và `playbackSpeed`, ước lượng tốc độ theo âm tiết, hiệu chỉnh 2 pha theo độ dài PCM thực tế, và tính `adaptiveTailMs` động).
- [x] **TASK-TTSSYNC-03**: Mở rộng thu thập timeline + lookahead câu sắp tới từ 4 nguồn phụ đề (`SidecarSubtitleController`, `SubtitleCuePresenter`, ExoPlayer embedded/ASS trong `PlayerEngine.android.kt`, và `libmpv` Android/iOS).
- [x] **TASK-TTSSYNC-04**: Nâng cấp `SubtitleTtsPlayer` (`adaptiveTailMs`, callback hoàn tất), `SystemTtsEngine` (mở rộng ngưỡng tốc độ lên `3.0f` + `UtteranceProgressListener`), `PiperOnnxEngine` (`3.0f`) và `SubtitleTtsCoordinator` (tiền tổng hợp Lookahead Cache, khớp PCM 2 pha, hàng đợi tùy chọn `waitForCueCompletion`, và bổ sung `onSeek()` ở mọi thao tác tua/chuyển chương/skip).
- [x] **TASK-TTSSYNC-05**: Cập nhật UI trong `PlaybackAudioTtsSettings.kt`, `SubtitleTtsModal.kt` và `SettingsSearch.kt` (đổi nhãn `"Tự động căn nhịp theo thời gian phụ đề"`, bỏ gợi ý preset `vais1000`, tăng giới hạn tốc độ đọc cơ bản lên `2.0x`, slider `"Tốc độ tối đa khi bắt nhịp"` `1.4x..3.0x` và công tắc `"Chờ đọc hết câu trước khi chuyển"`).
- [x] **TASK-TTSSYNC-06**: Hoàn tất Unit/Robolectric Test (`68` suites / `512` tests PASS), build `assembleFullRelease` (`NuvioMobile-1.2.5-arm64-v8a.apk`) và cài sẵn qua ADB lên `SM-S938B` (`192.168.7.135:46795`) để bạn tự kiểm chứng trên phim thoại nhanh.

---

### 🎙️ Tính năng: TTS Offline Thuần Túy & Tích hợp Cài đặt Âm thanh (`PLAN-tts-offline-and-huggingface` - HOÀN TẤT 100%)
*Kế hoạch chi tiết: [`PLAN-tts-offline-and-huggingface.md`](file:///f:/MyGithub/NuvioMobile-Fast/PLAN-tts-offline-and-huggingface.md)*

- [x] **TASK-TTS2-01**: Gỡ bỏ `SubtitleTtsSynthesizer.kt` (Online TTS); cập nhật `SubtitleTtsModels.kt` & `SubtitleTtsStorage` (Android/iOS) cho Google Speech (Engine/Voice/Pitch) + Piper ONNX; nâng cấp `SubtitleTtsPlayer` phát **PCM (`FloatArray`)** trực tiếp qua Android `AudioTrack`.
- [x] **TASK-TTS2-02**: Triển khai `SystemTtsEngine` (`expect/actual`: Android `TextToSpeech` + iOS `AVSpeechSynthesizer`) hỗ trợ chọn Engine Google Speech, quét & chọn từng giọng đọc, chỉnh Cao độ (`Pitch`), Tốc độ, Âm lượng và nút mở cài đặt tải giọng Google trên máy.
- [x] **TASK-TTS2-03**: Tích hợp `sherpa-onnx` vào Gradle (`androidMain`), xây dựng `PiperConfigParser` (sinh `tokens.txt` từ `.onnx.json`), `PiperModelManager` (cài sẵn 3 preset từ `hoanglinhn0/Model`: **Ngọc Huyền 5**, **Lạc Phi**, **Ngọc Huyền** + nhập link HuggingFace tùy chỉnh + lưu tên file ASCII + gắn metadata Piper + chuẩn bị `espeak-ng-data`), và `PiperOnnxEngine` xuất PCM trực tiếp.
- [x] **TASK-TTS2-04**: Cập nhật `SubtitleTtsCoordinator` điều phối 100% Offline giữa `SystemTtsEngine` và `PiperOnnxEngine`, tự giải phóng session ONNX khi tắt hoặc đổi sang Google Speech.
- [x] **TASK-TTS2-05**: Tạo `PlaybackAudioTtsSettings.kt` và tích hợp toàn bộ cấu hình TTS (Google Speech tùy chỉnh giọng/pitch, tải & quản lý 3 giọng Piper + link HuggingFace, 2 thanh âm lượng, tốc độ, ẩn phụ đề) vào `PlaybackAudioSettings.kt` (**Cài đặt ➔ Phát lại ➔ Âm thanh**) & `SettingsSearch.kt`; đồng bộ với `SubtitleTtsModal.kt`.
- [x] **TASK-TTS2-06**: Viết Unit Test `PiperConfigParserTest`, chạy `testAndroidHostTest` (PASS 100%) và biên dịch đóng gói `./gradlew assembleFullDebug` thành công (`BUILD SUCCESSFUL`).
- [x] **TASK-TTS2-07**: Vá triệt để lỗi crash chọn ONNX & mất tiếng (`sherpa-onnx` v1.13.8 AAR tích hợp `espeak-ng`, khóa luồng JNI, chuẩn Protobuf `OnnxMetadataInjector`, làm phẳng `espeak-ng-data`, hàng đợi `SystemTtsEngine`, `ensureLoaded()` trong Settings) và thêm nút nghe thử giọng `"chào mừng bạn đến với áp nuvio 9 triệu đây là giọng của tôi"`; đã kiểm thử thực tế trên giả lập MuMu (`127.0.0.1:16416`) pass 100%.
- [x] **TASK-TTS2-08**: Sửa lỗi crash R8/ProGuard JNI (`OfflineTts.newFromFile` tìm `fid == null` khi minify bản Release) trong `composeApp/proguard-rules.pro` (`-keep com.k2fsa.sherpa.onnx.**` và `com.nuvio.app.features.player.tts.**`); đã xác minh 4 giọng Piper chạy ổn định không crash trên điện thoại thật `SM-S938B` (`arm64-v8a`, `192.168.7.135:46795`).
- [x] **TASK-TTS2-09**: Khắc phục tình trạng câu cuối bị ngắt nhanh / hụt hơi: tự động bổ sung dấu chấm cuối câu khi tổng hợp (`SubtitleTtsTextCleaner.ensureSentenceEndingPunctuation`), tăng `silenceScale` lên `0.7f` (`PiperOnnxEngine`), đưa mặc định `baseSpeed` về `1.0f` (`SubtitleTtsModels`), đệm khoảng lặng đầu/cuối (40ms đầu, fade-out 12ms, 450ms cuối) và xả sạch bộ đệm `AudioTrack` trước khi `release()` (`SubtitleTtsPlayer.android.kt`); bổ sung unit test `SubtitleTtsPlayerPcmTest` & `SubtitleTtsTextCleanerTest`, build lại `fullRelease` và kiểm thử trên `SM-S938B`.

---

### 📦 Lưu trữ: Các đợt công việc đã hoàn tất

#### 🔊 Tích hợp Thuyết minh Phụ đề AI (`PLAN-subtitle-tts`) - HOÀN TẤT (100%)
- [x] **TASK-TTS-01** .. **TASK-TTS-07**: Hoàn tất khung TTS, nút điều khiển ngoài PlayerControls, 2 thanh âm lượng độc lập, ẩn phụ đề chạy ngầm và build thành công `assembleFullDebug`.

#### 📺 Cải tiến & Tối ưu hóa Live TV (`PLAN-live-tv-improvements`) - HOÀN TẤT (100%)
- [x] **TASK-LTV-01** .. **TASK-LTV-07**: Hoàn tất lưu/phục hồi playlist, đổi tên "List Kênh", "Danh mục", "❤️ ⭐", mục "Xem gần đây" và chuỗi điều hướng thông minh.

#### 🔄 Upstream Merge (14/11/2026 - 19/11/2026) - HOÀN TẤT (100%)
- [x] **TASK-01** .. **TASK-11**: Hoàn tất merge upstream `c9898a79b` và kiểm thử toàn diện.
