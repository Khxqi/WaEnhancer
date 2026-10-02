# Phase 4 Liquid Glass feasibility

This document records only the Phase 4 implementation. It is a rendering feasibility prototype,
not a WhatsApp redesign and not a declaration that the renderer is ready for Phase 5. Phase 3
privacy behavior is unchanged. The companion Glass Lab passed its initial OnePlus 15 test with
`RUNTIME_SHADER_SAMPLED`, including visible sampled refraction. The first WhatsApp compositor-blur
prototype failed its visual gate; the replacement described below still requires device validation.

## Baseline and scope

- Base content: final Phase 3 tree `cfeca48b1c46853801bd19a27f49bd48b86170ae`.
- Active injected host remains official `com.whatsapp` only.
- Android/System Framework and `com.whatsapp.w4b` remain outside active scope.
- The prototype is off by default and does not replace navigation, toolbars, conversations, or
  any production surface.
- Startup safe mode, Disable all hooks, and Disable visual modifications all prevent prototype
  installation.
- No privacy preference, resolver, hook target, or behavior was changed.

## AndroidLiquidGlass provenance audit

Reference repository: `https://github.com/Kyant0/AndroidLiquidGlass`

| Item | Pinned evidence |
|---|---|
| Commit | `65ab177e90e5c1d8c62e70cf7755841982da65f6` (`Update dependencies`, 2026-08-26) |
| License | Apache License 2.0, `Copyright 2025 Kyant`, from the pinned `LICENSE` |
| Published artifact | `io.github.kyant0:backdrop:2.0.1` |
| Build | AGP 9.3.2, Kotlin 2.4.10, Compose Multiplatform 1.12.0, Kyant Shapes 1.2.1 |
| Android baseline | minSdk 21, compileSdk 37, JVM target 11 |

Files inspected in the pinned tree include `DrawBackdropModifier.kt`,
`backdrops/LayerBackdrop.kt`, `backdrops/LayerBackdropModifier.kt`, `effects/Blur.kt`,
`effects/Lens.kt`, `RuntimeShaderCache.kt`, highlight/shadow implementations, the catalog
components, `backdrop/build.gradle.kts`, and `gradle/libs.versions.toml`.

The reference renderer works because it owns the Compose scene containing the backdrop. It records
that content in a Compose `GraphicsLayer`, replays it at the glass surface's coordinates, clips the
recorded layer to the requested shape, and applies an effect chain. Blur is a RenderEffect-backed
Compose `BlurEffect`. The lens path is gated by RuntimeShader support and applies refraction to
rounded-rectangular shapes. Highlight and shadow stages are separate from backdrop effects. The
catalog builds buttons, toggles, sliders, and bottom tabs by composing these primitives and using
Compose animation state.

That ownership model is the important constraint: inserting a ComposeView above an existing native
WhatsApp hierarchy would not give the Compose graphics layer ownership of the pixels WhatsApp has
already rendered. It would add Compose/KMP/shapes classes and classloader risk without solving
backdrop correctness.

### Reuse decision

Apache-2.0 is compatible with reuse in a GPLv3 project if its conditions and notices are retained.
Nevertheless, Phase 4 does **not** copy, translate, or bundle AndroidLiquidGlass source, shader text,
or binaries. It is not a dependency. Reused concepts are limited to general rendering architecture:
own the sampled backdrop, separate blur/lens/tint/highlight stages, cache GPU objects, and make shape
and effect parameters explicit. `GlassRenderer.kt` contains independently authored AGSL. The
reference remains credited in `THIRD_PARTY_NOTICES.md` as research provenance only.

## Architecture evaluation

