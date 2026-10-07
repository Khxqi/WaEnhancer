# AirPods XAPL Fix

Minimal LSPosed module for the AirPods Pro 3 firmware 9A348 / 9442752 Android HFP disconnect loop.

## What it changes

Inside `com.android.bluetooth`, the module hooks:

`com.android.bluetooth.hfp.HeadsetNativeInterface.atResponseString(...)`

and rewrites only this outgoing HFP response:

`+XAPL=iPhone,2` -> `+XAPL=iPhone,0`

No Bluetooth addresses, AirPods data, codecs, A2DP settings, or LibrePods traffic are modified.

## Install / enable

1. Install the APK.
2. Open LSPosed.
3. Enable **AirPods XAPL Fix**.
4. Scope it **only** to **Bluetooth / `com.android.bluetooth`**.
5. Reboot.
6. In LibrePods, keep **Act as an Apple device** disabled while testing, because firmware 9A348 has a separate Apple-host/AAP disconnect issue.

## Verify

```sh
su
logcat -d | grep -F AirPodsXaplFix
```

Expected:

```text
[AirPodsXaplFix] active in com.android.bluetooth (hooked overloads=1)
[AirPodsXaplFix] rewrote +XAPL=iPhone,2 -> +XAPL=iPhone,0
```

## Rollback

Disable the module in LSPosed and reboot, or uninstall the APK. It writes no system files and no persistent Bluetooth properties.
