# Phase 5A Home Liquid Glass

This document records the implemented Phase 5A development surface. It does not describe a full
WhatsApp redesign and it does not claim OnePlus 15 acceptance. Phase 5B, Phase 5C, conversations,
message bubbles, the composer, settings, profiles, dialogs, emoji, and fonts remain out of scope.

## Baseline and gates

- Base content is the device-accepted Phase 4 tree
  `68a855667b8cf99b6a847aa74b31a1b2b59e6262`.
- The active host remains official `com.whatsapp` only. Business and Android/System Framework are
  not active scopes.
- `runtime_enable_ios_home_redesign` is off by default.
- Startup safe mode, Disable all hooks, Disable visual modifications, and failed-closed
  configuration each prevent installation.
- Enabling Phase 5A suppresses the Phase 4 diagnostic pill. The automatic five-mode visual cycle is
  disabled in production surfaces; the probe implementation remains available as diagnostic code.
- Phase 3 privacy feature IDs, capabilities, installers, configuration meanings, and hooks are not
  changed.

## Design system

`LiquidGlassDesignSystem.kt` centralizes the initial light and dark tokens. It defines neutral
graphite tint, real sampled blur/refraction, saturation, rim/highlight strength, depth, navigation
geometry, insets, control sizes, title size, and the selected-pill transition duration. Individual
Home controls do not carry independent copies of these values.

The initial dark appearance intentionally differs from the exaggerated blue Phase 4 diagnostic
style: it uses a neutral charcoal tint at 30% opacity, a 26 dp blur request, 5.5 dp refraction,
restrained edge/highlight values, and a 76 dp full-capsule surface. These are development values for
device evaluation, not final visual sign-off.

## Floating navigation architecture

`IosHomeChromeController` discovers the existing WhatsApp navigation using the public resource name
`bottom_nav`, then validates only structural evidence:

- the activity package is `com.whatsapp` and its simple class name is `HomeActivity`;
- the candidate is visible, is a `ViewGroup`, is near the bottom, and occupies a plausible width
  and height;
- between three and six visible, enabled, clickable leaf destinations exist in increasing
  horizontal order.

No obfuscated navigation method is invoked. If validation fails, Phase 5A leaves stock navigation
in place and records a sanitized reason.

After validation, the controller moves the **real host navigation ViewGroup** into one
`GlassNavigationSurface` attached to the activity `DecorView`. The host controls remain the source
of truth for destination clicks, selected/activated state, accessibility, avatar/icon state, and
live badges. The surface uses one `LocalizedBackdropGlassView` and therefore one bounded host
hierarchy recording pass for the complete outer capsule. The darker selection pill is a retained
lightweight child layer; it does not perform a second backdrop capture. Badge views are excluded
from icon/text emphasis tinting.

The localized renderer continues to sample only the content-root region behind the capsule. It
uses retained RenderNode replay plus the device-accepted RuntimeShader effect and has no PopupWindow,
`FLAG_BLUR_BEHIND`, PixelCopy, screenshot, bitmap-capture, or CPU-blur path.

## Home chrome

The existing chat list and its data remain native. Phase 5A applies a reversible near-black/light
Home background, removes toolbar elevation, gives discovered toolbar actions and the existing
search surface restrained rounded layers, and adds a large localized Chats title only when the
Chats destination can be identified safely from host resources/state. It does not replace the chat
RecyclerView or aggressively restyle rows.

The largest visible RecyclerView-like Home content view receives reversible bottom padding equal to
the floating bar height plus margin, with `clipToPadding=false`. This prevents permanent coverage of
the last row while allowing content to move behind the glass. Gesture insets add to the configured
bottom margin. The bar becomes invisible while the IME is visible.

## Ownership and legacy conflict policy

When the Phase 5A flag is enabled, the following legacy visual owners are not installed because
they can mutate the same Home surfaces:

- `CustomToolbar`
- `CustomView`
- `CustomThemeV2`
- `FloatingBottomBar`
- `SeparateGroup`
- `HideTabs`

The policy is configuration-gated and reversible. It does not block privacy features or unrelated
legacy behavior. Disabling Phase 5A on the next host start returns ownership to the legacy loader.

## Rollback and lifecycle

The controller snapshots the navigation parent, index, layout parameters, navigation background
and elevation, destination tints/colors/backgrounds, content padding, toolbar/search backgrounds,
toolbar logo visibility, and Home background. A failure after mutation removes the custom surface
and restores every snapshot. A pause, activity destruction, feature stop, or renderer failure uses
the same rollback path. Posted discovery work is accepted only while that exact Home activity is
resumed.

