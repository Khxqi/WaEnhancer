package io.github.khxqi.airpodsxaplfix;

import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPosed/Xposed workaround for AirPods Pro 3 firmware 9A348 (9442752).
 *
 * AOSP advertises Apple HFP battery reporting with "+XAPL=iPhone,2". On the
 * affected AirPods firmware this causes a malformed AT+IPHONEACCEV exchange and
 * a repeating HFP disconnect cycle. This module changes only that one outgoing
 * response to "+XAPL=iPhone,0".
 */
public final class XaplFixModule implements IXposedHookLoadPackage {
    private static final String TAG = "AirPodsXaplFix";
    private static final String TARGET_PACKAGE = "com.android.bluetooth";
    private static final String TARGET_CLASS = "com.android.bluetooth.hfp.HeadsetNativeInterface";
    private static final String TARGET_METHOD = "atResponseString";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) return;

        try {
            Class<?> nativeInterface = XposedHelpers.findClass(TARGET_CLASS, lpparam.classLoader);

            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(
                    nativeInterface,
                    TARGET_METHOD,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.args == null) return;

                            // Hook all overloads instead of relying on a vendor-specific method
                            // signature. Only String arguments equal to the known XAPL response
                            // are touched; all other calls are left untouched.
                            for (int i = 0; i < param.args.length; i++) {
                                Object arg = param.args[i];
                                if (!(arg instanceof String)) continue;

                                String original = (String) arg;
                                String rewritten = XaplRewriter.rewriteIfNeeded(original);
                                if (!original.equals(rewritten)) {
                                    param.args[i] = rewritten;
                                    XposedBridge.log("[" + TAG + "] rewrote "
                                            + XaplRewriter.BROKEN_RESPONSE + " -> "
                                            + XaplRewriter.FIXED_RESPONSE);
                                }
                            }
                        }
                    });

            if (hooks == null || hooks.isEmpty()) {
                XposedBridge.log("[" + TAG + "] ERROR: no " + TARGET_METHOD
                        + " method found in " + TARGET_CLASS);
            } else {
                XposedBridge.log("[" + TAG + "] active in " + lpparam.packageName
                        + " (hooked overloads=" + hooks.size() + ")");
            }
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] ERROR while installing hook");
            XposedBridge.log(t);
        }
    }
}
