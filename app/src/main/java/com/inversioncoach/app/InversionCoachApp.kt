package com.inversioncoach.app

import android.app.Application
import androidx.work.Configuration

class InversionCoachApp : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
