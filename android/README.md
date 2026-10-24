# 漫频 Aether · Kotlin Android

Native Android implementation of the immutable two-page design. The app uses Kotlin `View`/`Canvas`, native text and SVG paths, individual material skins, and the original lunar GLES shader. It contains no WebView and no whole-interface screenshot drawable. The launcher uses the user-selected temporary E1 raster from `branding/icons/current/`, preserving its full composition inside Android adaptive masks. Its generated-raster provenance is retained; this is not final brand approval.

The permanent APK application ID is `io.github.dpan538.aether`; debug evidence uses `io.github.dpan538.aether.debug`. The initial distribution channel is a preview. Aether has its own local preview signing identity for lasting in-place updates; no other app's signing material or store credentials are used. See [APK distribution](release/README.md).

## Build

Android 12+ (API 31 minimum), compile/target API 36. Pinned versions: AGP 8.13.2, Gradle 8.13, Kotlin 2.2.0, AndroidX Core 1.17.0, AndroidSVG 1.4, Media3 1.8.0 and apksig 8.13.2. Dependency locks are in `app/gradle.lockfile`; the wrapper verifies the official distribution SHA-256. Use JDK 17+ supported by Gradle 8.13; verification used JDK 21.

```sh
# From android/, with a normal Android SDK and compatible JDK installed:
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

This workspace also has an isolated, ignored `.tooling/` containing the copied SDK/JDK/dependency cache and its own AVD. `scripts/gradle-local.sh` uses that local runtime. Toolchains, caches, AVD data, APKs and local captures are not committed.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Signed preview delivery: `app/build/outputs/apk/release/app-release.apk` (requires Aether's ignored local signing configuration). The `verification` type uses the permanent package and signer with debug diagnostics; it is only an upgrade fixture.

Compatibility was checked against [AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes), [Kotlin/AGP support](https://developer.android.com/build/kotlin-support), [Gradle 8.13](https://docs.gradle.org/8.13/release-notes.html), [Media3 releases](https://developer.android.com/jetpack/androidx/releases/media3), and [Media3 playback setup](https://developer.android.com/media/media3/exoplayer/hello-world). These are deliberate compatible pins, not a claim to use every newest library.

## Run

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Real directory/player (also the launcher default):
adb shell am start -n io.github.dpan538.aether.debug/com.aether.radio.MainActivity

# Deterministic reference mode, which never creates a player or opens a stream:
adb shell am start -n io.github.dpan538.aether.debug/com.aether.radio.MainActivity \
  --es mode reference --es scenario modern-ink
```

Reference scenarios (debug only): `modern-ink`, `modern-lit`, `modern-settings`, `modern-language-menu`, `modern-genre-menu`, `modern-mute`, `modern-off`, `preset004`, `immersive`, `locked`, `empty-results`, `tempo-min`, `tempo-max`, `volume-max`. They use the frozen four stations, programme text, clocks, 20/30 volume, `[0,2,1,3]` favorites, and static visual instruments. MOCK/real data are separate. The distributable release always uses real mode.

Debug-only launch extras support repeatable evidence: `light_time` (integer milliseconds 0–1050), `refresh_time` (long milliseconds 0–1800), `phase_time` (long milliseconds), `motion`, `animate_light`, `animate_power`, and optional `touch_trace`. They add no controls to the designed UI. After a true native frame completes, `run-as … cat files/frame-ready.txt` identifies the scenario. `files/runtime-state.json` reports actual device, state and Media3 observations; diagnostics are local to the debug app.

## Interaction and state

Both pages use one `Session` and, in live mode, exactly one ExoPlayer. Horizontal gestures drag continuously; release settles over 280ms, cancellation over 220ms with the physical easing. These two release durations are Android implementation choices because the frozen browser host had no transition duration. Immersive vertical gestures and the two triangles select stations.

The 0–30 volume encoder is relative: pressing never seeks. Center drags and wheel-edge rotation consume fresh travel with the frozen thresholds, clamps and time gates. Direction reversal, cancel and idle discard residual travel; there is no inertia queue. Mute restores the last mute level. The upper strip is an absolute control of that same value. The large wheel rotates only its pointer; its metal shading and leaders stay fixed. The four-dimension mapping remains the handoff's proposal, implemented as station/region/genre/language filtering, not a newly approved product rule.

