# Approved 99-ring launcher / preview.5

**0.1.0-preview.5 / versionCode6** integrates the user-selected 99-ring identity. The 60/33-ring trials remain separate. All old E1 Android resource files/references were removed. Legacy 48/72/96/144/192px assets and the unmasked 512px adaptive input match the prepared brand hashes.

The adaptive resource is explicitly selected from `mipmap-anydpi-v26`, replacing the earlier inspected artifact’s legacy-only selection. Its foreground is the square master derivative with **16.9% proportional inset**, and its background is `#0B0D12`. There is no pre-rounded foreground. Scaling is applied to the completed artwork; the 99 rings are not redrawn at a fixed dp stroke.

The outer-art diameter is 65.77632dp within the 66dp guaranteed safe circle for a 108dp layer ([Android adaptive-icon guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive), [AOSP mask constraints](https://source.android.com/docs/core/display/adaptive-icons)).

The signed non-debuggable release passed native icon acceptance at **3408×2272 / API36 ARM64 emulator**. PackageManager returned **AdaptiveIconDrawable**. Its 512px native draw has symmetric significant-art bounds **[42,42,469,469]**; the real Pixel launcher screenshot shows centered round-masked production/debug icons without double rounding or visible ring clipping. This is not a physical Xiaomi test. Release/debug builds and release lint passed without errors; warnings are retained, including the standard v26 qualifier and absence of an unrequested monochrome variant.

[Machine-readable evidence](evidence/icon99-acceptance.json) · [Asset inventory](evidence/icon99-assets.json). Playback/page sources are unchanged, so previous album/typography tests remain their own baselines; no new 300-cycle or audio sweep is claimed.

APK **80,688,760 bytes**, SHA-256 `06b87d05263f0dc56f6094387d95acd91af8ae7a83fed0bf3f107580789e4724`. Same permanent package and signer. Portable evidence includes the actual launcher, native drawable render and original instrumentation/resource output.
