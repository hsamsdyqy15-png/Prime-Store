package com.example

import android.app.Application
import com.example.crash.CrashReporter

class PrimeStoreApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.installUncaughtHandler(this)
    }
}
