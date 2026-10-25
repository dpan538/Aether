# Before the Rise

**幻昼·万象归寂录 / Phantasmagoria: The Absolute Dissolution**

**Dai Pan (潘岱)** · 11 instrumentals · 30:39 (rounded)

Created with Google AI assistance. The artist supplied the recordings, concept and track descriptions, and explicitly chose the display credit Dai Pan (潘岱). No model name is inferred.

The 11-track edition moves from a clear night through hypnagogic drift and false awakening, into metal, embers and cold departure, then memory and daylight. This sequence is an editorial arrangement for the supplied recordings, not a measured key/BPM mix or a claim of artist approval of every transition. The two unsupplied works are excluded.

| # | Work | Duration |
|---|---|---|
| 01 | Before the First Light | 2:59 |
| 02 | Glass Garden Portraits | 2:37 |
| 03 | Where Water Meets Stone | 2:48 |
| 04 | The Slowest Tide | 2:39 |
| 05 | The Sleeper’s Lament | 3:01 |
| 06 | Pattern of the Iron Canopy | 2:53 |
| 07 | Three Minutes of Sharpened Iron | 2:50 |
| 08 | The Last Glowing Knot | 2:36 |
| 09 | November’s Glass | 2:50 |
| 10 | The Clearing Vision | 2:32 |
| 11 | Morning’s Sharp Truth | 2:54 |

Table durations round to the nearest second; native playback uses decoder duration and the player’s elapsed-time convention. Exact summed source duration: **1,838,994 ms**. All files are 44.1 kHz stereo MP3 at nominal 192 kbps; manifest `bitrate` is ffprobe’s container-average rate including overhead. Total audio: **44,203,233 bytes**. Instruments, narrative and stated 150 BPM values come from the artist’s descriptions, not independent acoustic verification.

## Artwork and notes

The cover is the existing [J3-01 project export](../../../branding/icons/2026-10-25-symmetric-gradient/exports/J3-01.png), used **byte-for-byte**, 3072×3072. SHA-256: `90c5ff75ebbee30b544f945d93cb854ac87e9ebf710c9c998de1a5348c3a8ae6`. The user rejected a newly drawn replacement; it is not shipped. The existing provisional launcher remains independent of this album cover.

[Notes](notes.json) adapt the supplied Chinese and English concepts to these 11 recordings. Complete track descriptions, filenames, durations, codec details and source hashes are in the [bundled manifest](../../app/src/main/assets/albums/before-the-rise/manifest.json). Source MP3 bytes are unchanged: no re-encoding, added ID3 tags, removed watermarks or invented artist metadata. Album presentation comes from the manifest.

Regenerate the app bundle with `python3 scripts/prepare-local-album.py --source /path/to/the/supplied/files` from `android/`. Do not modify originals. No general code, music or brand license is granted by this delivery.

## Native presentation

Immersive ellipsis → source menu → Before the Rise. The user requested an Apple Music Classical-inspired hierarchy, then a black/white/neutral-gray album sheet: unchanged large cover above, title/credit/actions occupying the lower-left area with adaptive 34–64sp balanced title wrapping, independent right-hand track list, large close target. The marked bottom status, production credit, progress/control dock and top file-import link were removed. The Chinese concept heading appears only in the booklet; the lower-left gray phrase is 22sp. A single album play/pause/resume control and track selection remain. Full production notes are in the booklet.

Device audio is selected separately through the ellipsis source menu and Android’s document picker. Only explicit document read grants are requested; no microphone or broad storage permission is used. The same Media3 player drives radio and local audio, real PCM spectrum and 50–150% pitch-preserving speed. Existing immersive triangles/swipes navigate the active local queue. Explicit radio selection returns to radio; radio favorites and presets keep their radio identities. A cold launch restores selection without autoplay. Playback pauses on Activity stop.
