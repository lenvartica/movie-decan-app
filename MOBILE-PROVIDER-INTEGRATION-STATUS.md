# Decan Movie — Mobile Provider Integration Pack

This package combines the supplied Decan Movie Android project with a preserved reference copy of MovieBox-TUI's provider source and its MIT/Apache license notices. The Android display name remains **Decan Movie**.

## Included
- Existing Kotlin/Jetpack Compose Android application and ExoPlayer player.
- First-run Terms and Conditions gate. Users must tick the agreement checkbox before entering the app; acceptance is stored locally for terms version 1.
- Expanded metadata catalogs and the existing saved/download features.
- `third_party/MovieBox-Tui-provider-source-reference/`: original provider modules, provider documentation, Cargo manifest and upstream license texts.

## Important integration status
The original provider modules are Rust code designed for MovieBox-TUI's async Rust service. They are included as source reference, **not silently represented as already linked into the Android APK**. A working native integration requires a Rust `cdylib`/JNI boundary, Android NDK toolchains for supported ABIs, async-runtime adaptation, and Android-specific networking/player/download glue. The original TUI's terminal UI and desktop player launcher are not Android UI components. Rust-to-Java/Kotlin native integration and Android `.so` packaging are described by the Android NDK/JNI docs and cargo-ndk project.

No prebuilt `.so` libraries are included, and no successful Gradle/NDK build or on-device provider playback test is claimed. See `third_party/.../docs/providers.md` for provider descriptions. Provider access may depend on service availability, provider terms, regional network reachability and authorization.

## Terms
The app now presents a first-run acceptance screen. It is a practical user agreement, not legal advice or a substitute for provider-specific licenses. Before commercial distribution, have counsel review it for your operating jurisdictions and publish a privacy policy/contact route. Increment `terms_v1_accepted` to a new versioned preference key when you change the terms and need users to accept again.

## Build
Open this folder in Android Studio with JDK 17, Android SDK 35 and Gradle support. Build the Android app first. The native Rust provider code is not compiled by the existing Gradle workflow yet. To add it, implement a stable JNI API and build per ABI (for example `arm64-v8a` and `x86_64`) using Rust Android targets and cargo-ndk, then load the libraries from Kotlin and test on actual devices. Do not enable a provider until its integration, content rights, and download behavior have been validated.

## Branding
The launcher label is Decan Movie. The internal Android namespace/application ID has been left unchanged to avoid accidentally breaking existing installs or app links.