| Candidate | Backdrop fidelity | Injection/runtime risk | Size/dependencies | Decision |
|---|---:|---:|---:|---|
| Inject Compose + AndroidLiquidGlass | High only when Compose owns the backdrop; it would not own WhatsApp's native scene | High: foreign Compose runtime and classloader surface | High | Rejected for WhatsApp injection |
| Native View + sampled RuntimeShader/AGSL | High in a controlled scene with a caller-owned `Shader` | Low in companion app; API 33+ | No new dependency | Selected for Glass Lab |
| RenderEffect on an overlay View | Filters the overlay's RenderNode, not arbitrary pixels behind it | Low | No new dependency | Detected, but not misrepresented as backdrop blur |
| App-attached popup + cross-window blur | OEM compositor decides the blurred region | Medium; isolated but behavior is OEM-dependent | No new dependency or overlay permission | **Rejected:** OxygenOS 16 blurred the entire WhatsApp content behind the popup window |
| Same-window localized RenderNode replay + AGSL | Replays current host content into a retained GPU display list clipped to the pill plus sampling margin | Medium; one additional host view-tree recording pass per frame | No new dependency | Selected second WhatsApp feasibility path |
| Layered Canvas surface | No real blur/refraction; stable tint/highlight/depth cue only | Low | No new dependency | Explicit fallback |
| PixelCopy/screenshot + CPU blur | Can sample pixels but creates latency, allocation, privacy, and recursion problems | High | High frame cost | Rejected |

## Glass Engine API

The native engine is under `com.wmods.wppenhacer.ui.glass`:

- `GlassStyle`: bounded blur, tint, opacity, refraction, edge, highlight, corner radius, depth,
  saturation, and animation progress.
- `GlassShape`: rounded rectangle, capsule, and circle masks.
- `GlassSurface`: immutable shape/style value that delegates rendering.
- `GlassRenderer`: backend-neutral draw contract.
- `RuntimeShaderGlassRenderer`: API 33+ sampled AGSL backend. It receives an existing `Shader`,
  performs a bounded nine-tap GPU sample, edge-weighted refraction, saturation/tint, rim light, and
  directional highlight. It never captures the screen.
- `LayeredGlassRenderer`: hardware Canvas tint/gradient/edge fallback. It is deliberately reported
  as `LAYERED_GPU_FALLBACK`, not as true Liquid Glass.
- `LocalizedBackdropGlassView`: API 33+ same-window prototype. It records the activity content root
  into one retained `RenderNode`, translated and clipped to the glass bounds plus a fixed sampling
  margin. A RuntimeShader RenderEffect receives that node as its `backdrop` input. The overlay skips
  itself during recording, preventing recursive mirror capture.
- `GlassCapabilities`: detects RuntimeShader (API 33), RenderEffect (API 31), localized same-window
  support, current cross-window blur availability for diagnostics, and low-RAM/high-end context.
- `GlassBackend`: `RUNTIME_SHADER_SAMPLED` for the lab,
  `LOCALIZED_SAME_WINDOW_SAMPLED` for the replacement WhatsApp prototype,
  `CROSS_WINDOW_BLUR_REJECTED` as a recorded rejected backend, or
  `LAYERED_GPU_FALLBACK` for the explicit non-glass lab comparison.

GPU objects, paths, paints, matrices, and the lab backdrop shader are retained. No bitmap or shader
is allocated from the per-frame draw callback. The lab allocates one detailed bitmap when its size
or light/dark mode changes, then reuses a tiled `BitmapShader`. There is no PixelCopy, screenshot,
CPU Gaussian blur, or continuous bitmap capture path.

## Companion Glass Lab

`GlassLabActivity` is non-exported and opened from General > Liquid Glass development > Open Glass
Lab. `GlassLabView` owns both the detailed moving backdrop and the glass samples, which makes AGSL
sampling technically correct. It renders a pill, button, floating card, bottom-tab-shaped surface,
and circular control.

Development controls cover blur, refraction, tint, edge, highlight, radius, depth, saturation, and
motion speed. Buttons switch light/dark content, pause/resume motion, and compare the sampled GPU
backend with the stable layered fallback. The moving detailed grid/circle backdrop provides slow and
fast scrolling-style content without capturing another application.

Directly embedding the reference Compose catalog for a side-by-side comparison was rejected because
it would add the exact runtime/dependency risk being evaluated. The lab instead compares our sampled
native backend against its explicitly named fallback. Visual equivalence to AndroidLiquidGlass is
not claimed; the user must judge depth, refraction, highlight, and motion on the target device.

## First WhatsApp prototype: rejected on device

