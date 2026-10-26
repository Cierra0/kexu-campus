package com.kexu.campus.focus

import android.app.admin.DeviceAdminReceiver

/** 只用于「一键锁屏」的设备管理器；不申请擦除数据等危险权限。 */
class LockAdminReceiver : DeviceAdminReceiver()
