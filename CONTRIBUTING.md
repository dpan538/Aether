# 贡献者须知 · Contributing to 漫频

欢迎为小米之外的安卓平板提供适配。请先阅读公开设计参考，**从新分支开发，通过 PR 提交到 `main`**；适配应保留漫频的视觉和交互，并提供目标设备上的真实验证。

## Contribution focus

The primary supported device is **Xiaomi Pad 9 Pro Max**, landscape **3408 × 2272**. Community adaptations for other Android tablets are welcome through pull requests. A device is not advertised as supported until its adaptation has been reviewed and tested. Keep the Xiaomi baseline working.

Read these before changing the interface:

- [Public design and tablet adaptation reference](ADAPTATION.md), including both current approved mock images and the implementation entry points.
- [MIT code license](LICENSE), [visual-assets contribution license](VISUAL-LICENSE.md), and [third-party notices](android/third-party/README.md).

The public design reference and native source are the contributor materials. Private design studies, development discussions, raw verification archives and credentials are not contribution inputs and must not be added to a PR.

## Branch and PR workflow

1. Open an [Issue](https://github.com/dpan538/Aether/issues) describing the tablet model, Android/vendor OS version, pixel resolution, density and the adaptation problem. Link the Issue in your PR. For a small fix you may put those details directly in the PR.
2. Fork `dpan538/Aether` if you do not have write access. Start a **new branch from current `main`**, for example `adapt/lenovo-tablet-layout`. Use one focused branch per adaptation or fix.
3. Make the changes, retaining the existing listening and control behavior. Avoid unrelated visual redesigns and unrelated formatting changes.
4. Build and test. Include evidence from the target tablet and a Xiaomi-baseline regression check; label emulator-only results clearly.
5. Push your branch to your fork, then open a **pull request against `dpan538/Aether:main`**. Use the PR template. Maintainers review and merge; contributors must not push their changes directly to `main`, force-push shared history, or publish official release tags/APKs.

Example (replace `YOUR_GITHUB_NAME` and the branch name):

```sh
git clone https://github.com/YOUR_GITHUB_NAME/Aether.git
cd Aether
git remote add upstream https://github.com/dpan538/Aether.git
git fetch upstream
git switch -c adapt/your-tablet upstream/main
# Implement and validate your adaptation.
git add <changed-source-files>
git commit -m "Adapt tablet layout for your-device"
git push -u origin adapt/your-tablet
```

Create the PR with base `dpan538/Aether:main` and compare `YOUR_GITHUB_NAME:adapt/your-tablet`. Do not commit SDKs, caches, signed packages, signing files, device music, secrets or private reports. Screenshots submitted for review should contain no personal information.

## Build

Use JDK 17 or newer, Android SDK Platform 36 and Build Tools 36.0.0. The repository includes the Gradle 8.13 wrapper. Configure your SDK using `ANDROID_HOME` or an ignored `android/local.properties` file.

```sh
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The debug app uses `io.github.dpan538.aether.debug`, so it can coexist with the installed release. Release signing keys are private and are not needed to contribute. Do not change the permanent release package identity or version as part of a device adaptation.

For native instrumentation on a connected test tablet, build the debug test target:

```sh
./gradlew -PaetherTestBuildType=debug :app:assembleDebugAndroidTest
```

Install the debug and debug-test APKs on the intended device. The instrumentation entry point is `com.aether.radio.NativeEvidence`; select the relevant operation deliberately. Some operations access actual internet radio or the Android document picker. **Never run `feedback-live`** as an ordinary test: it submits a public Issue and requires the project owner's explicit authorization. Use your own audio fixtures and redact personal file names from evidence.

## Validation for a tablet adaptation

Include the device, vendor OS, Android version, resolution, density, font scale, refresh rate and navigation mode. Check:

- Both pages at native resolution: typography, alignment, artwork sharpness, system insets, menus and dialogs; no clipped text or controls.
- Touch, rotary Volume and Tempo, detent sound/haptics, pause/resume, all four station presets, actual PCM spectra and 50%–150% pitch-preserving speed.
- Immersive disc tap and clockwise/counterclockwise seek; track-changing gestures stay inside the upper-right selector. Top-edge system gestures must not skip a track.
- Local import with persisted document permission, embedded-artwork/fallback behavior, album star/delete confirmation and preservation of original device files.
- Continued playback after Home, lock/screen off, Activity recreation and return; notification and home-screen widget controls address the same player. Report test duration and any vendor battery settings used.
- Linear page transitions, ink partial/full refresh, front-light ramp and reduced-motion behavior.

Report actual results and limitations. A directory count is not a count of universally playable stations; an emulator check is not a physical-device pass. Reviewers may request additional tests where the change affects playback or interaction.

## Contribution licensing

Your original code and technical-documentation contributions are submitted under MIT. Visual contributions follow the limited grant in `VISUAL-LICENSE.md`, including permission for maintainers to integrate and distribute them in this project. Retain third-party attribution and declare any external material or licensing constraints in the PR.