The first implementation attached a non-touchable `PopupWindow` and requested
`FLAG_BLUR_BEHIND`. On the OnePlus 15 / OxygenOS 16, the pill was positioned correctly but the OEM
compositor blurred the entire WhatsApp content behind the popup window. This violates the primary
localized-rendering requirement. That path has been removed from active code and is retained only
as `CROSS_WINDOW_BLUR_REJECTED` diagnostic/provenance. It is not a fallback and is not suitable for
Phase 5.

## Second WhatsApp prototype: localized same-window sampling

Feature ID: `visual.experimental.liquid-glass-prototype`.

`GlassPrototypeController` still uses the Phase 2/3 `FeatureRegistry` and public
`Application.ActivityLifecycleCallbacks`, without resolving any obfuscated WhatsApp class. On
activity resume it inserts one non-clickable, non-focusable `LocalizedBackdropGlassView` into the
standard `android.R.id.content` `FrameLayout`. Pause, destroy, rollback, or process exit removes the
view and its pre-draw listener. No popup, extra window, overlay permission, window blur flag, or OEM
blur API is used.

Immediately before a normal frame is drawn, the view records the existing content root into a
retained hardware `RenderNode`. The recording canvas is translated so only the pill's local region
plus 36 dp blur/refraction padding lands inside the node. The recording is clipped to that node.
While recording, the glass view's own draw path is disabled; this prevents the new sample from
containing the preceding glass result. The retained node is then filtered using
`RenderEffect.createRuntimeShaderEffect`, reusing the lab's independently authored sampled blur,
refraction, tint, rim, highlight, and depth stages, and is drawn only inside the capsule path.
Everything outside that capsule receives no effect.

This does not copy pixels into a bitmap and does not use PixelCopy, screenshots, CPU blur, or
per-frame bitmap allocation. It does, however, traverse the host view hierarchy once more to record
each dynamic sample. Canvas clipping bounds GPU recording/output, but it may not eliminate all CPU
work performed by `View.draw`. That overhead is the central performance risk and must be measured on
the OnePlus 15. If it is unacceptable or view-tree replay is unstable, public Android APIs have not
met this prototype's production gate; the implementation must not be relabeled as successful or
replaced with simple tint.

The active feature requires the named `visual.glass.localized-same-window` capability (Android 13+
RuntimeShader/RenderNode effect path). Unsupported or non-hardware activities fail locally and keep
stock WhatsApp. There is intentionally no layered WhatsApp fallback because a tint-only surface is
not the requested Liquid Glass behavior.

The preference `runtime_enable_liquid_glass_prototype` defaults to false. Installation additionally
requires configuration transport to succeed and all three recovery gates to permit it:

| Runtime state | Prototype result |
|---|---|
| Startup safe mode | Not installed |
| Disable all hooks | Not installed |
| Disable visual modifications | Not installed |
| Configuration failed closed | Not installed |
| Experimental toggle off | Not installed |
| Renderer/view-tree failure | Failure isolated; surface removed, stock UI remains, privacy registry continues |

## Diagnostics and performance instrumentation

Runtime diagnostics schema 7 includes `glass` with the experimental flag, RuntimeShader/RenderEffect/
localized/cross-window/high-end capability flags, active backend, rejected backend list, attached
surface count, hardware acceleration state, captured frame count, last/worst recording duration,
approximate RenderNode memory, and a sanitized failure. It does not include WhatsApp content or
identifiers. `CROSS_WINDOW_BLUR_REJECTED` can never be reported as the active backend.

`GlassFrameMonitor` uses Android `Window.OnFrameMetricsAvailableListener` in the companion Glass Lab.
It reports observed frames, frames over 1.5 times the display refresh budget, worst total frame time,
and target frame time. This measures the entire lab window, not GPU time for the shader alone.

For target-device measurements, capture each scenario separately:

```bash
adb shell dumpsys gfxinfo com.whatsapp reset
# Exercise stock WhatsApp for 30-60 seconds, then:
adb shell dumpsys gfxinfo com.whatsapp framestats > whatsapp-stock-framestats.txt
adb shell dumpsys meminfo com.whatsapp > whatsapp-stock-meminfo.txt

adb shell dumpsys gfxinfo com.whatsapp reset
# Enable the prototype, restart, scroll and transition for the same duration, then:
adb shell dumpsys gfxinfo com.whatsapp framestats > whatsapp-glass-framestats.txt
adb shell dumpsys meminfo com.whatsapp > whatsapp-glass-meminfo.txt
```