Front light uses the specified 1050ms curve independently of the 1800ms ink refresh and 1850ms interaction lock. Keys have 90ms travel. Native display-list layers are recorded at the actual device pixel dimensions (3408×2272 on the target), with static materials separate from animated pointers and local cap movement. Resource decoding and SVG preparation run on a worker before input is enabled, and reusable shader renders are cached privately. Native key, detent and end-stop haptics honor the system setting; actual motor feel needs target-device validation. Power-off freezes ink data and demonstration phase, closes illumination, disables controls and extinguishes LEDs. System animation scale zero skips refresh/transition demonstrations. Background drawing stops; returning does not replay queued animation frames.

The user subsequently authorized **real tempo and PCM spectrum**. In live mode, Tempo changes Media3 playback speed from 50–150% in 5% steps while pitch remains 1.0. Equalizer/monitor toggle an FFT of actual decoded PCM. Analysis is in the audio-processor chain, requires no microphone permission, passes audio through, and draws native segmented bars; it does not invent measurements or implement an unrequested multiband equalizer. Reference mode retains the frozen demonstration. Expanded About content scrolls inside the ink screen.

The modern Settings update entry opens an ink panel in the existing display. It reads public Aether GitHub Releases, compares integer version codes, downloads over controlled HTTPS redirects, checks size/SHA-256/cryptographic signature/package/version/current signer, and opens the Android installer. Complete verified APKs remain in private persistent storage and are reused after cancellation or process restart. Range requests resume incomplete files when supported. Installation permission refusal preserves the verified package. See [distribution contract](release/README.md).

## Real data and lifecycle

The live repository uses HTTPS Radio Browser mirrors, a bounded 100-record name query, cancellation generations and a local initial-directory cache. It preserves service response order; there is no popularity ranking or recommendation system. Filters and active random selection operate on the loaded result set. Favorites persist UUIDs and the station records needed to recall them after a different directory query. Extra favorites scroll horizontally; directory results scroll vertically.

The directory is a source, not a guarantee of worldwide availability. Its standard response does not provide a complete city/callsign/time-zone index. Those fields stay absent; name search and the supplied country/language/tags are supported. Station clocks, programme and bitrate fields are not inferred from country or branding. Actual ICY titles and decoder MIME/bitrate are shown only when supplied. Search refreshes cannot rename the playing station, and metadata is accepted only for its originating media item. Directory errors stay separate from player errors; retained results are labelled CACHE.

The upper clock is explicitly device time. Battery percentage, **battery** temperature and network transport come from Android system APIs, with absent readings left blank. They are not claims about room temperature, station location, signal strength or connection latency. Favorites, their recall records, the last selected station, volume/mute restoration, Tempo and UI language persist locally. Cold starts restore selection without automatically starting audio.

Only HTTPS streams are accepted. An automatic approval review rejected broad cleartext enablement, so the implementation uses the safer HTTPS-only configuration. HTTP-only stations are excluded rather than silently changing that boundary.

Media3 handles music audio focus and becoming-noisy events. This implementation pauses on Activity stop and releases on destroy; it has no background service, lock-screen playback promise, recording, rewind or time-shift. The transport arrows select adjacent stations/favorites. Resume does not automatically start another stream. Power-on restores retained play intent within the current session. These lifecycle choices are recorded implementation scope, not a claim that all product policy questions are finalized.

## Visual resources and verification

`scripts/export-scene.mjs` reads the unmodified frozen preview at port 4177. With Node and Playwright available, run it from `android/` (`PLAYWRIGHT_MODULE` and `CHROMIUM_PATH` can select local installations). It exports geometry, native text baselines, SVGs, and **empty individual material surfaces**. Static CSS colors are converted at export time. Text, icons, station lists, menus, pointers, slider position, presses and state remain native. The original lunar fragment shader is copied exactly to `assets/disc.frag`; no external reference artwork is bundled. Arimo/Cousine are OFL fonts, with notices in the APK and `third-party/`.

The scene is fitted uniformly into the available content area using a 1704×1136 logical frame, centered with letterboxing when needed. A separate phone/portrait rearrangement is not invented. Font scale is fixed to 1.0 for reference evidence; custom-drawn typography is not a completed dynamic-type accessibility implementation.

`scripts/capture.py` saves raw device PNGs and state. `scripts/compare.py` makes 50% overlays, raw absolute differences, explicitly labeled 4× difference previews and region pairs, without registration or changing any baseline. `scripts/ui-regression.py` drives real touch/key events. See [verification report](verification/REPORT.md) for the actual tested build, metrics, evidence and remaining differences. Compilation alone is not visual acceptance, and this delivery does not claim 100% pixel equality or physical-tablet validation.
