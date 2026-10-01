# Phase 3 privacy runtime

This document records the Phase 3 privacy/runtime migration for official WhatsApp. It does not
describe or implement the future iOS/Liquid Glass redesign. Runtime behavior is not marked as
device-verified until it is tested on the target device.

## Phase 2 baseline

Phase 3 starts from Phase 2 tree `f7cee4465c544b37de994d228eee365bfa7d6c5c`. The Phase 2 APK
was subsequently validated by the user on a OnePlus 15 running Android 16/OxygenOS 16 with
KernelSU Next, LSPosed, and official `com.whatsapp`. Cold start, repeated restarts, chats,
send/receive, media, startup safe mode, disable-all, and recovery after re-enabling passed.

## Pre-refactor privacy inventory

The inventory below was recorded before changing privacy hook installation. “Hook sites” counts
the repository-level installation calls; `hookAll*` and resolver-returned method collections can
produce more than one runtime hook.

| Existing behavior | Source | Preference/private keys | Resolver dependencies | Hook sites and behavior | Shared dependencies | Cleanup before Phase 3 |
|---|---|---|---|---|---|---|
| Anti-revoke messages/status | `features/general/AntiRevoke.kt` | `antirevoke`, `antirevokestatus`, `toastdeleted` | `loadAntiRevokeMessageMethod`, `loadUnknownStatusPlaybackMethod`, `loadStatusPlaybackViewClass`, `loadAntiRevokeFStatusMethod`, `UnobfuscatorCache` | Three method hooks; blocks deletion and adds optional deleted-message/status feedback | `WppCore`, `FMessageWpp`, `FStatusWpp`, `WaContactWpp`, stores, conversation listener | None |
| Manual/send-on-reply receipts | `features/general/SeenTick.kt` | `seentick`, `blueonreply`, reads `hidestatusview` | Blue-on-reply job/manager, conversation menu, status playback, view-once menu, send-job classes | Constructor plus method hooks and menu providers; runtime receipt sending mixed with buttons/menus | `WppCore`, message/status wrappers, history store, menu provider | None |
| Always online | `features/general/Others.kt` | `always_online` | `loadStateChangeMethod` | One replacement hook suppresses the state change | Hidden inside the large `Others` feature | None |
| Call privacy | `features/privacy/CallPrivacy.kt` | `call_privacy`, `call_type`, `call_block_contacts`, `call_white_contacts`; per-contact `BlockCall` | `loadVoipManager`, `loadAntiRevokeOnCallReceivedMethod`, JID class lookup | VoIP constructor set, incoming-call method, and incoming-offer method set; runtime call rejection | `WppCore` VoIP classes, `FMessageWpp`, `WaContactWpp`, `CustomPrivacy` | None |
| Per-contact privacy editor/storage | `features/privacy/CustomPrivacy.kt` | `custom_privacy_type`; per-contact `HideSeen`, `HideViewStatus`, `HideReceipt`, `HideTyping`, `HideRecording`, `BlockCall` | Contact/group info activities and user/group JID classes | Activity callback or two menu hooks plus menu provider; configuration UI/support rather than message/network behavior | `WppCore` activity/private preferences, dialog/message wrappers | None |
| DND mode | `features/privacy/DndMode.kt` | private `dndmode` | `loadDndModeMethod` | One replacement hook | `WppCore` private preference | None |
| Freeze last seen | `features/privacy/FreezeLastSeen.kt` | `freezelastseen`, `ghostmode`; private `freezelastseen`, `ghostmode` | `loadFreezeSeenMethod` | One replacement hook suppresses presence update | `WppCore` private preference | None |
| Hide archived chat entry | `features/privacy/HideChat.kt` | `typearchive` | `loadArchiveChatClass` | Constructor set replaces a view; visual-only despite its package | `WppCore` current activity | None |
| Hide receipts/seen/played/status views | `features/privacy/HideSeen.kt` | `hideread`, `hideaudioseen`, `hideonceseen`, `hideread_group`, `hidestatusview`, `hidereceipt`; private `ghostmode`; per-contact overrides | Read-receipt, send-read-job, receipt, dispatch, sender-played methods/classes | Multiple method hooks; suppresses receipts and records local hidden-receipt state | `WppCore`, message/protocol wrappers, history store, `CustomPrivacy`, `Others.propsBoolean` | None |
| Locked chats enhancer | `features/privacy/LockedChatsEnhancer.kt` | `lockedchats_enhancer` | Notification, locked-chat, chat-cache, loaded-contact targets | Three method hooks plus constructor set; filters locked-chat data in notification/contact paths | `FMessageWpp.UserJid`, `WaContactWpp`, reflection cache | None |
| Forward/broadcast tag handling | `features/privacy/TagMessage.kt` | `hidetag`, `broadcast_tag` | `loadForwardTagMethod`, `loadForwardClassMethod` | One runtime hook hides forwarded tag; separate conversation listener adds a broadcast icon | Message wrapper, conversation listener, design utilities | None |
| Typing/recording privacy | `features/privacy/TypingPrivacy.kt` | `ghostmode_t`, `ghostmode_r`; private `ghostmode`; per-contact overrides | `loadGhostModeMethod` | One method hook suppresses typing/recording updates | `WppCore` private preference, message/JID wrapper, `CustomPrivacy` | None |
| Unlimited view-once | `features/privacy/ViewOnce.kt` | `viewonce` | `loadViewOnceMethod` | Hooks every returned method and changes received view-once state | `FMessageWpp` | None |
| Root/ROM/emulator anti-detection | `features/privacy/AntiWa.kt` | `bootloader_spoofer` | root/emulator/custom-ROM detection targets plus platform APIs | Multiple broad spoofing hooks | Global/platform behavior | Deliberately inactive since Phase 2; excluded from Phase 3 |

