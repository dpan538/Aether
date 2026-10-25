# APK distribution and lasting updates

The permanent APK identity is `io.github.dpan538.aether`. Debug evidence uses the separate `.debug` suffix. The initial distribution channel is an explicitly labelled **preview**. A normal release build is not debuggable; this is independent of its preview label. Store publication is outside this delivery.

The first APK is installed once. Subsequent versions are checked inside the modern screen's Settings → Check updates panel, downloaded privately, verified, and handed to the Android system installer. This preserves the installed app and its local preferences. Android still asks the user to approve installation.

The app reads the latest 30 non-draft Aether GitHub Releases, including preview releases, and selects the highest **integer versionCode** above the installed build. Every eligible release must have the signed APK and `aether-update.json`. It does not compare version strings or treat a missing release as success. No repository token is shipped.

Completed APKs live under the app's private `files/updates/`, keyed by version and SHA-256. Checking, retrying installation, cancelling the system installer, or restarting the app reuses that complete file after verifying it again. Incomplete downloads retain bytes and request an HTTPS Range resume; a server that ignores Range requires a restart. A corrupted cached file is removed and can be downloaded again. Installed/obsolete pending packages are cleaned up.

Before opening the installer, the client checks exact file size, SHA-256, the cryptographic APK signature with Android `apksig`, package identity, version code/name, non-debuggable status, and the currently installed signing certificate. Download redirects must remain on the explicit GitHub HTTPS host allowlist. SHA-256 is integrity evidence; the installed signer supplies the trust boundary.

## Build and publish

`scripts/init-signing.py` creates **Aether's own preview distribution key** once in ignored `.tooling/signing/`. It does not use another app's certificate. Keep that directory and an offline backup permanently. Losing or replacing the private key breaks same-signer updates. Keys and passwords are never committed, uploaded with releases, or included in the APK. A source checkout without this local configuration produces an unsigned release; it must not be distributed.

```sh
# With a full JDK and Android SDK available, from android/:
python3 scripts/init-signing.py
./gradlew :app:assembleRelease :app:testDebugUnitTest :app:lintRelease
python3 scripts/prepare-release.py
```

For later builds, increment `-PaetherVersionCode=N` and set `-PaetherVersionName=X.Y.Z-preview.N`. Preserve the same application ID and signing key. The `verification` build type uses the permanent app identity and the preview signer but remains debuggable; it is a local upgrade fixture, never a distributable release.

Publish the inspected APK, `aether-update.json`, and `SHA256SUMS.txt` together in an Aether GitHub Release. The generated manifest comes from the exact signed APK. Keep prepared artifacts immutable; do not rebuild and silently replace an already published version. GitHub's connector has no release-asset upload operation, so release preparation/upload uses the authenticated `gh` CLI without exposing its credential.

The user requested learning from Annota's update experience. Its distribution and continuous-update acceptance documents were read for channel selection, same-signer upgrades, retained pending packages, and installer permission handling. Aether's implementation, signing identity and app data remain independent; no Annota code, assets or private signing material were imported.

Platform references: [GitHub Releases API](https://docs.github.com/en/rest/releases/releases), [Android signing](https://developer.android.com/studio/publish/app-signing), [package installation](https://developer.android.com/reference/android/content/pm/PackageInstaller).

Initial inspected preview: [v0.1.0-preview.1](https://github.com/dpan538/Aether/releases/tag/v0.1.0-preview.1), integer versionCode 2. Its APK SHA-256 is `9f81bbd8977a9a8bf2d354497652c3e75fee95b0a3ef2d3f483e1650d1670810`. Keep this published artifact immutable; local earlier candidates are not releases.


Previous repair preview: [v0.1.0-preview.2](https://github.com/dpan538/Aether/releases/tag/v0.1.0-preview.2), versionCode3, APK SHA-256 `12eb8b35a0787b6cd8aef70d1b6de7bb63f4024549c907f2c6f8adf2d3648d11`. Preview.1 acceptance is withdrawn; its assets remain immutable. The public2→3 flow downloaded once, reused identical bytes after restart/denial/cancellation, then installed through Android system confirmation. Station/favorites, volume17, Tempo90 and Chinese persisted. See [current verification](../verification/REPORT.md).

Previous album preview: [v0.1.0-preview.3](https://github.com/dpan538/Aether/releases/tag/v0.1.0-preview.3), versionCode4, APK83,461,896 bytes, SHA-256 `ae624f62a211f4fccc74a9df81690af083f5bd2180cf65e80e533495c1d4e2d3`. Same signer and permanent package; native package-manager upgrade retained preferences. Includes all eleven Before the Rise recordings offline. See [album verification](../verification/ALBUM-REPORT.md).

Current typography preview: [v0.1.0-preview.4](https://github.com/dpan538/Aether/releases/tag/v0.1.0-preview.4), versionCode5. APK83,476,432 bytes, SHA-256 `bce86ca27fa10df077544e0316d695259b4a3e38180a53bfc9a0189fb141f1fb`. Same signer/package; adaptive 34–64sp titles and complete long-title fallback. Four native layout/control checks passed; unchanged audio/source behavior retains the code4 functional evidence.
