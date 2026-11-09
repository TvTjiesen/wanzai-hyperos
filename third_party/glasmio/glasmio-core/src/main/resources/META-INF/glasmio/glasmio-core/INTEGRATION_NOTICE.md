# WanzaiHyperOS Glasmio integration notice

Glasmio code is Copyright 2026 NEORUAA, licensed under Apache-2.0.
The original license and upstream third-party notices accompany this source and
each imported module's runtime resources. Existing per-file notices remain intact.

This integration modifies build wiring, NDK selection, restricted-artwork
fallback rendering, host-owned vector/badge slots in the navigation control,
disabling backdrop recording when the effective appearance is opaque,
and a default-preserving material-strength bridge for the host's opacity preference.
It excludes every upstream Apple-owned library image
(the four listening-mode PNGs and six Tapback WEBPs), the sample wallpapers,
Apple fonts, and Apple binaries. References in the unmodified upstream
THIRD_PARTY_NOTICES.md describe the upstream distribution, not bundled assets here.

Reaction and adaptive-audio fallbacks use the Apache-2.0 AndroidX Material icons
already declared by glasmio-icons, or a host-supplied resolver. These replacements
are not Apple image renditions and do not reproduce the removed Tapback animations.

No rights to Apple fonts, symbols, images, or marks are granted by this notice.
The WanzaiHyperOS application retains its own branding and business functionality.