Related but non-migrated surfaces:

- `features/customization/HideSeenView.kt` is visual feedback only and remains visual.
- `features/others/MenuHome.kt` supplies UI controls for ghost, DND, and freeze-last-seen private
  state. It is not itself the privacy enforcement hook.
- `features/media/DownloadViewOnce.kt` and call recording are media features, not privacy-hook
  enforcement, and remain outside Phase 3.
- call-information display and other general diagnostics are not classified as privacy controls.

## Boundary decisions

- `HideChat` must be classified as visual so disable-visual suppresses it.
- `TagMessage` must separate forwarded-tag enforcement from its broadcast icon.
- Anti-revoke and manual receipt controls may retain user-facing feedback required by their
  existing semantics; they must not depend on theme engines or future UI code.
- Per-contact lookup with a missing/ambiguous contact context must return empty overrides and
  preserve stock behavior. Identifiers must never enter runtime diagnostics.
- `AntiWa` remains inactive. Phase 3 does not restore bootloader/root spoofing or system scope.

## Implemented startup and ownership model

`RuntimeBootstrap` now reads a typed `PrivacyConfigSnapshot` before registering privacy features.
Recovery policy is evaluated by `PrivacyFeaturePolicy`: failed configuration transport, startup
safe mode, or disable-all makes every privacy feature disabled; disable-visual only disables the
two visual entries. After resolver/cache initialization, `PrivacyCapabilityResolver` resolves only
the named targets needed by enabled managed features. `PrivacyFeatureRegistry` registers the
privacy entries before the remaining `LegacyRuntimeAdapter` entries, and one failed entry does not
stop later entries.

Managed installers receive a `HookInstallScope`. Each registered Xposed `Unhook` or listener
cleanup is owned by that feature. If a later step in the same installer throws, registered handles
are rolled back in reverse order. This is install-time rollback only: Phase 3 does not claim safe
process-wide hot toggling after startup. A WhatsApp restart remains required after settings change.

`RuntimeDiagnostics` schema 2 reports the stable ID, diagnostic name, enabled decision, state,
required capability states, known hook counts, rollback status, and whether the feature still owns
legacy enablement. `installed` means the installer returned (`READY`); `runtimeVerified` remains
false until a future truthful runtime signal exists. Diagnostics do not intentionally include
message text, contact names, phone numbers, JIDs, or chat contents.

