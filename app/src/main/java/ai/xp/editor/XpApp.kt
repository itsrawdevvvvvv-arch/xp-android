package ai.xp.editor

import android.app.Application
import android.content.Context

class XpApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: XpApp
            private set
        val context: Context get() = instance.applicationContext
    }
}
