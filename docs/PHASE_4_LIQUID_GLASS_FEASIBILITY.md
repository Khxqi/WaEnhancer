# Phase 4 Liquid Glass feasibility

This document records only the Phase 4 implementation. It is a rendering feasibility prototype,
not a WhatsApp redesign and not a declaration that the renderer is ready for Phase 5. Phase 3
privacy behavior is unchanged. Real-device visual quality and performance remain a user acceptance
gate on the OnePlus 15.

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
| App-attached popup + cross-window blur | Real system/GPU backdrop blur when the platform and OEM compositor enable it | Medium; isolated and removable | No new dependency or overlay permission | Selected experimental WhatsApp path |
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
- `GlassCapabilities`: detects RuntimeShader (API 33), RenderEffect (API 31), current cross-window
  blur availability, and low-RAM/high-end graphics context.
- `GlassBackend`: `RUNTIME_SHADER_SAMPLED`, `CROSS_WINDOW_BLUR`, or
  `LAYERED_GPU_FALLBACK`.

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

## Isolated WhatsApp prototype

Feature ID: `visual.experimental.liquid-glass-prototype`.

`GlassPrototypeController` registers an `Application.ActivityLifecycleCallbacks` instance from the
Phase 2/3 `FeatureRegistry`. On activity resume it creates one small, non-focusable, non-touchable
application-token `PopupWindow` near the top-right of the current activity. It uses public Android
framework lifecycle/window APIs and does not resolve or guess an obfuscated WhatsApp class. On pause,
destroy, feature rollback, or process exit the popup is dismissed.

On API 31+ the controller checks current `WindowManager.isCrossWindowBlurEnabled`, then requests
`FLAG_BLUR_BEHIND` and a bounded blur-behind radius for the popup window. If the compositor reports
blur unavailable or applying window parameters fails, the surface stays on the stable layered GPU
fallback. No `TYPE_APPLICATION_OVERLAY` window or overlay permission is used. The popup cannot accept
touch or focus and therefore must not intercept WhatsApp interaction.

The preference `runtime_enable_liquid_glass_prototype` defaults to false. Installation additionally
requires configuration transport to succeed and all three recovery gates to permit it:

| Runtime state | Prototype result |
|---|---|
| Startup safe mode | Not installed |
| Disable all hooks | Not installed |
| Disable visual modifications | Not installed |
| Configuration failed closed | Not installed |
| Experimental toggle off | Not installed |
| Renderer/window failure | Failure isolated; popup removed or fallback used; privacy registry continues |

## Diagnostics and performance instrumentation

Runtime diagnostics schema 3 adds `glass` with the experimental flag, RuntimeShader/RenderEffect/
cross-window/high-end capability flags, active backend, attached surface count, hardware acceleration
state, and a sanitized initialization failure. It does not include WhatsApp content or identifiers.

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
   `GLASS PROTOTYPE` pill appears, content behind it remains dynamic, and the pill accepts no touch.
6. Repeat chats scrolling, fast scrolling, keyboard open/close, background/foreground, activity
   transitions, applicable rotation, system light/dark mode, and three WhatsApp restarts. Watch for
   z-order errors, full-screen blur, stale blur, ghost popups, touch interception, crashes, and jank.
7. Export runtime diagnostics and record `glass.backend`, capability flags, failure, and attached
   surface count. A layered fallback is stable but is not visual proof of Liquid Glass.
8. Capture matched stock/prototype `gfxinfo` and `meminfo` files using the commands above.
9. Turn on Disable visual modifications, restart WhatsApp, and confirm the pill is absent while
   privacy features remain available. Repeat for Startup safe mode and Disable all hooks.
10. Clear all recovery switches, keep or disable the prototype as desired, restart, and confirm the
    expected state is restored.

## Known limitations and risks

- The WhatsApp popup can request real cross-window blur, but arbitrary backdrop refraction is not
  available through standard child-View APIs. The sampled AGSL path is therefore lab-only.
- Cross-window blur is compositor/OEM controlled and may be disabled dynamically by battery saver,
  media tunneling, or device policy. The layered surface remains visible, but diagnostics are updated
  on attachment rather than continuously listening for compositor changes.
- Popup window blur behavior and clipping must be verified on OxygenOS 16; Phase 4 does not claim it
  is visually accepted.
- The nine-tap sampled shader is bounded and allocation-free during draw, but its real cost at the
  OnePlus 15 refresh rate is unknown until measured.
- The lab's one backdrop bitmap is intentionally a controlled test asset. It is rebuilt on size or
  theme change and is not a production WhatsApp capture mechanism.
- A successful installer means the lifecycle controller was registered, not that visual behavior was
  verified. `runtimeVerified` remains false until real-device testing supplies evidence.
- No full AndroidLiquidGlass side-by-side binary is bundled, so visual comparison to the reference is
  manual rather than pixel-identical.

Phase 5 must not start until CI succeeds, the extracted APK is verified, and the user accepts the
visual stability and measured performance on the target device.
