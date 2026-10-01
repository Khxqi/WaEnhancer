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

## Migration status

Implementation status will be updated as the migration proceeds. This pre-refactor inventory is
the authoritative record of behavior discovered before code changes.
