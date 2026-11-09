# Glasmio source integration

Vendored from [NEORUAA/Glasmio](https://github.com/NEORUAA/Glasmio) at commit
`b9502f08809d09f74814112dae44fee6318b20b7` (2026-11-09).
The source archive was supplied at `[local]/wk/glasmio-research/src`.
Before copying, all 163 selected source/legal Git blobs were hashed with Git's
`blob <length>\0` SHA-1 encoding and matched against the fixed-commit API tree.
`UPSTREAM.json` records the revision and exact exclusions.

## Included modules

`glasmio-core`, `glasmio-glass`, `glasmio-ui`, `glasmio-icons`, and
`glasmio-preference` are ordinary subprojects of the existing WanzaiHyperOS build.
No sample, scaffold/navigation shell, upstream root build, publication/signing
plugins, optional Apple fonts, or prebuilt Maven AARs are imported.
Existing application features, brand resources, and navigation remain host-owned.

## Local adaptations

- The root project's AGP 9.4.1 and Kotlin Compose plugin 2.4.20 are reused.
  All modules compile for SDK 37, min SDK 33, Java/Kotlin bytecode 21.
  Dependency resolution uses the existing root repositories/mirrors.
- Native glass uses the already-installed NDK `28.2.13676358`, instead of upstream
  `27.1.12297006`, and the already-installed CMake `3.22.1`. The native implementation
  remains C++17; its upstream `-Wl,-z,max-page-size=16384` linker option is retained.
  All four ABIs (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) compiled successfully;
  `llvm-readelf -lW` confirms every ELF LOAD segment is aligned to at least 16384
  bytes. The debug AAR contains all four JNI libraries and the legal notices.
  See `BUILD_EVIDENCE.json` for module test and native alignment evidence.
- All ten Apple-owned library images are excluded: four listening-mode PNGs in
  icons and six animated Tapback WEBPs in UI. The six semantic reaction values
  remain available, rendered through the host resolver or Apache-2.0 Material
  vectors. Adaptive audio defaults to a Material sparkles vector; callers can
  still supply their own thumb content. Apple animation playback is not retained.
  Tests requiring the removed native animations now verify licensed vector fallback.
- The host-facing `GlasmioTabItem` accepts an optional host-owned `ImageVector`
  and a notification badge count, keeping existing symbol callers compatible.
  Selected glyph filters exclude the red/white badge. The selected badge is a
  separate status overlay above the color/lens portal, preserving its colors.
  This extends the imported UI control only; the application's navigation shell
  and notification business state remain host-owned.
- `BackdropHost` disables layer recording and position tracking when
  `LocalGlassAppearance.current.usesBackdrop` is false. The sampled and overlay
  content remain siblings; this prevents opaque-mode controls from recording a
  source that their renderer does not consume.
- `GlassAppearance.materialAlphaMultiplier` defaults to `1f`, preserving upstream
  material output. The host's live opacity preference reaches material recipes,
  retained/lens sources, and morph materials; explicit caller tints opt out of
  this second multiplier. Recipe identity includes the strength, with both light
  and dark endpoints updated. Strength above one is retained; non-default shader
  output clamps final premultiplied RGB to alpha. Geometry, optics, animation
  physics, content alpha, and native C++/JNI code are not replaced by this adapter.
- Original `LICENSE`, `licenses/Apache-2.0.txt`, source notices, and
  `THIRD_PARTY_NOTICES.md` are retained. The upstream notice is historical and
  describes excluded assets; it does not assert that those assets are present in
  this integration. Each module packages those notices and a local integration
  notice under its own `META-INF/glasmio/<module>/` namespace to avoid collisions.

## Validation

All five modules compile with the root Kotlin 2.4.20 toolchain. Unit suites
currently total 116 tests, with zero failures/errors and three skips: the upstream
optional host-JNI comparisons are skipped on Windows. Android JNI compilation is
verified separately for all four ABIs; it is not a device execution test. The
licensed reaction-vector test exercises all six semantic reaction values. Icons
and preference have no unit-test sources in this upstream revision.

No device/ADB tests or on-device visual claims are made by this integration check.
See `BUILD_EVIDENCE.json` for the measured results and `INTEGRATION_NOTICE.md` for
the distribution-specific clarification.
