# Adaptive album typography / preview.4

The current delivery is **0.1.0-preview.4 / versionCode5**. Album titles now use natural balanced wrapping at **34–64sp**, with no album-specific forced newline. Names are sized against their actual available width/height; very long titles keep complete text and can scroll with the left column. The user rejected both the sparse corner block and the later 84sp title candidate.

The exact signed non-debuggable APK passed **4 native layout/control checks** at 3408×2272: complete short/long titles and unclipped actions, real playback, booklet opening, and close/return. Release build and lint passed with no errors (13 existing warnings). This typography revision leaves the 11 source recordings, cover, source handling and player unchanged. The **26 native / 18 unit** functional results below are the preceding code4 baseline; they are not fresh code5 runs.

[Current layout acceptance](evidence/album/layout-acceptance.json). APK **83,476,432 bytes**, SHA-256 `bce86ca27fa10df077544e0316d695259b4a3e38180a53bfc9a0189fb141f1fb`. Same permanent package/signer. Physical Xiaomi hardware remains untested.

---

# Before the Rise / preview.3 verification

The signed non-debuggable **0.1.0-preview.3**, versionCode **4**, passed **26 native checks** and **18 unit tests**. Release compilation and lint passed with no errors and 13 existing warnings (pinned dependency versions, launcher resources and the custom-view tooling constructor).

The native cases ran on the owned API36 ARM64 emulator at **3408×2272, density320**. This verifies the requested screen geometry, not physical Xiaomi Pad 9 Pro Max audio hardware, tactile response or color reproduction. This is a targeted extension suite; the earlier 300 completed repair cycles are not recounted as tests of this APK.

[Machine-readable acceptance](evidence/album/acceptance.json) · [Verified recording durations](evidence/album/recordings.svg) · [Album/provenance](../album-art/before-the-rise/README.md)

- The upper-right immersive registration opens an actual source menu; the album sheet uses unchanged J3-01 artwork, black/white/gray UI, a 68dp close target and lower-left title/credit/actions. The marked bottom dock and top file link are absent.
- Every one of the 11 actual bundled files was selected by a native touch, decoded as MP3, checked against real decoder duration, and observed producing actual PCM. These are short decoded samples, not a full-length subjective listening claim. Source SHA-256 values all match the supplied recordings.
- Album pause/resume, 50–150% speed with pitch1, automatic advance, final-track ending/replay, immersive triangle navigation, both pages’ metadata/spectra, real HTTPS-radio return and radio-to-local recovery passed. End transitions were exercised by seeking near the end through the player API; the final album sheet intentionally has no seek bar.
- The Android document picker returned a real audio URI with persisted read permission. After a cold launch, the selection stayed paused and could be reopened. Missing-document playback produced a genuine error with no fake PCM, and bundled playback recovered afterward. No microphone or broad storage permission was added.
- An ADB package-manager in-place installation upgraded versionCode3→4 using the same permanent package and signer. Station UUID, favorite, volume17, Tempo90 and Chinese persisted. The unchanged updater’s public download/cache/installer flow was verified for preview.2; this extension did not repeat that whole public flow.

APK: **83,461,896 bytes**. SHA-256: `ae624f62a211f4fccc74a9df81690af083f5bd2180cf65e80e533495c1d4e2d3`. Verified signer: `934601abcd99dd1cfbd7466f6096fd2c27a6e6ae2e75681d5233a4fd6e1a15b9`.

Earlier debug harness attempts are retained: one incorrect dialog-window coordinate calculation, one missing Downloads navigation, and one accidental click on Android’s preview icon. They are not counted as final passing cases. All 26 final release cases passed after correcting the harness. Full raw output, the HTML report, chart and native captures are in the portable release evidence bundle and ignored `verification/local/album/`.
