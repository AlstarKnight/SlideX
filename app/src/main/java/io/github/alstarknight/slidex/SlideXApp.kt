package io.github.alstarknight.slidex

import android.app.Application

class SlideXApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
