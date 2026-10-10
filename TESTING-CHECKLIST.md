# Decan Movie verification checklist

## Build verification
1. Push the project to GitHub and run the **Android APK** workflow. Confirm `buildRustProviderBridge` completes for ARM64, ARMv7, and x86_64 and that `assembleDebug` succeeds.
2. Download the `decan-movie-debug` artifact and install `app-debug.apk` on an Android 8.0+ device.
3. Confirm the APK contains `libdecanmovie_bridge.so` for the device ABI; if the native library is absent, provider search will be unavailable.

## Streaming-provider verification
4. Search a known title and confirm results from MovieBox, 4KHDHub, and/or Dramachi appear when upstream providers are reachable.
5. Open a provider result. Confirm the details dialog searches for streams, shows source options, and enables Play only after a non-empty HTTPS stream URL is returned.
6. Play a stream with Media3 and test seeking, pause/resume, quality selection, back navigation, and portrait/landscape. Confirm source-specific headers are honored for streams that require them.
7. Test an unavailable title, a provider timeout, and offline mode. The app should not crash or open a blank player; it should continue to the next provider or the Internet Archive fallback.
8. Search a TMDB result that is also present at a provider. Confirm the app searches the provider by title and does not send the TMDB ID as the provider ID.

## Existing functionality
9. Open a qualifying open-license Internet Archive film, verify metadata and MP4 playback, and test Android DownloadManager completion.
10. Test favorites, playback progress, search, catalogs, Wi-Fi/mobile-data download settings, and interrupted network requests.

**Not yet implemented in this update:** downloading native-provider streams, TV season/episode stream selection, subtitle forwarding for native-provider streams, and BDIX provider enablement. This checklist is for verification; no Android build/device test has been performed in the editing environment.

## Episode playback (added in this pass)
- [ ] Open a TV series, choose a season, and tap an episode row.
- [ ] Confirm the UI enters the provider-search state and displays returned source/quality options.
- [ ] Select a source and press Play; confirm the selected episode starts.
- [ ] Try an unavailable episode and confirm a clear no-source message appears.

## Build and runtime acceptance
- [ ] Run the GitHub Actions workflow `Android APK` with `buildRustProviders=true`.
- [ ] Confirm `app-debug.apk` contains `libdecanmovie_bridge.so` for arm64-v8a and x86_64.
- [ ] Install on a physical Android device and test one authorized provider title end-to-end.
- [ ] Check playback errors, switching sources, subtitles, and downloads separately.

A successful ZIP integrity check is not a substitute for these build and device tests.