## Feature and capability mapping

| Stable feature ID | Category | Capability requirements | Hook ownership | Phase 3 state |
|---|---|---|---|---|
| `privacy.presence.always-online` | Privacy | `privacy.presence.always-online.target` | One owned Xposed handle | Managed |
| `privacy.network.dnd` | Privacy | `privacy.network.dnd.target` | One owned Xposed handle | Managed |
| `privacy.presence.freeze-last-seen` | Privacy | `privacy.presence.freeze-last-seen.target` | One owned Xposed handle | Managed |
| `privacy.presence.typing-recording` | Privacy | `runtime.message-components`, `legacy.core-components`, `privacy.presence.typing-recording.target` | One owned Xposed handle | Managed; per-contact lookup remains legacy |
| `privacy.media.unlimited-view-once` | Privacy | `runtime.message-components`, `privacy.media.view-once.targets` | One owned handle per resolved method | Managed |
| `privacy.metadata.hide-forward-tag` | Privacy | `privacy.metadata.forward-tag.target` | One owned Xposed handle | Managed |
| `visual.metadata.broadcast-indicator` | Visual | `runtime.message-components`, `legacy.core-components` | One removable conversation listener | Managed visual half of `TagMessage` |
| `privacy.revoke.anti-revoke` | Privacy | `legacy.core-components`, `privacy.revoke.anti-revoke.preflight` | Counts/rollback unknown | Explicit registry entry; legacy installer |
| `privacy.receipts.manual-send` | Privacy | `legacy.core-components`, `privacy.receipts.manual-send.preflight` | Counts/rollback unknown | Explicit registry entry; legacy installer |
| `privacy.calls.blocking` | Privacy | `legacy.core-components`, `privacy.calls.blocking.preflight` | Counts/rollback unknown | Explicit registry entry; legacy installer |
| `privacy.context.per-contact-editor` | Privacy | `legacy.core-components`, `privacy.context.per-contact-editor.preflight` | Counts/rollback unknown | Explicit registry entry; legacy installer |
| `privacy.receipts.hide-seen` | Privacy | `legacy.core-components`, `privacy.receipts.hide-seen.preflight` | Counts/rollback unknown | Explicit registry entry; legacy installer |
| `privacy.chats.locked-enhancer` | Privacy | `runtime.message-components`, `resolver.cache`, `privacy.chats.locked-enhancer.preflight` | Counts/rollback unknown | Explicit registry entry; legacy installer |
| `visual.chats.hide-archive` | Visual | `legacy.core-components` | Counts/rollback unknown | Explicit visual entry; legacy installer |

The feature-specific capability validators check the shapes actually consumed by the existing
hooks: the typing target must expose an integer argument, every view-once target must begin with an
integer argument, and the forwarding target must begin with a long argument. Resolution failure is
recorded on that capability and produces `UNSUPPORTED` for only the dependent feature.
Complex legacy installers also have named preflight capabilities that resolve and shape-check their
first critical targets without installing hooks. They reduce partial legacy installation but do not
claim that every later target in those large classes has been validated.

## Configuration path

`PrivacyConfigReader` parses only the Phase 3 keys into typed booleans, bounded integer modes, and
an allow-listed call rejection type. Invalid/missing values use stock-safe defaults. It reads from
the Phase 2 `RuntimeConfigAccess` transport (`XSharedPreferences` when readable, otherwise the
hardened remote preferences provider). No arbitrary-path bridge is used or restored.

Managed installers consume the snapshot for startup decisions. The following legacy runtime reads
remain intentionally:

- anti-revoke modes/toast, manual receipt controls, call lists/mode, hide-receipt modes, locked-chat
  enablement, and archive mode are reread by their existing implementations;
- `CustomPrivacy` reads per-contact JSON from `WppCore` private preferences;
- typing/recording uses the typed global defaults but still asks `CustomPrivacy` for a per-contact
  override at callback time;
- DND, menu ghost mode, and the toolbar freeze toggle are stored in `WppCore` private preferences.

