# Phase 2 runtime foundation

This document describes the runtime that is implemented on the
`w-injector/phase-2-runtime-foundation` branch. It does not describe the future iOS/Liquid Glass
UI and it does not claim real-device compatibility.

## Supported host and scope

- The only active hook target is `com.whatsapp`.
- `com.whatsapp.w4b` is no longer accepted by `WppXposed`.
- The LSPosed scope metadata contains only `com.whatsapp`.
- Android/System Framework is no longer in the scope metadata.
- `AntiUpdater`, the downgrade `Patch`, `ScopeHook`, and `HookBL` are not called by startup.
- Old Business constants/resources and inactive system-hook source remain temporarily so this
  migration does not delete potentially reusable history in the same change.

## Startup sequence

`WppXposed` performs only official-package validation and installs the small
`RuntimeBootstrap` application callback. The callback runs these recorded stages:

1. Validate `com.whatsapp` package/process input.
2. Read the typed recovery configuration.
3. create `HostSession` with package, process, WhatsApp version, module version, and Android
   version.
4. Stop before DexKit when disable-all or safe mode requires it.
5. Initialize module resources from the already-injected WhatsApp resources.
6. Initialize DexKit and resolver cache as explicit capabilities.
7. Initialize the still-monolithic legacy component group as `legacy.core-components`.
8. Register and install legacy features through `FeatureRegistry`.
9. Publish sanitized diagnostics to the companion app.

`HostSession.processContext` records a process suffix when present. This is diagnostic context,
not a verified WhatsApp account identifier. Multi-account correctness is not claimed.

## Feature isolation

`FeatureRegistry` owns typed `FeatureSpec` and `FeatureRecord` values. Each record has:

- a stable ID;
- a category;
- an enabled decision;
- required capability IDs;
- `DISABLED`, `UNSUPPORTED`, `RESOLVING`, `READY`, or `FAILED` state;
- a sanitized failure summary;
- tracked hook handles when the installer can provide them.

Every old `Feature` class is installed through an individual legacy adapter entry. An exception
marks only that entry `FAILED`; installation continues for later entries. A missing required
capability produces `UNSUPPORTED` and leaves stock WhatsApp behavior in place.

Legacy feature classes still own most of their detailed preference checks and do not expose their
hook handles. Diagnostics therefore call these entries `readyLegacyFeatures`, not active features.
`READY` means the legacy installer returned without throwing; it does not claim that every hook
inside old code was semantically verified.

## Capability lifecycle and resolver contract

`CapabilityRegistry.resolve` records `RESOLVING`, validates the returned value, caches a stable
successful value, and records an explicit sanitized `FAILED` result on error. The current
capabilities are:

- `runtime.config`
- `runtime.host-session`
- `runtime.module-resources`
- `resolver.dexkit`
- `resolver.cache`
- `legacy.core-components`

The 3,000+ line `Unobfuscator` was not rewritten. Newly touched startup paths no longer assume
that DexKit, cache initialization, or the legacy component group succeeded. Features requiring a
failed capability become unsupported instead of being installed blindly.

## Recovery controls

The companion app's existing General settings page now exposes:

- **Disable all hooks**: fail-stop before resolver or feature initialization.
- **Startup safe mode**: initialize only configuration/session/diagnostics and mark optional
  features disabled; DexKit and the resolver are skipped.
- **Disable visual modifications**: skips legacy `features.customization` installers while
  allowing other categories to load.
- **Export runtime diagnostics**: shares the most recent sanitized JSON received from WhatsApp.

The recovery preferences are read before risky optional hooks. They are controlled from the
companion app, so WhatsApp does not need to open successfully. After changing a recovery switch,
force-stop and restart WhatsApp (or reboot).

## Configuration and IPC

`RuntimeConfigReader` reads only the recovery/logging keys into `RuntimeConfigSnapshot`. It first
uses `XSharedPreferences` and then the existing read-only `RemotePreferenceProvider`. If both paths
fail with an exception, startup fails closed: all hooks, visual changes, and optional features are
disabled.

The provider validates the Binder calling UID against the module UID or official WhatsApp UID and
rejects writes. Legacy features still receive a read-only `SharedPreferences` implementation and
read their historical keys directly; this is an explicit transitional dependency.

The generic bridge service/provider are disabled and non-exported. `WppCore.initialize` no longer
connects to them, and bridge lookup returns `null` instead of making startup fail. Optional media
features use their existing app-private fallback or fail that operation without taking down the
runtime. The arbitrary `openFile/createDir/listFiles/exists` Binder API is therefore no longer an
externally reachable privileged IPC API.

Restart/check/manual-restart broadcasts now contain a random companion-generated token, use an
explicit target package, and are ignored on authentication failure. The runtime-state response is
also explicit and authenticated.

The unused manifest `WAFReceiver` and the legacy bridge wake-up `ForceStartActivity` are disabled
and non-exported. The companion-only crash report activity is non-exported. The two remaining
exported providers are intentionally cross-package and enforce module/official-WhatsApp UID checks.

The exported diagnostics provider accepts only official WhatsApp or the module UID, limits input
to 64 KiB, accepts one operation, and persists one JSON document in module-private storage.

## Diagnostics privacy

Runtime diagnostics record module/host/Android versions, package/process, recovery state,
configuration transport, startup stages, feature states, and capability failures. Exception text
is length-limited and scrubbed for WhatsApp-style JIDs and long phone-number patterns.

The runtime intentionally does not collect message bodies, chat contents, contacts, JIDs, or phone
numbers. Users should still inspect an exported file before posting it publicly.

## Remaining legacy dependencies and risks

- `WppCore.initialize` and the component wrappers remain a shared legacy capability. Failure is
  contained, but can make all dependent legacy features unsupported.
- Individual old feature classes still perform their own resolver calls and preference checks.
- Most old hooks do not return `Unhook` handles, so immediate in-process rollback is unavailable.
- The legacy generic bridge source/AIDL remains compiled but its Android components are disabled,
  it is not initialized, and it is not externally reachable.
- Optional features that depended on arbitrary shared-storage paths may fall back to app-private
  storage or report an operation-level failure.
- Diagnostics persistence depends on the companion provider being reachable. Failure is recorded
  in memory and never aborts WhatsApp startup.
- Secondary process and multi-account behavior require real-device validation.
- Runtime compatibility with a particular WhatsApp release is not claimed until device testing.

## Crash recovery

If WhatsApp crashes on startup:

1. Open the WaEnhancer companion app.
2. Open **General > General > Runtime recovery**.
3. Enable **Disable all hooks**.
4. Run `adb shell am force-stop com.whatsapp` and open WhatsApp again.
5. If WhatsApp now starts, leave disable-all enabled, export diagnostics, and collect LSPosed and
   logcat logs before re-enabling features.
6. For narrower isolation, turn disable-all off, enable startup safe mode, force-stop, and retest.

If the companion app cannot be used, disable WaEnhancer in LSPosed Manager and reboot. This is the
outer recovery path and does not depend on injected code.
