# Decan Movie Box

Decan Movie Box is a native Android movie discovery app with TMDB metadata, Kitsu anime metadata, metadata-only add-on catalogs, and a rights-filtered Internet Archive fallback. Playback and downloads are enabled only for direct Internet Archive MP4 files that pass the app’s license metadata checks.

## Contents

- [What you need](#what-you-need)
- [Get TMDB credentials](#get-tmdb-credentials)
- [Build locally on Windows](#build-locally-on-windows)
- [Build an APK with GitHub Actions](#build-an-apk-with-github-actions)
- [Catalog and metadata add-ons](#catalog-and-metadata-add-ons)
- [Check that the key works](#check-that-the-key-works)
- [Troubleshooting](#troubleshooting)
- [Important key security information](#important-key-security-information)
- [Catalog rights and attribution](#catalog-rights-and-attribution)
- [Current app scope](#current-app-scope)

## What you need

For a local build, install:

- Android Studio with Android SDK Platform 35.
- JDK 17.
- Gradle 8.9, if building from PowerShell rather than Android Studio.

The GitHub Actions workflow installs JDK 17 and Gradle 8.9 for you. For TMDB, add the repository secret `TMDB_ACCESS_TOKEN` (recommended) or the legacy `TMDB_API_KEY`.

## Get TMDB credentials

1. Create or sign in to your account at [themoviedb.org](https://www.themoviedb.org/).
2. Open your account settings, then open **API** (usually available at [themoviedb.org/settings/api](https://www.themoviedb.org/settings/api)).
3. If TMDB asks you to register an API application, follow its developer/API application form and describe Decan Movie Box accurately. Accept TMDB's current terms and attribution requirements.
4. Copy the **API Read Access Token (v4 auth)**. The app sends it to TMDB using the `Authorization: Bearer ...` request header.

The older **API Key (v3 auth)** is still accepted as a fallback. Prefer the API Read Access Token (v4) for new builds. Do not use your TMDB account password. Never share either credential or commit it into project files.

Keep your credentials private while developing. Do not paste them into a chat, publish them in a screenshot, or commit them into source files.

## Build locally on Windows

### Option A: Keep the token in your personal Gradle properties

This is the recommended option for local builds because the file is outside the project folder and should not be committed.

1. Close the project in Android Studio if it is open.
2. In File Explorer, open your Windows user folder and then the `.gradle` folder. The usual path is:

   ```text
   %USERPROFILE%\.gradle
   ```

   If `.gradle` does not exist, create that folder.
3. Create or edit a file named `gradle.properties` inside that folder. For example:

   ```text
   C:\Users\YourWindowsName\.gradle\gradle.properties
   ```

4. Add this line, replacing the example with your actual **API Read Access Token (v4 auth)**:

   ```properties
   TMDB_ACCESS_TOKEN=replace_with_your_v4_read_access_token
   ```

   Do not include quotes or spaces around the `=` sign. Keep the token on one line.
5. Save the file. Reopen the project or run Gradle again so it reads the updated property.
6. In Android Studio, open this project folder and build the `app` debug variant. Or use a PowerShell terminal in the project folder:

   ```powershell
   gradle --no-daemon assembleDebug
   ```

   The resulting testing APK is:

   ```text
   app\build\outputs\apk\debug\app-debug.apk
   ```

The Gradle build reads `TMDB_ACCESS_TOKEN` first as a Gradle property and then as an environment variable. If it is not set, the build falls back to `TMDB_API_KEY`. A personal Gradle properties file is outside the repository and is not included in Git commits.

### Option B: Set the token for one PowerShell window

This avoids saving the key in a file, but the key is available to processes launched from that PowerShell window. Open PowerShell in the project folder and run:

```powershell
$env:TMDB_ACCESS_TOKEN = "replace_with_your_v4_read_access_token"
gradle --no-daemon assembleDebug
```

The environment variable applies only to that PowerShell window and its child processes. Open a new PowerShell window and set it again for another build. To use the legacy v3 API key instead, set `$env:TMDB_API_KEY`.

## Build an APK with GitHub Actions

### 1. Put the project in a GitHub repository

Create a repository on GitHub and push the project files to the `main` or `master` branch. The workflow must be in this exact path in the repository:

```text
.github/workflows/android.yml
```

GitHub does not discover workflows stored in `github/workflows` without the leading dot. The project workflow in `.github/workflows/android.yml` starts on pushes to `main`/`master`, pull requests, and manual runs.

### 2. Add the token as a GitHub Actions secret

1. Open the repository page on GitHub.
2. Choose **Settings**.
3. In the left menu, open **Secrets and variables** → **Actions**.
4. Under **Repository secrets**, select **New repository secret**.
5. Enter the following exact name:

   ```text
   TMDB_ACCESS_TOKEN
   ```

6. Paste your TMDB **API Read Access Token (v4 auth)** into the secret value field.
7. Select **Add secret**.

The secret name is case-sensitive for this workflow. Do not add quotation marks or spaces around the token. Do not put credentials directly in `android.yml`, the README, Kotlin files, or any other tracked project file. The workflow can build without a TMDB credential; TMDB-powered rows will be unavailable until you add one, while fallback catalog paths remain available.

The workflow passes the secret to Gradle as an environment variable only during the APK build:

```yaml
env:
  TMDB_ACCESS_TOKEN: ${{ secrets.TMDB_ACCESS_TOKEN }}
  TMDB_API_KEY: ${{ secrets.TMDB_API_KEY }}
```

After changing the secret, start a new workflow run and download its new artifact. An APK you already downloaded will not be updated with a newly added token.

### 3. Run the workflow and download the APK

1. Push a commit to `main` or `master`, or open the repository's **Actions** tab and select **Android APK**.
2. For a manual build, choose **Run workflow**.
3. Open the finished workflow run. A successful run has a green check mark.
4. Scroll to **Artifacts** and download `decan-movie-box-debug`.
5. Extract the downloaded artifact. It contains `app-debug.apk`, which you can install on an Android device for testing.

This is an unsigned **debug/testing APK**, not a signed production release or Play Store package. GitHub does not expose repository secrets to workflows triggered by pull requests from forks, so the secret-check step will fail for those runs. To build from an external contribution, run the workflow from a trusted branch after reviewing the changes.

## Catalog sources

The Catalogs screen is preconfigured. Users do not need to add or paste URLs.

- **TMDB API** — `https://api.themoviedb.org/3/` for movie and TV metadata, search, seasons and episodes. Configure your own `TMDB_ACCESS_TOKEN` (recommended) or `TMDB_API_KEY` at build time.
- **Kitsu Anime API** — `https://kitsu.io/api/edge/trending/anime` for anime discovery and `https://kitsu.io/api/edge/anime?filter[text]=...` for anime search. This public metadata API does not require a user-supplied URL.
- **Internet Archive** — `https://archive.org/advancedsearch.php` for catalog discovery and `https://archive.org/metadata/{identifier}` to inspect item metadata and candidate media files. Playback/download buttons are enabled only for compatible HTTPS Archive MP4 files that pass the app's license-metadata filter.

If TMDB is offline or no credential is configured, Kitsu and Internet Archive remain independent fallback sources. A metadata entry is not a video stream; not every title will have a playable source. The app does not use third-party stream-resolver add-ons.

The main Browse screen has a hamburger menu and bottom navigation for Browse, Catalogs, Saved Movies, and Downloads. Tapping a movie opens a full details page with an in-app back button and Android back navigation. Series details include season and episode metadata when TMDB is available.


## Check that the key works

- TMDB-powered home rows and season details need a newly built APK containing either `TMDB_ACCESS_TOKEN` or `TMDB_API_KEY`. If both are missing, TMDB requests fail, but search still attempts catalog-only add-ons and the licensed Internet Archive fallback.
- After installing the APK, open **Browse**. A valid key should allow the catalog search to request TMDB metadata. If TMDB returns no results, the app tries metadata-only add-ons and then Internet Archive items that pass the open-license and MP4 checks.
- If the key was just added locally, start a new Gradle build. If it was just added to GitHub, start a new workflow run; already-built APKs do not receive the new key.
- An invalid or disabled credential generally causes the TMDB request to fail. Check that you copied the full API Read Access Token (v4 auth), not the API Key (v3 auth), account password, or a truncated value.

## Troubleshooting

### TMDB-powered rows are missing

Check that `TMDB_ACCESS_TOKEN` (recommended) or `TMDB_API_KEY` is spelled exactly and has a non-empty value. The credential must be present when the APK is built; adding it afterward does not update an APK already downloaded or installed. The APK can still build without a key, but TMDB-powered rows and season details may be unavailable; metadata add-ons and the licensed Archive fallback are separate fallback paths.

### The build succeeds but TMDB returns an authorization error

Confirm the v4 Read Access Token is active in TMDB settings and copied completely without whitespace. The app sends it as a Bearer token. Alternatively, the older API Key (v3 auth) can be set as `TMDB_API_KEY`.

### GitHub Actions does not show the Android APK workflow

Confirm the workflow file is committed at `.github/workflows/android.yml` (including the leading dot), and check the repository's Actions tab/settings to ensure Actions are enabled.

### The APK builds but no catalog titles appear

The app intentionally filters Internet Archive results: an item needs recognized open-license/public-domain metadata and a compatible MP4 file. This automated check is only a preliminary filter, not a legal determination. A working TMDB key does not guarantee that a particular search will find a qualifying Archive item.

## Important key security information

GitHub Actions secrets protect the token while it is stored in the repository settings and passed into the build workflow. They do **not** make the credential secret after it is compiled into this Android app. The current Gradle configuration writes it into the APK's generated `BuildConfig`; someone with the APK can extract it.

Treat this setup as suitable for development/testing, not as a secure way to protect a production API credential. Before broad distribution, use a backend or serverless proxy that holds the TMDB credential on the server and applies appropriate rate limits and restrictions. If a token or key is accidentally committed or otherwise exposed, revoke/rotate it in TMDB and update the local property or GitHub secret.

## Catalog rights and attribution

The app accepts Internet Archive records only when metadata contains a recognized Creative Commons reuse license or public-domain/CC0 marker and a compatible MP4 file. It displays the source and license metadata in the details panel. This automated check is a filter, not legal verification: catalog metadata cannot prove the uploader had the rights to publish a work. Review each title before use or redistribution and comply with all license conditions, including attribution and non-commercial restrictions.

TMDB metadata and images are provided by TMDB and are subject to TMDB's current terms and attribution requirements. Review TMDB's requirements before public distribution.

## Current app scope

The app targets Android 8.0+ phones and tablets, uses English TMDB metadata when configured, and keeps favorites and playback position on-device. If TMDB fails, search can fall back to Internet Archive records that pass the app’s open-license and MP4 checks. The Catalogs tab is metadata-only; those add-on results cannot be played or downloaded. Authorized Internet Archive playback uses Media3 with standard controls, supported audio/subtitle track selection, playback speed, fullscreen orientation, and retry after playback errors. Downloads use Android Download Manager and are saved under Downloads/DecanMovieBox. Compatible MP4 choices are ordered by detected resolution where available. TMDB/Kitsu/add-on results may show metadata without playback, and this version does not implement TV season/episode playback.