An empty/missing per-contact identifier returns an empty override object, preserving the global or
stock decision; it is never treated as permission to apply an override globally.

## Remaining legacy dependencies and limitations

- `WppCore.initialize`, `AlertDialogWpp`, `DesignUtils`, `Utils`, and activity lifecycle callbacks
  remain grouped under `legacy.core-components`.
- Message/JID/status/contact wrappers now initialize separately as `runtime.message-components`,
  so unlimited view-once and locked-chat prerequisites no longer depend on every unrelated core
  component. Typing and the broadcast listener still require the legacy core.
- Anti-revoke, manual receipts, call privacy, per-contact editor, hide-seen, locked-chat enhancer,
  and hide-archive retain direct `Unobfuscator` calls and do not expose reliable hook counts or
  rollback. A throw is isolated, but hooks installed before that throw may remain until process
  restart; diagnostics mark this possibility instead of claiming reversibility.
- Anti-revoke and manual receipts retain their existing UI feedback because it is part of current
  behavior. They are not coupled to `BubbleColors`, `CustomThemeV2`, floating bars, toolbar/tab
  styling, or any future Liquid Glass component.
- `AntiWa` remains inactive. Android/System Framework scope, WhatsApp Business, root/bootloader
  spoofing, and the generic file bridge remain out of scope.
- No actual obfuscated WhatsApp hook is unit-testable off-device. JVM tests cover registry
  isolation, unsupported capability fallback, rollback accounting, diagnostic redaction, and
  recovery-policy gating only.

## Recovery regression contract

- **Disable all hooks** and failed configuration transport stop all privacy and legacy installers.
- **Startup safe mode** skips resolver/capability work and all optional feature installation.
- **Disable visual modifications** disables `visual.metadata.broadcast-indicator`,
  `visual.chats.hide-archive`, and legacy visual entries without disabling privacy entries.
- Active LSPosed scope remains only `com.whatsapp`; no Business or Android/System Framework scope
  was added.

## Target-device test matrix

These are post-build acceptance tests for the OnePlus 15 / Android 16 / OxygenOS 16 / KernelSU
Next / LSPosed target. Passing CI does not mark any hook runtime-verified.

First run the baseline and recovery checks: cold start; three force-stop/restart cycles; chats list;
open/close a conversation; send/receive text; open media; open WhatsApp Settings; confirm LSPosed
shows `com.whatsapp`; verify the companion settings; then test startup safe mode, disable-all,
disable-visual, and restoration after re-enabling. Restart WhatsApp after every module setting
change.

