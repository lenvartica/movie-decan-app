# Decan Movie verification checklist

1. Build the debug APK using the included GitHub Actions workflow or Android Studio (JDK 17, Android SDK 35, Gradle 8.9).
2. Launch on an Android 8.0+ device and accept the Terms and Conditions.
3. Confirm Browse loads; with no TMDB credentials, the app should still attempt to show anime metadata and rights-filtered Internet Archive films.
4. Open a licensed archive film: details should show title, poster, rights information and quality options.
5. Tap Play and confirm ExoPlayer plays the direct MP4.
6. Download the film, wait for Android DownloadManager completion, and play it from Downloads.
7. Open a normal TMDB-only title: its details should show rating and metadata, search for a verified licensed match, and keep Play disabled if no match is found.
8. Test portrait/landscape, back navigation, favorites, search, mobile data/Wi-Fi settings, and interrupted network requests.
