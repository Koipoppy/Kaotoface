package com.kaotoface.app

import android.app.Application
import com.kaotoface.app.core.SessionEngine
import com.kaotoface.app.core.SettingsStore

class KaoToFaceApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var engine: SessionEngine
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        engine = SessionEngine(this, settings)
    }
}
