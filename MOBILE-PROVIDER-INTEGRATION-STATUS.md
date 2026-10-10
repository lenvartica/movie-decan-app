# Decan Movie — MovieBox-TUI provider integration status

## Wired into the Android app
- Added the full MovieBox-TUI Rust source tree under `native/MovieBox-Tui` (MIT OR Apache-2.0 upstream licensing files retained).
- Added a JNI `cdylib` bridge in `native/decanmovie-bridge`.
- Configured native provider search and stream resolution for MovieBox, 4KHDHub, and Dramachi.
- Search results from those providers are appended to TMDB/add-on search results when the native library is present.
- Opening a title resolves provider-specific IDs rather than treating TMDB IDs as stream-provider IDs. For TMDB titles, provider search is used first and matching is conservative by title/year.
- Stream options include quality labels and HTTP headers; Media3 receives those headers during playback.
- GitHub Actions installs Rust targets, Android NDK, and `cargo-ndk`, builds the native bridge, and then builds the APK.
- BDIX CircleFTP/DhakaFlix are excluded from the enabled list because upstream documents them as region-specific.

## Not claimed as verified
- This environment has no Cargo/Rust, Gradle, or Android SDK, so neither the Rust bridge nor the APK could be compiled here.
- No physical Android device playback test was possible. The first successful GitHub Actions build and a device test are required to verify the JNI signatures, native dependency build, upstream API availability, and stream compatibility.
- Provider APIs/mirrors can change or block requests. A provider may return no streams for a given title.
- Native-provider downloads, subtitle forwarding, and TV season/episode playback are not completed by this patch; Android DownloadManager remains restricted to the supported Internet Archive direct-file path.
- Community add-ons in the Catalogs tab remain catalog/metadata-only.

## Local provider-enabled build
Install a Rust stable toolchain, Android SDK/NDK, and `cargo-ndk`; install Android targets `aarch64-linux-android`, `armv7-linux-androideabi`, and `x86_64-linux-android`; set `ANDROID_NDK_HOME`; then run from the project root:

```sh
gradle --no-daemon -PbuildRustProviders=true assembleDebug
```

The CI workflow performs those steps and publishes `app-debug.apk` as the `decan-movie-debug` artifact on success.


## Latest follow-up
TV episode rows now initiate provider resolution for the selected season/episode, then refresh the source list before playback. This is source-level implementation only; native compilation and device playback still require the GitHub Actions build and a real-device test.

## Follow-up UI changes
- Added a right-side overflow menu on the Browse header for Browse, Catalogs, Saved, and Downloads.
- Expanded movie details to full available dialog width to make the poster, metadata, episode list, and source selector easier to use on phones.
- Added a Delete action for downloads; this removes the DownloadManager task/file and the local entry.
- Added TOP Streaming Catalogs to the metadata-only catalog sources.
- TV episode source selection state now resets when the source options change.

These are not build-verified in this environment. A successful CI APK build and actual Android playback test are still required.
