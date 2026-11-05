package ai.xp.editor

import android.content.Context
import com.chaquo.python.android.PyApplication

/**
 * Chaquopy starts CPython in onCreate; the app's Application must be a
 * PyApplication subclass or Python.getInstance() never has a runtime.
 */
class XpApp : PyApplication() {
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
