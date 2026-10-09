# Decan Movie Box

Native Android starter app for browsing Internet Archive film records, matching them to TMDB metadata, streaming MP4 files, and saving permitted movies for offline playback.

## Build

- Android Studio with Android SDK 35 and JDK 17, or Gradle 8.9.
- Provide a TMDB API key as the `TMDB_API_KEY` environment variable or Gradle property.
- Build the debug APK with `gradle assembleDebug`.
- In GitHub, add a repository Actions secret named `TMDB_API_KEY`. The Android APK workflow uploads `app-debug.apk` as the `decan-movie-box-debug` artifact.

The TMDB key is compiled into the app and can be extracted from any distributed APK. A backend proxy is recommended before a public production release. The workflow currently creates an unsigned debug/testing APK, not a Play Store release.

## Rights and catalog behavior

The app accepts Internet Archive records only when the record exposes a recognized Creative Commons reuse license or a public-domain mark/CC0 license, has an MP4 source, and has a confident TMDB title match. It shows the catalog license and source in the movie details. Catalog license metadata is not independently verified; review rights before distributing or relying on any title. Respect license conditions, including attribution and non-commercial terms.

Downloads are stored in the app-specific Movies directory and are removed when the app is uninstalled. Android's system Download Manager handles progress and completion notifications. When an archive record provides multiple MP4 files, the download confirmation lets the user choose a source, ordered by resolution when available.

## Current scope

The initial APK focuses on Android 8.0+ phones/tablets, English metadata, on-device favorites and playback position, standard Media3 playback controls, audio/subtitle track selection when present, speed control, and landscape orientation. The workflow builds a debug artifact for testing; it does not sign a production APK.
