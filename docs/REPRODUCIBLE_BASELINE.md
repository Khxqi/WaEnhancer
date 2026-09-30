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

The recovery executor had no prior Gradle or Android caches. The pinned Gradle 9.6.1 distribution was fetched once with a 170-second transfer limit. Gradle then reached project configuration on JDK 17, proving that Foojay/JDK 21 provisioning was no longer involved.

The final baseline build was attempted offline with a 180-second hard limit and failed after four seconds because AGP `9.3.1` was not cached. A bounded transport check to Google Maven timed out during proxy CONNECT. Exact unresolved artifact:

```text
com.android.application:com.android.application.gradle.plugin:9.3.1
```

This is an external repository-access blocker in the current executor. It is not fixed by changing AGP or removing application features. No APK was produced in this recovery run.

## Remaining non-reproducible/external inputs

- Initial access to Google Maven, Maven Central, Gradle Plugin Portal, Xposed API, JitPack, Android SDK repositories, and submodule hosts.
- Android SDK platform 37, Build Tools selected by AGP, NDK 28.2.13676358, and CMake 3.22.1 must be installed before a clean build.
- No Gradle dependency-verification metadata is present yet.
- Default debug signing uses a machine-specific debug keystore, so APK signatures/hashes differ across machines.
- DexKit provenance/license remains unresolved despite exact file hashes.

## Expected APK path after a successful build

```text
app/build/outputs/apk/whatsapp/debug/WaEnhancer-1.6.0 (<GIT_SHA8>).apk
```
