# Implementation and data contract

The immutable two-page snapshot remains the visual reference. The user's later instruction adds real APK updates from the repository and requires working controls backed by real data. They explicitly selected real 50–150% tempo with preserved pitch and real audio spectrum. Those additions supersede the earlier demonstration-only restrictions for the **live** app; the debug reference mode retains the frozen MOCK states for comparison.

## Controls

| Control | Live behavior / source |
| --- | --- |
| Power | Stop/resume the shared player within the session; simulated ink refresh retains the last actual ink readings and extinguishes lighting/LEDs |
| Lock | Disable tuning, filtering, playback and volume controls; retain unlock and front light |
| Light | Coordinate warm/ice/green front light over 1050ms without starting an ink refresh |
| Settings | Language, repository updates and About within the modern page |
| Large tuning wheel | Damped relative detents; station or the selected region/genre/language dimension; centre dead zone |
| Small selector | Four stops for station / region / genre / language |
| Four presets | Recall persisted real station records; empty slots disabled; extra favorites scroll horizontally |
| Outer transport keys | Previous/next favorite in the loaded favorites pool |
| Inner transport arrows | Previous/next station in the filtered loaded pool |
| Play/pause | Actual Media3 playback state and retry after a stream error |
| Volume encoder | Relative 0–30 steps, fixed material shading, no change on press |
| Upper volume strip | Absolute setting of that same player volume |
| Speaker key | Mute and restore the previous nonzero level |
| Tempo | Media3 playback speed in 5% steps, pitch 1.0 |
| Equalizer key | Toggle real decoded-PCM spectrum; no invented spectrum or unrequested gain controls |
| Wave/monitor key | Optional visual phase plus the real PCM monitor; system reduced motion suppresses decorative phase |
| Directory / Favorites | Loaded public directory or persisted favorites, with vertical scrolling |
| Search / reset | Native IME input; real bounded name search; reset the local combined filters |
| Region / genre / language menus | Combine fields supplied by the loaded public station records |
| Star | Add/remove the current actual station and its recall record |
| Random | User-triggered random choice from loaded filtered results |
| Immersive triangles / vertical swipe | Previous/next station, preserving the same player/session |
| Horizontal page gesture | Continuous drag and settle, preserving station, volume and player |

The scope, audio format, calibration, leader lines and printed tuner chevrons are read-only elements. No third page or immersive utility menu was added.

## Data

Radio Browser supplies name, country, language, tags and HTTPS stream endpoints. The bounded directory retains source response order. Name search is supported; the service does not provide a complete city/callsign/time-zone index. Missing fields stay absent. Cached results are labelled CACHE after a directory failure; directory and playback errors are separate.

ICY title events are associated with their Media3 media item. Decoder format and bitrate appear only when supplied. The spectrum is a Hann-windowed FFT of decoded PCM before output volume/time stretching, with real dBFS band values. It is neither microphone capture nor a calibrated acoustic measurement. The audio processor passes those samples through unchanged; Media3's speed processor applies Tempo while preserving pitch.

The upper clock is device time. Battery percentage, **battery temperature** and network transport are Android system readings. They are independent of station identity and stream availability. Station-local time, schedule and artwork provenance are not inferred from country or branding. Artistic disc coloration is authored artwork, not a measurement of the stream.

The live app persists favorites and their station records, last selected station, volume, mute restoration, Tempo and UI language. It restores selection without automatic playback on cold launch. Both pages share one Session and one ExoPlayer. This delivery pauses on Activity stop and releases on destroy; it has no background playback service, recording or time shift. That lifecycle policy is an implementation choice, not a new frozen product decision.

## Updates

The permanent application ID is `io.github.dpan538.aether`. Aether has an independent preview signing identity. The first external APK installation is followed by in-app repository checks and same-signer Android covering installs. Verified downloads are retained privately and reused, including after installer cancellation and process restart. See [distribution](release/README.md) and the [verification report](verification/REPORT.md) for the actual artifact and upgrade test.

Annota's distribution and update acceptance documents were read for workflow lessons. Its app code, assets, configuration rules and signing material were not imported into Aether.
