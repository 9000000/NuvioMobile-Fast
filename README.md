<div align="center">

  <img src="https://nuvio.tv/assets/nuvio-app-logo-wordmark.webp" alt="Nuvio" width="320" />

  <p>
    A free, open-source media app for your phone, your desktop, and the TV you already own.
    <br />
    Bring your own sources. Nuvio turns them into a library with artwork, ratings, subtitles, and your place saved on every screen.
  </p>

  [Website](https://nuvio.tv) · [GitHub releases](https://github.com/9000000/NuvioMobile-Fast/releases/latest) · [Support Nuvio](https://nuvio.tv/support)

</div>

## Get Nuvio Mobile

- [Android APK](https://github.com/9000000/NuvioMobile-Fast/releases/latest)
- iOS via AltStore or SideStore: add [this source URL](https://raw.githubusercontent.com/9000000/NuvioMobile-Fast/main/store.json) in the app's Sources section, then install Nuvio.

## Build from source

```bash
git clone https://github.com/9000000/NuvioMobile-Fast.git
cd NuvioMobile-Fast
```

### Android

Android development requires Android Studio and the Android SDK.

```bash
./gradlew :androidApp:assembleFullDebug
```

### iOS

iOS development requires macOS and Xcode.

```bash
env NUVIO_IOS_DISTRIBUTION=full xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -sdk iphonesimulator \
  -derivedDataPath build/ios-derived-full-simulator \
  CODE_SIGNING_ALLOWED=NO \
  build
```

The shared app is built with Kotlin Multiplatform and Compose Multiplatform.

## License

[GNU General Public License v3.0](./LICENSE)
