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

The refined appearance intentionally differs from the exaggerated blue Phase 4 diagnostic style.
The current device-correction pass also rejects the first Phase 5A result: that 64 dp/30 dp version
looked like a heavy gray acrylic slab and allowed repeated chat text plus the stock green FAB to
contaminate the backdrop. The replacement uses a 62 dp capsule, native 24 dp GPU blur,
2.0 dp boundary-only refraction, 0.13 dark/0.14 light neutral tint opacity, and 1.05/1.04
saturation. Native blur supplies primary softness before AGSL adds tint, rim, depth, and edge
lensing. The center is spatially stable; refraction rises only in the outer boundary band.
An explicit 0.85 dp gradient rim supplies a brighter upper highlight and restrained lower depth.
Green is reserved for mirrored live badges. These remain development values for device evaluation,
not final visual sign-off.

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

After validation, the four functional host destinations are classified semantically and mapped into
a deterministic custom visual order:

1. Updates / Status
2. Calls
3. Communities
4. Chats
5. Profile / Avatar

Profile artwork and Settings routing are validated independently. A live photo is accepted only
when resource/accessibility context explicitly identifies the current account/profile and contains
none of the Status/story/contact/chat rejection signals. Otherwise the fifth slot uses an
independently drawn neutral placeholder and never substitutes an arbitrary photo. Its click
delegates only to a separately discovered explicit Settings action. If Settings cannot be proven,
that custom slot is disabled and the original Settings entry remains accessible.

After validation, the controller moves the **real host navigation ViewGroup** into one
`GlassNavigationSurface` attached to the activity `DecorView`. Its original pixels and accessibility
tree are hidden while it stays attached as the functional source of truth. A separate, fully custom
icon-only presentation uses independently authored vectors: segmented Updates ring, outlined
handset, three-person outline, and two overlapping speech bubbles. It mirrors only each host
destination's live badge, accessible label, and selected/activated state. Custom clicks delegate to
the matching
host destination; no obfuscated routing method or hard-coded destination action is used. If even
one destination has no safely mirrorable drawable, the mutation is rolled back and stock navigation
is restored. Host destination artwork is never used for the four standard visible icons.

No text label is created inside the floating bar. Every destination receives one equal-width visual
cell and a normalized 42 dp optical box; navigation icons target 29 dp while the circular live avatar
targets 32 dp. The selected pill is inset inside exactly one cell and cannot span neighboring slots.
Avatar/bitmap drawables keep their original color;
other runtime host drawables receive the design-system active/inactive emphasis. Badge visibility
and text are refreshed from the host hierarchy and badge numbers are never hard-coded. The outer
surface uses one `LocalizedBackdropGlassView` and therefore one bounded host hierarchy recording
pass for the complete capsule. The darker selection pill and every custom icon/badge are retained
child layers; none performs a second backdrop capture.

The localized renderer continues to sample only the content-root region behind the capsule. It
uses retained RenderNode replay plus the device-accepted RuntimeShader effect and has no PopupWindow,
`FLAG_BLUR_BEHIND`, PixelCopy, screenshot, bitmap-capture, or CPU-blur path.

## Home chrome

The existing chat list and its data remain native. Phase 5A applies a reversible near-black/light
Home background and removes toolbar elevation. Structurally validated toolbar actions are visually
replaced by custom circular controls in one lightweight overlay: their host pixels/accessibility are
hidden, their icons and labels are mirrored at runtime, and clicks/long-clicks delegate to the real
host actions. These compact controls use retained gradient/rim layers and intentionally create no
additional backdrop-capture loops.

A structurally validated bottom-right Home FAB is treated the same way: its original alpha and
accessibility importance are snapshotted, its pixels are suppressed while Chats is active, and a
48 dp lightweight Phase-5A action delegates click/long-click to that exact host View. This removes
the green stock surface from the sampled backdrop without inventing a new-chat route. If a safe host
FAB is not found, no custom FAB is fabricated.

The search input is **not** functionally replaced: the real host search view keeps its text input,
focus, IME, and query behavior while receiving a custom rounded glass-like background, 16 dp Home
margins, and normalized 14 dp horizontal padding. A large
localized Chats title is added only when the Chats destination can be identified safely from host
resources/state. Phase 5A does not replace the chat RecyclerView or aggressively restyle rows.