| Feature ID | Enable/action | Stock result | Enabled result to verify | Disable-again result / diagnostic |
|---|---|---|---|---|
| `privacy.presence.always-online` | Privacy → **Always Online**; observe from a second account, then background WhatsApp | Account goes offline normally | Account remains online while the process remains alive | Goes offline normally; feature changes from `READY` to `DISABLED` after restart |
| `privacy.network.dnd` | Enable **Show DND Button**, use the WhatsApp home DND control, send from a second account | Message delivery proceeds | Existing DND behavior suppresses the resolved dispatch until turned off | Delivery returns; diagnostic is `READY` only when private DND state was on at startup |
| `privacy.presence.freeze-last-seen` | Privacy → **Freeze Last Seen** (or enabled toolbar control); compare from a second account | Last-seen advances | Last-seen remains at its prior value | Last-seen resumes; inspect target capability and one hook handle |
| `privacy.presence.typing-recording` | Enable **Hide Typing** and then **Hide Recording Audio** separately; observe from a second account | Typing/recording indicator appears | Corresponding indicator is absent | Indicator returns; inspect typing capability and one handle |
| `privacy.media.unlimited-view-once` | Enable **Unlimited View Once**; receive a view-once image/video and reopen it | Media opens once | Existing feature permits reopening | One-open behavior returns; handle count equals resolved target count |
| `privacy.metadata.hide-forward-tag` | Enable **Hide Forward Tag**; forward a message to a second account | Recipient sees forwarded marking | Existing forwarding path suppresses the marking | Marking returns; inspect forwarding capability and one handle |
| `privacy.revoke.anti-revoke` | General → Conversation → **Anti-Revoke**; have a second account delete a test message/status | Deleted item disappears | Existing configured text/icon behavior remains | Stock deletion returns; diagnostic may be `READY` but `runtimeVerified=false` and counts unknown |
| `privacy.receipts.manual-send` | Enable **Hide Read**, then select a **Show button to send blue tick** mode; open an unread chat and invoke its button/menu | Stock read receipt is automatic | Receipt stays hidden until the existing manual action sends it | Automatic/stock behavior returns; legacy counts remain unknown |
| `privacy.calls.blocking` | Privacy → **Call Blocker** plus one rejection type; call from a second account | Incoming call rings normally | Existing configured reject/block result occurs | Calls ring normally; no phone/JID may appear in diagnostics |
| `privacy.context.per-contact-editor` | Enable **Custom Privacy Per Contact**; open a test contact/group info and its custom-privacy entry; set one override | Only global behavior applies | Override applies only to the selected context | Removing override restores global/stock behavior; ambiguous context must not apply globally |
| `privacy.receipts.hide-seen` | Test **Hide Read**, group read, status view, audio played, view-once played, and delivery receipt one at a time with a second account | Matching receipt is sent | Only the enabled receipt type is suppressed | Receipt returns when disabled; legacy counts remain unknown |
| `privacy.chats.locked-enhancer` | Enable **Enhanced Locked Chats**; lock a test chat, then test notification and contact-list paths | Stock locked-chat exposure rules | Existing enhancer hides the notification/contact entry | Stock behavior returns; inspect message-components/resolver-cache states |
| `visual.metadata.broadcast-indicator` | Enable **Show Broadcast Icon**, bind a broadcast item, then repeat with disable-visual on | No module icon | Existing icon appears | Disable-visual removes it while privacy entries remain enabled |
| `visual.chats.hide-archive` | Set **Hide Archived Chat** mode, then repeat with disable-visual on | Stock archived entry | Existing selected hide mode applies | Disable-visual restores stock visual entry while privacy remains enabled |

For every row, export runtime diagnostics after restart and confirm the feature ID, enabled decision,
status, capability states, and handle count/legacy marker. Do not mark the behavior passed merely
because its installer reports `READY`.

## Device log collection

Raw Android and WhatsApp logs can contain unrelated sensitive data. Review/redact before sharing.

```bash
# Start a clean capture, reproduce once, then save all relevant buffers.
adb logcat -c
adb shell am force-stop com.whatsapp
adb shell monkey -p com.whatsapp 1
adb logcat -v threadtime -b main -b system -b crash -d > phase3-whatsapp.log

# Immediate crash evidence and Android crash dropbox.
adb logcat -v threadtime -b crash -d > phase3-crash.log
adb shell dumpsys dropbox --print data_app_crash > phase3-dropbox.txt

# Narrow live capture for one feature or resolver/config failure.
adb logcat -c
adb logcat -v threadtime | grep -E 'WAE|Vector-lsposed|WaEnhancer|LSPosed|DexKit|AndroidRuntime'

# Confirm process/package state.
adb shell pidof com.whatsapp
adb shell dumpsys package com.whatsapp | grep -E 'versionName|versionCode'

# Collect LSPosed module/verbose logs (root required), without guessing one filename.
adb shell su -c 'ls -lt /data/adb/lspd/log'
adb shell su -c 'tar -C /data/adb/lspd/log -czf /data/local/tmp/phase3-lsposed-logs.tgz .'
adb shell su -c 'chmod 0644 /data/local/tmp/phase3-lsposed-logs.tgz'
adb pull /data/local/tmp/phase3-lsposed-logs.tgz
```

For a configuration-read failure, also export diagnostics from the companion app and search the
capture for `WaEnhancer config`, `REMOTE_PROVIDER`, or `FAILED_CLOSED`. For one feature failure,
include its stable feature ID and exported diagnostics; for resolver failure, include the failed
capability ID. Do not include test-contact identifiers in the report.
