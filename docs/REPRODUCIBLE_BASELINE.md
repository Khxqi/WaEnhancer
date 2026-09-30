# Phase 1 reproducible baseline

Phase 1 freezes the build environment before any privacy-hook refactor, styling removal, or Liquid-Glass UI work.

## Repository identity

- Upstream: `https://github.com/Dev4Mod/WaEnhancer.git`
- Audited upstream commit: `b32a740dfecb98a0c551be24e6e84e97215342f6`
- Development branch: `w-injector/phase-1-reproducible-baseline`
- Variant: `whatsappDebug`
- Companion application ID: `com.wmods.wppenhacer`

## Toolchain

| Component | Version/configuration |
|---|---|
| Gradle wrapper | 9.6.1 |
| Android Gradle Plugin | 9.3.1 |
| Effective Android Kotlin compiler | 2.2.10 in the previously resolved baseline graph |
| Resolved app Kotlin stdlib | 2.2.21 in the previously resolved baseline graph |
| Kotlin catalog entry | 2.4.10; the `kotlinAndroid` alias is not applied |
| Gradle's embedded Kotlin | 2.3.21 |
| KSP | 2.3.10 |
| JDK | 17; Java/Kotlin bytecode target 17 |
| compileSdk | 37 |
| targetSdk | 34 |
| minSdk | 28 |
| Android platform package | `platforms;android-37.0`, revision 2 |
| Build Tools selected by AGP | 36.0.0 |
| NDK | 28.2.13676358 |
| CMake | 3.22.1 |
| ABIs | `arm64-v8a`, `armeabi-v7a` |

### Foojay/JDK correction

The audited tree installed JDK 17 in GitHub Actions, then `gradle/gradle-daemon-jvm.properties` forced a JetBrains JDK 21 download through Foojay. `settings.gradle.kts` also applied `org.gradle.toolchains.foojay-resolver-convention:1.0.0`. This introduced a second network-dependent toolchain bootstrap before Android configuration.

Phase 1 removes the Foojay settings plugin and the generated daemon-JVM criteria file. Gradle 9.6.1 now uses the existing JDK 17 directly, matching the repository's CI setup and `VERSION_17` compilation target. Verified output:

```text
Launcher JVM: 17.0.20
Daemon JVM: /usr/lib/jvm/java-17-openjdk-amd64 (no Daemon JVM specified, using current Java home)
```

No Gradle, AGP, SDK, hook, feature, scope, or source compatibility level was downgraded.

## Submodules/native inputs

| Path | Commit |
|---|---|
| `app/src/main/cpp/libopusenc` | `3c65f440baff6220872ec11b0cbde37ef7a48f78` |
| `app/src/main/cpp/ogg` | `06a5e0262cdc28aa4ae6797627a783b5010440f0` |
| `app/src/main/cpp/opus` | `22244de5a79bd1d6d623c32e72bf1954b56235be` |

Initialize them with bounded external execution in CI, or normally with:

```bash
git submodule sync --recursive
git submodule update --init --recursive
git submodule status --recursive
```

## DexKit

DexKit is a checked-in local dependency, not a Maven coordinate:

| File | SHA-256 |
|---|---|
| `app/libs/dexkit-android.aar` | `c945d5c5feabc99cd75f19c1c416d0e1ea3e3b1d62a659349fdf258ee5dce984` |
| `app/libs/dexkit-android.source.jar` | `e4ddd0d2d610cd68b02ceb3bb748f963cf518be8d1c8189346daaa053ec4a317` |

The exact DexKit release/commit and applicable license for these copied files remain unresolved.

## Xposed/LSPosed assumptions

- Legacy compile-only Xposed API: 82.
- Manifest minimum Xposed version: 93.
- Entrypoint: `com.wmods.wppenhacer.WppXposed` via `assets/xposed_init`.
- Upstream recommended scopes: `com.whatsapp`, `com.whatsapp.w4b`, and `android`.
- Phase 1 does not establish runtime compatibility with Android 16, OxygenOS 16, LSPosed, KernelSU Next, or a particular WhatsApp version.

## Reproducibility constraint

`com.github.DavidArsene:arscblamer:1.0` declares `com.google.guava:guava:+`. Phase 1 constrains Guava to `33.7.2-jre`, the version resolved during the baseline audit, so future builds cannot silently select a different Guava release.

## Build command and bounded execution

Normal command:

```bash
./gradlew --no-daemon clean assembleWhatsappDebug
```

For unattended runs, wrap it in an explicit limit:

```bash
timeout --signal=TERM --kill-after=5s 20m \
  ./gradlew --no-daemon clean assembleWhatsappDebug
```

## Current build verification status

The clean baseline build passed in GitHub Actions after the fork's hosted runner supplied access to the required Android and Maven repositories. The workflow used the exact command documented above and retained all audited features.

| Evidence | Value |
|---|---|
| Repository | `Khxqi/WaEnhancer` |
| Workflow | `Build WhatsApp` / `.github/workflows/build-whatsapp.yml` |
| Run | `36787823996` |
| Run URL | `https://github.com/Khxqi/WaEnhancer/actions/runs/36787823996` |
| Result | `success` |
| Built commit | `505b5d2066bbb127478b909f2a2969147d89d04f` |
| Source tree | `03c492702722203433165a4864fbc2366e27a609` |
| Runner | GitHub-hosted `ubuntu-24.04` |
| JDK | Temurin `17.0.20+1` |
| APK path | `app/build/outputs/apk/whatsapp/debug/WaEnhancer-1.6.0 (505B5D20).apk` |
| APK size | `37119563` bytes |
| APK SHA-256 | `0ba6f3ccfb4b59b4017ff9ce595e751b5a90167acea6d9a30a535b47aab8719d` |
| Artifact | `WaEnhancer-whatsappDebug-505b5d2066bbb127478b909f2a2969147d89d04f` (ID `11130692713`) |
| Artifact expiry | `2026-10-30T22:54:22Z` |

The first branch run (`36787498258`) established that the current hosted image does not expose `sdkmanager` on `PATH`. Commit `505b5d20` makes the runner-independent minimum correction by invoking `${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager` explicitly. SDK installation and the subsequent clean build then passed.

The local executor remains unable to reach Google Maven through its network path. GitHub Actions is therefore the authoritative clean Phase 1 build environment; no AGP or Gradle downgrade was made.

## Remaining non-reproducible/external inputs

- Initial access to Google Maven, Maven Central, Gradle Plugin Portal, Xposed API, JitPack, Android SDK repositories, and submodule hosts.
- Android SDK platform 37, Build Tools selected by AGP, NDK 28.2.13676358, and CMake 3.22.1 must be installed before a clean build.
- No Gradle dependency-verification metadata is present yet.
- Default debug signing uses a machine-specific debug keystore, so APK signatures/hashes differ across machines.
- DexKit provenance/license remains unresolved despite exact file hashes.

## Verified APK path

```text
app/build/outputs/apk/whatsapp/debug/WaEnhancer-1.6.0 (505B5D20).apk
```
