package ai.xp.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ai.xp.editor.core.Screen
import ai.xp.editor.core.XpPython
import ai.xp.editor.core.XpState
import ai.xp.editor.design.Haptics
import ai.xp.editor.design.Xp
import ai.xp.editor.ui.EditorScreen
import ai.xp.editor.ui.ExportScreen
import ai.xp.editor.ui.HomeScreen
import ai.xp.editor.ui.ImportScreen
import ai.xp.editor.ui.SettingsScreen
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Haptics.init(this)
        // Python boot (font + sample extraction) off the main thread
        thread(name = "xp-boot") {
            try {
                XpPython.ensureInit(applicationContext)
            } catch (_: Throwable) {
            }
        }
        setContent { XpRoot() }
    }
}

@Composable
fun XpRoot() {
    val ctx = LocalContext.current
    val state = remember { XpState(ctx) }

    DisposableEffect(Unit) {
        state.engine.start()
        onDispose { state.engine.stop() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Xp.cream)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Crossfade(
            targetState = state.screen,
            animationSpec = tween(220),
            label = "screens"
        ) { screen ->
            when (screen) {
                Screen.Home -> HomeScreen(state)
                Screen.Import -> ImportScreen(state)
                Screen.Editor -> EditorScreen(state)
                Screen.Export -> ExportScreen(state)
                Screen.Settings -> SettingsScreen(state)
            }
        }
    }
}
