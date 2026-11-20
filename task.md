# Task Management: NuvioMobile Fast

## 📋 Trạng thái Hiện tại: HOÀN TẤT TRIỂN KHAI (`PLAN-subtitle-tts-timing-sync` - Chờ test thực tế trên máy)

---

### ⏱️ Đợt công việc Mới: Căn Nhịp TTS Theo Thời Gian Phụ Đề (`PLAN-subtitle-tts-timing-sync`)
*Kế hoạch chi tiết: [`PLAN-subtitle-tts-timing-sync.md`](file:///f:/MyGithub/NuvioMobile-Fast/PLAN-subtitle-tts-timing-sync.md) | Báo cáo nghiệm thu: [`walkthrough.md`](file:///f:/MyGithub/NuvioMobile-Fast/walkthrough.md)*

- [x] **TASK-TTSSYNC-01**: Mở rộng `SubtitleTtsModels.kt` & `SubtitleTtsStorage` (Android/iOS) thêm `TimedSpeechCue`, `SubtitleCuePlaybackContext`, `maxAutoSpeed` (`1.4f..2.2f`, mặc định `2.1f`) và `waitForCueCompletion` (`Boolean = false`).
- [x] **TASK-TTSSYNC-02**: Xây dựng `SubtitleTtsTimingCalculator` & cập nhật `SubtitleTtsTextCleaner.kt` (tính quỹ thời gian theo `nextCueStartMs` và `playbackSpeed`, ước lượng tốc độ theo âm tiết, hiệu chỉnh 2 pha theo độ dài PCM thực tế, và tính `adaptiveTailMs` động).
- [x] **TASK-TTSSYNC-03**: Mở rộng thu thập timeline + lookahead câu sắp tới từ 4 nguồn phụ đề (`SidecarSubtitleController`, `SubtitleCuePresenter`, ExoPlayer embedded/ASS trong `PlayerEngine.android.kt`, và `libmpv` Android/iOS).
- [x] **TASK-TTSSYNC-04**: Nâng cấp `SubtitleTtsPlayer` (`adaptiveTailMs`, callback hoàn tất), `SystemTtsEngine` (mở rộng ngưỡng tốc độ lên `2.5f` + `UtteranceProgressListener`) và `SubtitleTtsCoordinator` (tiền tổng hợp Lookahead Cache, khớp PCM 2 pha, hàng đợi tùy chọn `waitForCueCompletion`, và bổ sung `onSeek()` ở mọi thao tác tua/chuyển chương/skip).
- [x] **TASK-TTSSYNC-05**: Cập nhật UI trong `PlaybackAudioTtsSettings.kt`, `SubtitleTtsModal.kt` và `SettingsSearch.kt` (đổi nhãn `"Tự động căn nhịp theo thời gian phụ đề"`, thêm slider `"Tốc độ tối đa khi bắt nhịp"` và công tắc `"Chờ đọc hết câu trước khi chuyển"`).
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
