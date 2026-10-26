package com.kexu.campus.focus

/**
 * 当前锁机会话（轻量模式）。
 * 只保存在内存里：进程被系统杀掉后锁机自动结束，避免"锁死自己"。
 */
object LockSession {

    @Volatile
    var active: Boolean = false
        private set

    @Volatile
    var endAtMillis: Long = 0L
        private set

    @Volatile
    var allowedPackages: Set<String> = emptySet()
        private set

    @Volatile
    var briefUnlockUntil: Long = 0L
        private set

    @Volatile
    var briefUnlockRemaining: Int = 0
        private set

    @Volatile
    var unlockDurationMinutes: Int = 5
        private set

    @Volatile
    var plannedMinutes: Int = 60
        private set

    @Volatile
    var initialUnlockCount: Int = 0
        private set

    fun start(durationMinutes: Int, allowed: Set<String>, unlockMinutes: Int, unlockCount: Int) {
        active = true
        endAtMillis = System.currentTimeMillis() + durationMinutes * 60_000L
        allowedPackages = allowed
        briefUnlockUntil = 0L
        briefUnlockRemaining = unlockCount.coerceAtLeast(0)
        unlockDurationMinutes = unlockMinutes.coerceAtLeast(1)
        plannedMinutes = durationMinutes
        initialUnlockCount = unlockCount.coerceAtLeast(0)
    }

    fun stop() {
        active = false
        endAtMillis = 0L
        allowedPackages = emptySet()
        briefUnlockUntil = 0L
        briefUnlockRemaining = 0
    }

    fun isBriefUnlocking(now: Long = System.currentTimeMillis()): Boolean =
        briefUnlockUntil > now

    /** 返回是否成功消耗一次短时解锁。 */
    fun tryStartBriefUnlock(now: Long = System.currentTimeMillis()): Boolean {
        if (!active || briefUnlockRemaining <= 0) return false
        briefUnlockRemaining -= 1
        briefUnlockUntil = now + unlockDurationMinutes * 60_000L
        return true
    }

    fun endBriefUnlock() {
        briefUnlockUntil = 0L
    }

    fun remainingSeconds(now: Long = System.currentTimeMillis()): Int =
        ((endAtMillis - now) / 1000).toInt().coerceAtLeast(0)
}