The largest visible RecyclerView-like Home content view receives reversible bottom padding equal to
the floating bar height plus margin, with `clipToPadding=false`. This prevents permanent coverage of
the last row while allowing content to move behind the glass. Gesture insets add to the configured
bottom margin. The bar becomes invisible while the IME is visible. A retained scroll observer
applies threshold/hysteresis: downward Chats scrolling slides the existing surface away, upward
scrolling restores it, and returning to list top forces it visible. This changes only translation,
alpha, and visibility; it does not detach or rebuild the renderer.

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

The controller snapshots the navigation parent, index, layout parameters, navigation background,
elevation, alpha and accessibility importance; toolbar-action alpha/accessibility; content padding;
toolbar/search backgrounds; toolbar logo visibility; and Home background. Home-chrome mutation is
transactional: an exception while creating custom title, search chrome, or action overlay restores
the already-mutated state before propagating to the outer rollback. A failure after navigation
mutation removes the custom surface and restores every snapshot. A pause, activity destruction,
feature stop, or renderer failure uses the same rollback path. Posted discovery work is accepted
only while that exact Home activity is resumed.

The implementation must never deliberately leave both navigations visible, remove all navigation,
or place an unclickable replacement over the host. Only the custom destination controls consume
intended navigation input and delegate it to the paired host destinations; decorative glass and
selection layers are non-clickable and excluded from accessibility. Custom top actions are active
only while Chats is selected; other Home destinations regain their stock action visuals.

## Diagnostics and performance evidence

Runtime diagnostics schema 11 includes `phase5aHome` with:

- enablement/status, lifecycle and Home detection;
- discovery attempts and sanitized host navigation class;
- destination, badge, active-index, attachment, and single-surface counts;
- custom-navigation visibility, hidden-host state, zero visible labels, and custom top-action count;
- canonical visual destination order, profile-source/action validation, FAB detection/suppression,
  custom-FAB state, active-pill bounds, and scroll-controller installation;
- effective outer tint, production blur/refraction, and bar-height tokens;
- content-padding, toolbar, and search mutation state;
- hierarchy capture count, last/rolling/worst capture duration, and approximate RenderNode bytes;
- draw-path-only runtime verification and a sanitized failure summary.

In-memory metrics update on frames, but diagnostics publication is bounded to the first captured
frame and every 300th capture. No per-frame file/Binder publication is introduced. Phase 5A does
not include invented performance results; `dumpsys gfxinfo` and `dumpsys meminfo` measurements must
be collected on the OnePlus 15 with the redesign disabled and enabled under matched scrolling.

## JVM coverage

Pure tests cover recovery gating, structural navigation validation, exact five-slot order and
functional-source reordering, selected-state mapping, strict profile validation, hidden-host
rollback, single-cell selection geometry, normalized icon/avatar optical boxes, scroll hysteresis,
legacy conflict ownership, bottom-inset/IME policy, prevention of a second concurrent glass
surface, one-to-one host-icon requirements, zero-label policy, and restrained production token
bounds. They do not claim GPU or obfuscated-host verification.

## OnePlus 15 device test

Target: Android 16 / OxygenOS 16, KernelSU Next, LSPosed, official WhatsApp 2.26.38.73.

1. Install the Phase 5A debug APK, enable only `com.whatsapp` in LSPosed, and force-stop both apps.
2. With `iOS / Liquid Glass Home redesign` off, launch WhatsApp and confirm the stock Home,
   destinations, chats, badges, and scrolling are unchanged.
3. Enable the redesign in the companion, keep safe mode/Disable all/Disable visual off, force-stop
   WhatsApp, and open the Chats screen for at least five seconds.
4. Confirm exactly one slim 62 dp icon-only floating capsule appears in the order Updates, Calls,
   Communities, Chats, Avatar. Confirm there is no destination text, opaque strip, duplicate stock
   bar, distorted edge, repeated/offset chat text, or green stock-FAB blob; all pixels outside the
   capsule must remain sharp.
5. With Chats active, confirm the dark pill occupies only the Chats cell. Open every displayed
   destination. Confirm the real screen changes, the dark selected pill follows
   within a short restrained transition, live badge values and avatar remain correct, and custom
   accessibility labels/clicks map to the corresponding host destinations.
6. Exercise every replaced top action and the custom new-chat/FAB action visible on Home. Confirm
   each routes to the same host action, the original green FAB stays visually suppressed on Chats,
   and search still accepts text, focus, IME, and query changes.
