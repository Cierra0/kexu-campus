package com.kexu.campus

import android.app.Application
import com.kexu.campus.data.CampusRepository
import com.kexu.campus.data.Feedback
import com.kexu.campus.focus.LockScheduler

class CampusApp : Application() {

    lateinit var repo: CampusRepository
        private set

    override fun onCreate() {
        super.onCreate()
        Feedback.installCrashHandler(this)
        repo = CampusRepository(this)
        runCatching { LockScheduler.schedule(this, repo.data.value.settings) }
    }
}
