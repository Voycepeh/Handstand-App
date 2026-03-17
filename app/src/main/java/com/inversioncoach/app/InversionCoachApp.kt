package com.inversioncoach.app

import android.app.Application
import androidx.work.Configuration
import com.inversioncoach.app.history.RetentionCleanupWorker

class InversionCoachApp : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        RetentionCleanupWorker.enqueuePeriodic(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