Repeat the second capture while the backdrop scrolls quickly and while activities transition. A
Perfetto trace should only be requested if these frame statistics or visible artifacts need deeper
analysis. No target-device frame or memory values are recorded yet; inventing numbers would be
misleading.

## Target-device acceptance matrix

Target: OnePlus 15, Android 16, OxygenOS 16, arm64, KernelSU Next, LSPosed, official WhatsApp.

1. Install the Phase 4 debug APK, enable only `com.whatsapp` in LSPosed scope, force-stop both apps,
   and leave the prototype toggle off.
2. Confirm cold start, three restart cycles, chats list, conversation open/close, text send/receive,
   media, Settings, keyboard open/close, background/foreground, and activity transitions remain stock.
3. Open the companion Glass Lab. Test light and dark modes, detailed content, motion at 0/slow/fast,
   animation pause/resume, all five surface shapes, parameter extremes, and GPU/fallback switching.
4. Check the lab for black frames, stale samples, clipping errors, blur/refraction lag, edge seams,
   flicker, ghost views, memory growth, and excessive slow-frame count.
5. Enable the experimental prototype, force-stop WhatsApp, and cold start it. Confirm exactly one
   `GLASS PROTOTYPE` pill appears. Before scrolling, verify every pixel outside the capsule remains
   as sharp as the toggle-off baseline; any whole-window blur is an immediate failure.
6. Slowly and then rapidly scroll the chats list underneath the pill. Confirm only content inside the
   pill is sampled, the sample tracks movement, refraction moves with the content, and no stale,
   black, mirrored, recursively repeated, or wrongly clipped frame appears.
7. Tap and swipe immediately beside and directly through the pill, then open/close the keyboard.
   Confirm it intercepts no touch, does not shift resize behavior, and leaves no ghost surface.
8. Repeat background/foreground, conversation and Settings transitions, applicable rotation,
   system light/dark changes, and three force-stop/cold-start cycles. Watch for z-order errors,
   duplicate surfaces, crashes, memory growth, and jank.
9. Export runtime diagnostics. Require `glass.backend=LOCALIZED_SAME_WINDOW_SAMPLED`, one attached
   surface in a resumed activity, increasing `capturedFrames`, no failure, and
   `CROSS_WINDOW_BLUR_REJECTED` only under `rejectedBackends`. `LAYERED_GPU_FALLBACK` is not an
   acceptable WhatsApp result.
10. Capture matched stock/prototype `gfxinfo` and `meminfo` files using the commands above. Use equal
    30-60 second chat-list scroll/transition sequences and compare slow frames, worst frames, and
    total PSS; do not compare unmatched workloads.
11. Turn on Disable visual modifications, restart WhatsApp, and confirm the pill is absent while
   privacy features remain available. Repeat for Startup safe mode and Disable all hooks.
12. Clear all recovery switches, keep or disable the prototype as desired, restart, and confirm the
    expected state is restored.

## Known limitations and risks

- OxygenOS 16 demonstrated that popup `FLAG_BLUR_BEHIND` affects the whole host content, so that path
  is rejected and no longer installed.
- The localized path is public-API and GPU-backed, but it records the host View hierarchy once per
  dynamic frame. The GPU output is spatially bounded; CPU traversal cost is not guaranteed to be.
- `SurfaceView`, `TextureView`, protected surfaces, or compositor-owned content may not replay like
  ordinary Views and can appear blank/stale inside the pill. This must be tested around media.
- Calling `View.draw` during pre-draw is a feasibility technique, not a platform-provided backdrop
  capture API. Re-entrancy and vendor rendering behavior remain device-test risks.
- The nine-tap sampled shader is bounded and allocation-free during draw, but its real cost at the
  OnePlus 15 refresh rate is unknown until measured.
