# Third-party dependency and license inventory

Initial Phase 1 inventory for upstream commit `b32a740dfecb98a0c551be24e6e84e97215342f6`. WaEnhancer itself is distributed under GNU GPL version 3 (`LICENSE`). This inventory is not yet the final binary notice bundle.

## Bundled native sources

| Component | Commit | License evidence |
|---|---|---|
| libopusenc | `3c65f440baff6220872ec11b0cbde37ef7a48f78` | Xiph three-clause BSD-style terms in `app/src/main/cpp/libopusenc/COPYING` |
| libogg | `06a5e0262cdc28aa4ae6797627a783b5010440f0` | Xiph three-clause BSD-style terms in `app/src/main/cpp/ogg/COPYING` |
| Opus | `22244de5a79bd1d6d623c32e72bf1954b56235be` | Xiph three-clause BSD-style terms plus patent notices in `app/src/main/cpp/opus/COPYING` and `LICENSE_PLEASE_READ.txt` |

Binary redistribution must reproduce the applicable Xiph copyright notices, conditions, and disclaimers.

## Local DexKit artifacts

| File | SHA-256 | Status |
|---|---|---|
| `app/libs/dexkit-android.aar` | `c945d5c5feabc99cd75f19c1c416d0e1ea3e3b1d62a659349fdf258ee5dce984` | Exact upstream release/commit and license unresolved |
| `app/libs/dexkit-android.source.jar` | `e4ddd0d2d610cd68b02ceb3bb748f963cf518be8d1c8189346daaa053ec4a317` | Exact upstream release/commit and license unresolved |

## Direct dependency inventory started

The direct graph includes AndroidX Activity, DocumentFile, ConstraintLayout, Fragment, Navigation, Preference and Room; Material Components; RikkaX; FlatBuffers; libsu; OkHttp; Bouncy Castle; Markwon; RemotePreferences; ColorPicker; FilePicker; BatteryPermissionHelper; jStyleParser; arscblamer; legacy Xposed API; and JUnit.

Known license families from published metadata/source include Apache-2.0 for AndroidX, Material, FlatBuffers, OkHttp, Markwon, ColorPicker, FilePicker, arscblamer and the legacy Xposed API; MIT for RikkaX, RemotePreferences and BatteryPermissionHelper; LGPL-3.0 for jStyleParser; and the Bouncy Castle License for Bouncy Castle.

Open items before release:

- Resolve and retain exact license evidence for libsu and any dependency whose POM lacks license metadata.
- Resolve DexKit provenance and license from the exact hashes above.
- Expand the inventory to all resolved transitive runtime artifacts after repository access is available.
- Generate a distribution-ready notice bundle with required complete license texts and copyright statements.

## Phase 4 reference-only research

`Kyant0/AndroidLiquidGlass` (artifact `io.github.kyant0:backdrop:2.0.1`) was inspected at commit
`65ab177e90e5c1d8c62e70cf7755841982da65f6`. The pinned tree contains the Apache License 2.0
and a `Copyright 2025 Kyant` notice. It is not added as a dependency and no source or shader from
that repository is included in WaEnhancer. Phase 4 uses only general architectural concepts, with
an independently authored native Android View/AGSL implementation. See
`docs/PHASE_4_LIQUID_GLASS_FEASIBILITY.md` for exact provenance.