7. Scroll down through Chats until the bar slides away, then scroll up and confirm it returns; at
   list top it must be visible. Fast-scroll and confirm backdrop content updates dynamically,
   blur/refraction stay
   inside the capsule, the last row can scroll above the bar, and there is no recursion, stale/black
   frame, or full-screen blur.
8. Tap empty regions around the capsule and controls near it; confirm only destination controls
   consume navigation input.
9. Open and close the keyboard, background/foreground WhatsApp, rotate if enabled, and repeat three
   cold starts. Confirm no ghost surface and no bar above the IME or non-Home screens.
10. Exercise normal chats, send/receive, media, and the Phase 3 privacy settings selected for the
   regression pass. Phase 5A must not alter their behavior.
11. Export runtime diagnostics after active scrolling. Require `destinationCount=5`,
    `visualDestinationOrder=UPDATES,CALLS,COMMUNITIES,CHATS,PROFILE`,
    `profileSourceDiscovered=true`, `profileActionMapped=true`,
    `hostFabVisualSuppressed=true` when a host FAB was detected,
    `customNavigationVisible=true`, `hostNavigationVisuallyHidden=true`,
    `visibleNavigationLabelCount=0`, `glassSurfaceCount=1`,
    `floatingBottomBarAttached=true`, capture count increasing, no `homeFailure`, and
    `runtimeVerified=true` only after an actual draw.
12. Repeat with Disable visual modifications, Disable all hooks, and startup safe mode separately.
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

## OnePlus correction pass

The current correction pass is based on the latest real-device screenshot, not a simulated host.
It replaces all four standard host artworks with independent Phase-5A vectors, separates own-account
avatar discovery from Settings routing, and uses a neutral profile placeholder whenever ownership
cannot be proven. A Status/story/contact image is an explicit rejection signal.

Scroll visibility now reads the discovered RecyclerView's absolute vertical offset. If its public
offset method is unavailable, adapter position plus first-child top provides a recycling-safe
fallback. The thresholds are asymmetric: 24 dp down hides, 12 dp up shows, and list top forces
visible. The bar is translated/animated in place and is never detached for scroll visibility.

After moving the host navigation, an otherwise empty plausible-height wrapper is classified as the
obsolete black-strip source and collapsed transactionally. If the wrapper is not safe to collapse,
Home alone opts into public edge-to-edge window drawing with transparent navigation bar and keeps
the floating controls above gesture insets. Both paths have rollback snapshots.

Production material is `NATIVE_BLUR_EDGE_SHADER`: one retained host recording, one cached native
GPU blur, then one cached RuntimeShader for low-opacity tint, rim/highlight/depth and boundary-only
refraction. No per-frame shader/effect creation is added. The center uses a single already-blurred
sample, eliminating the separated multi-offset text copies while retaining a real dynamic backdrop.

Diagnostics schema 12 adds icon/chat/selection identity; scroll source, events, offset, delta and
hidden state; independent avatar and Settings source state; original navigation-parent and system
insets; black-strip classification; and blur-first material/refraction mode. All fields remain
sanitized and contain no view text or personal content.

## Known risks and limits

- WhatsApp may change the `bottom_nav` resource or hierarchy; structural mismatch intentionally
  falls back to stock.
- Moving and visually hiding a host ViewGroup is reversible but must be verified against host
  lifecycle, layout assumptions, delegated clicks, accessibility, every live destination, badges,
  and avatar changes on the target build.
- Host icons and toolbar actions are discovered structurally. If WhatsApp removes their drawables or
  changes the hierarchy, Phase 5A intentionally restores stock rather than showing a partial custom
  navigation.
- The five-slot redesign deliberately fails closed when no safe live profile/avatar source can be
  proven. Some WhatsApp account/header variants may therefore remain stock until device evidence
  supports an additional structural profile source.
- One bounded host hierarchy recording pass per rendered bar frame remains additional GPU/UI work.
  Phase 4 proved visual feasibility, not Phase 5A scrolling cost.
- Toolbar/search discovery is conservative; unsupported layouts remain stock rather than receiving
  guessed mutations.
- This build is a development acceptance build. Phase 5B/5C work is blocked until the user approves
  the floating navigation and Chats/Home chrome on the real device.