- The lab's one backdrop bitmap is intentionally a controlled test asset. It is rebuilt on size or
  theme change and is not a production WhatsApp capture mechanism.
- A successful installer means the lifecycle controller was registered. The active backend is not
  reported until the first hardware RenderNode recording succeeds, and `runtimeVerified` remains
  false until real-device testing supplies evidence.
- No full AndroidLiquidGlass side-by-side binary is bundled, so visual comparison to the reference is
  manual rather than pixel-identical.

Phase 5 must not start until CI succeeds, the extracted APK is verified, and the user accepts the
visual stability and measured performance on the target device.

## WhatsApp 2.26.38.73 compatibility pass

The localized renderer acceptance test was paused after the target updated to official WhatsApp
`2.26.38.73` (`versionCode 263807322`). LSPosed load-package callbacks and all three resource
injection groups still ran, but WaEnhancer did not become functionally active. This was not a glass
backend failure.

The exact stop was the supported-version gate in `RuntimeBootstrap`. The
`supported_versions_wpp` metadata ended at `2.26.37.xx`. After DexKit and resolver-cache setup,
the bootstrap returned before message-component initialization, legacy core initialization,
privacy capability resolution, or `FeatureRegistry.installAll()`. Because the glass feature had
only been registered at that point, the return also prevented its public-framework lifecycle
installer from running. The similarly named check in `HomeFragment` only controls companion UI;
it was not the runtime stop.

The observed first-launch string searches (`mystatus`, `online`, `groups`, `messagedeleted`,
`selectcalltype`, `lastseensun%s`, and `updates`) show that the persistent cache missed and rebuilt
after the host update. `UnobfuscatorCache` already compared WhatsApp `longVersionCode`, so cached
2.26.37 reflection targets were not reused for 2.26.38. Later launches omitted those searches
because the rebuilt string entries were cache hits, not because bootstrap never entered.

The compatibility change keeps the version safeguard instead of bypassing it:

- `2.26.38.xx` is now official-WhatsApp supported metadata; Business metadata is unchanged.
- `WhatsAppVersionPolicy` produces an explicit `FULL_RUNTIME`, `FULL_RUNTIME_BYPASS`, or
  `FRAMEWORK_ONLY` decision.
- An unknown/future version defaults to `FRAMEWORK_ONLY`. DexKit, legacy core, and obfuscated
  privacy features are withheld, while already registered Android-framework-only features may be
  evaluated under their own flags and capabilities. Stock behavior is therefore the default for
  unresolved host internals.
- The existing explicit user bypass remains distinct in diagnostics and retains its historical
  behavior; bypass is never reported as metadata acceptance.
- Resolver cache identity now includes both host version code and version name. Diagnostics report
  `HIT` or `INVALIDATED`, a bounded reason, and the previous/current host versions. A host update
  clears hook and string caches before any resolver target can be reused.
- Runtime logs now distinguish module load, resource injection, bootstrap entry, configuration,
  version acceptance/rejection, resolver initialization, cache state, capability resolution,
  feature-registry start, glass registration, completion, and fail-closed startup.

No WhatsApp class or signature was guessed or changed in this pass. The Phase 3 named privacy
capabilities still validate their resolved method/class shape and fail locally. Complex legacy
privacy preflights and the bundled message-component/legacy-core capabilities remain dependent on
the existing `Unobfuscator`; their actual 2.26.38 targets cannot be truthfully verified by JVM
tests or CI without the installed host. A failed privacy capability does not prevent the
`visual.experimental.liquid-glass-prototype` feature from installing when its independent Android
platform capability and recovery/configuration policy permit it.

Compatibility is not considered device-validated by this metadata and startup fix. Before
resuming the visual acceptance matrix, install the resulting APK and verify exported diagnostics
show:

1. `whatsAppVersionName=2.26.38.73` and `whatsAppVersionCode=263807322`.
2. `hostCompatibility.metadataAccepted=true` and `hostCompatibility.mode=FULL_RUNTIME`.
3. `resolverCache.disposition=INVALIDATED` on the first cold launch after installation (or `HIT`
   after that rebuild), followed by `HIT` on the next cold launch.
