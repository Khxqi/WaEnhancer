package com.wmods.wppenhacer.xposed.runtime

import de.robv.android.xposed.XC_MethodHook

fun HookInstallScope.registerXposed(id: String, unhook: XC_MethodHook.Unhook) {
    register(id, object : HookHandle {
        override fun unhook() = unhook.unhook()
    })
}

fun HookInstallScope.registerXposed(idPrefix: String, unhooks: Set<XC_MethodHook.Unhook>) {
    unhooks.forEachIndexed { index, unhook ->
        registerXposed("$idPrefix[$index]", unhook)
    }
}
