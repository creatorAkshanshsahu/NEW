# AK TV

A simple Android TV app for browsing and playing free/public-domain or Creative Commons video items discovered through the Internet Archive APIs.

## Important

This project deliberately does NOT scrape NetMirror, Dulo, AlmostOTT, or other third-party movie-streaming sites.

The catalog uses Internet Archive search and metadata endpoints. Internet Archive's documentation says its `mediatype:movies` items are videos and can have an online video player; its metadata API exposes the files belonging to an item.

The app applies a conservative rights filter:
- `rights` contains "public domain", OR
- `licenseurl` contains `creativecommons.org`

Rights information on user-uploaded material should still be checked before redistribution.

## GitHub Actions

Push this repository to GitHub. The workflow at:

`.github/workflows/build.yml`

builds the debug APK automatically.

After the workflow finishes:
GitHub → Actions → Build AK TV APK → Artifacts → AK-TV-debug

## Android target

- minSdk 28 (Android 9)
- Media3/ExoPlayer
- Android TV/D-pad friendly
- Native player; no WebView

## Current limitation

This is intentionally a legal free-content starter. It cannot guarantee that every commercial movie exists in the free catalog. A title such as a current commercial release may have no legally reusable free stream.

## Expanding the catalog

The app searches Internet Archive dynamically. It can therefore show more titles as the Archive's indexed catalog changes, without rebuilding the APK.

For a large production catalog, move the catalog search and rights verification to a backend instead of doing all metadata requests on the TV.


## v1.1 UI upgrade

The upgraded build adds:
- Netflix-style hero area
- Search bar
- Larger TV-friendly poster grid
- Focus scaling for D-pad navigation
- More useful default sorting by downloads
- Dynamic catalog loading without shipping a fixed 100-title list

Internet Archive's Advanced Search API supports JSON output and fielded/Boolean queries, and its Metadata API returns the files belonging to an item. See the official docs before expanding the rights filters.


## v1.2 Live TV

This build adds a dedicated **Live TV** section backed by the TG TV public channels API:

`https://livetgtv.lovable.app/api/public/v2/channels`

The app supports common channel fields such as name/title, logo, category and direct stream URL. If the API returns an `/embed/` player URL, AK TV opens it in a TV-friendly WebView fallback; direct HLS/MP4/DASH URLs use the native Media3 player.

Use only channels and streams you are authorized to access and redistribute.


## v1.3 – Polish, performance & CI fix

- **CI fix:** workflow no longer uses `android-actions/setup-android` (it tried to install the removed `tools` package). It uses the SDK already on the GitHub runner.
- **Compile fix:** unescaped quotes in the Internet Archive query (`ArchiveApi.kt`).
- **Live TV fixes:** network call was running on the main thread; added HLS/DASH modules so `.m3u8` channels play; cleartext (http) streams allowed; redirects followed.
- **UI:** slim top bar instead of the 210dp header that covered the channels; adaptive grids (phone → TV); category chips; instant Live TV search; focus ring + zoom; press feedback; error/empty/retry states; notch/cutout & immersive full-screen.
- **Smoothness:** DiffUtil adapters, resized/cached images, shared HTTP client, cached tab data, debounced search.