4. `RESOLVER_INITIALIZATION`, `CAPABILITY_RESOLUTION`, and `FEATURE_INSTALLATION` reached a recorded
   terminal state instead of disappearing after resource injection.
5. Failed resolver capabilities, if any, appear individually; WhatsApp remains open and unrelated
   features continue.
6. `visual.experimental.liquid-glass-prototype` is registered independently. With its toggle on
   and all recovery switches off, it can reach `READY` even if an unrelated privacy capability is
   `FAILED` or `UNSUPPORTED`.

Only after those checks pass should the OnePlus 15 localized RenderNode/AGSL visual and performance
acceptance procedure above resume.

## Post-startup glass observability pass

The `C2FED88A` compatibility build reached `READY` for both the localized platform capability and
`visual.experimental.liquid-glass-prototype`, with no failed features or capabilities. Its exported
diagnostics still showed no backend, surface, or captured frame. That file was a bootstrap snapshot:
`RuntimeBootstrap` published immediately after `FeatureRegistry.installAll()`, before a WhatsApp
activity necessarily resumed. Later `GlassRuntimeState` changes were held only in the WhatsApp
process and were not republished to the companion.

The renderer and attachment policy are unchanged. A bounded live publisher now coalesces meaningful
glass state changes with a 500 ms minimum interval. It republishes after activity/attachment state,
successful addView, structural attach failure, the first pre-draw/record/capture/render milestones,
render failure, and detach. It is not called on every frame. Existing 60-frame in-memory metrics do
not trigger repeated provider/file writes.

Schema 6 adds:

- lifecycle callback registration and latest sanitized activity/lifecycle event;
- attach attempts, successful attaches, and active surface count;
- actual `android.R.id.content` class, ViewGroup/FrameLayout classification, and dimensions;
- candidate surface dimensions;
- first pre-draw, first recording, and first successful RenderNode draw state;
- last render stage and sanitized attach/render failure;
- a `glass.runtimeVerified` value that becomes true only after at least one successful surface
  attach and one successful localized RenderNode draw.

At the same milestone, the existing feature record is marked `runtimeVerified=true`; installer
`READY` alone still leaves it false. If `android.R.id.content` is not a `FrameLayout`, diagnostics
record the real class and both structural booleans and the controller leaves stock WhatsApp intact.
No alternative root traversal, PopupWindow, cross-window blur, privacy hook, or renderer fallback
was introduced by this pass.

## Same-window display-list ownership correction

The OnePlus 15 result from `5FD61DF4` isolated a capture-only state: the surface attached, pre-draw
ran, and one localized backdrop RenderNode was recorded in about 0.336 ms, but the glass view never
completed a normal draw. There was no renderer exception. The implementation attached the surface
to `android.R.id.content` and also called `draw()` on that same hierarchy while recording the
backdrop. Consequently the sampled traversal contained the glass child itself. Its defensive
`recordingBackdrop` guard returned an empty child draw during that nested traversal, but did not
provide structural display-list isolation.

The corrected same-window ownership is:

- sampling root: `android.R.id.content`, containing only WhatsApp content;
- surface parent: the Activity window's `DecorView`, accepted only when it is a `FrameLayout`;
- required relationship: the surface parent is neither the sampling root nor a descendant of it;
- coordinate mapping: both sampling root and surface use window coordinates, and their difference
  translates the retained RenderNode to the exact region behind the pill plus blur padding.

The surface remains in the same Activity window and remains non-clickable, non-focusable, and
accessibility-hidden. No extra Window, overlay permission, PopupWindow, PixelCopy, bitmap capture,
CPU blur, or hidden SurfaceControl API is used. If the DecorView is unsuitable or the ownership
check fails, only the prototype fails and stock WhatsApp remains intact.

Schema 7 records the sampling-root and surface-parent classes and dimensions, confirms
`surfaceOutsideSamplingSubtree`, records both window origins and surface bounds, and adds bounded
draw counters: total onDraw entries, draws attempted during capture, normal onDraw attempts, and
successful RenderNode draws. Milestones are traced/published only on their first transition; there
is no per-frame diagnostic I/O. Runtime verification still requires a successful attachment and a
completed normal localized RenderNode frame.