The implementation must never deliberately leave both navigations visible, remove all navigation,
or place an unclickable replacement over the host. Only host destination children consume intended
navigation input; decorative glass and selection layers are non-clickable and excluded from
accessibility.

## Diagnostics and performance evidence

Runtime diagnostics schema 9 includes `phase5aHome` with:

- enablement/status, lifecycle and Home detection;
- discovery attempts and sanitized host navigation class;
- destination, badge, active-index, attachment, and single-surface counts;
- content-padding, toolbar, and search mutation state;
- hierarchy capture count, last/rolling/worst capture duration, and approximate RenderNode bytes;
- draw-path-only runtime verification and a sanitized failure summary.

In-memory metrics update on frames, but diagnostics publication is bounded to the first captured
frame and every 300th capture. No per-frame file/Binder publication is introduced. Phase 5A does
not include invented performance results; `dumpsys gfxinfo` and `dumpsys meminfo` measurements must
be collected on the OnePlus 15 with the redesign disabled and enabled under matched scrolling.

## JVM coverage

Pure tests cover recovery gating, structural navigation validation, destination-count bounds,
selected-state mapping, legacy conflict ownership, bottom-inset/IME policy, and prevention of a
second concurrent glass surface. They do not claim GPU or obfuscated-host verification.

## OnePlus 15 device test

Target: Android 16 / OxygenOS 16, KernelSU Next, LSPosed, official WhatsApp 2.26.38.73.

1. Install the Phase 5A debug APK, enable only `com.whatsapp` in LSPosed, and force-stop both apps.
2. With `iOS / Liquid Glass Home redesign` off, launch WhatsApp and confirm the stock Home,
   destinations, chats, badges, and scrolling are unchanged.
3. Enable the redesign in the companion, keep safe mode/Disable all/Disable visual off, force-stop
   WhatsApp, and open the Chats screen for at least five seconds.
4. Confirm exactly one floating capsule appears, no opaque strip or duplicate stock bar remains,
   and all pixels outside the capsule stay sharp.
5. Open every displayed destination. Confirm the real screen changes, selected pill follows within
   a short restrained transition, live badge values remain correct, and accessibility labels/clicks
   still belong to the host destinations.
6. Scroll and fast-scroll Chats. Confirm backdrop content updates dynamically, blur/refraction stay
   inside the capsule, the last row can scroll above the bar, and there is no recursion, stale/black
   frame, or full-screen blur.
7. Tap empty regions around the capsule and controls near it; confirm only destination controls
   consume navigation input.
8. Open and close the keyboard, background/foreground WhatsApp, rotate if enabled, and repeat three
   cold starts. Confirm no ghost surface and no bar above the IME or non-Home screens.
9. Exercise normal chats, send/receive, media, and the Phase 3 privacy settings selected for the
   regression pass. Phase 5A must not alter their behavior.
10. Export runtime diagnostics after active scrolling. Require `destinationCount` in the validated
    range, `glassSurfaceCount=1`, `floatingBottomBarAttached=true`, capture count increasing, no
    `homeFailure`, and `runtimeVerified=true` only after an actual draw.
11. Repeat with Disable visual modifications, Disable all hooks, and startup safe mode separately.
    Each must restore stock appearance. Re-enable and confirm normal recovery.

For matched frame evidence, reset and collect each mode separately:

```sh
adb shell dumpsys gfxinfo com.whatsapp reset
# perform the same 20-second Chats scroll sequence
adb shell dumpsys gfxinfo com.whatsapp framestats > gfxinfo-phase5a.txt
adb shell dumpsys meminfo com.whatsapp > meminfo-phase5a.txt
```

Raw Android and LSPosed logs can contain unrelated sensitive information. Review them before
sharing; diagnostics intentionally omit messages, chat text, contact names, phone numbers, and JIDs.

## Known risks and limits

- WhatsApp may change the `bottom_nav` resource or hierarchy; structural mismatch intentionally
  falls back to stock.
- Moving a host ViewGroup across parents is reversible but must be verified against host lifecycle,
  layout assumptions, accessibility, and every live destination on the target build.
- One bounded host hierarchy recording pass per rendered bar frame remains additional GPU/UI work.
  Phase 4 proved visual feasibility, not Phase 5A scrolling cost.
- Toolbar/search discovery is conservative; unsupported layouts remain stock rather than receiving
  guessed mutations.
- This build is a development acceptance build. Phase 5B/5C work is blocked until the user approves
  the floating navigation and Chats/Home chrome on the real device.
