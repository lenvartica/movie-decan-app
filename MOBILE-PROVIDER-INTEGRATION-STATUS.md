# Decan Movie mobile playback status

## Fixed in this update
- Selecting a title opens the details window immediately.
- The app now asynchronously searches the rights-filtered Internet Archive catalog for a close title match before enabling playback.
- A dedicated `Licensed Films You Can Play` home row is loaded independently of TMDB availability.
- TMDB vote averages are carried through the model, saved library, and displayed on cards/details.
- The Play action refuses to open the player when the URL is empty and gives a useful message instead.
- GitHub Actions can build the APK without requiring TMDB credentials; the app still supports optional TMDB key/token configuration.

## Important boundaries
- Internet Archive playback/download only accepts files whose metadata indicates an open license or public-domain status and that are direct MP4 URLs.
- TMDB, Kitsu, and metadata add-on results are discovery metadata, not video files. A title is playable only when a separately verified media URL is found.
- MovieBox-TUI Rust sources are included as reference, but this Android app does not yet compile them into a JNI/NDK library or invoke their scrapers. Some provider implementations include provider-specific request signing, anti-bot behavior, or mirror resolution; they require an explicit Android port and separate review before integration.
- No Android SDK/Gradle installation is available in the editing environment, so an APK build and physical-device test could not be run here. The GitHub Actions workflow is configured to perform the build after the ZIP is pushed to GitHub.
