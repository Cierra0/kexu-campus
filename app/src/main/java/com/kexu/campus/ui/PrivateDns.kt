package com.kexu.campus.ui

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build

/** 系统「私人 DNS」是否开启（开启后 DoT 会绕过本机 VPN 的 DNS 拦截）。 */
internal fun isPrivateDnsActive(context: Context): Boolean = runCatching {
    if (Build.VERSION.SDK_INT >= 28) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.getLinkProperties(cm.activeNetwork)?.isPrivateDnsActive == true
    } else {
        false
    }
}.getOrDefault(false)
