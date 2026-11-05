package ai.xp.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.xp.editor.core.Screen
import ai.xp.editor.core.XpPython
import ai.xp.editor.core.XpState
import ai.xp.editor.design.Accent
import ai.xp.editor.design.Body
import ai.xp.editor.design.HairLine
import ai.xp.editor.design.Haptics
import ai.xp.editor.design.Kicker
import ai.xp.editor.design.MintPill
import ai.xp.editor.design.Segmented
import ai.xp.editor.design.Title
import ai.xp.editor.design.Xp
import ai.xp.editor.design.XpPanel
import ai.xp.editor.design.XpText
import ai.xp.editor.design.XpToggle
import ai.xp.editor.design.pressable
import ai.xp.editor.design.xpEnter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun SettingsScreen(state: XpState) {
    val ctx = LocalContext.current
    var info by remember { mutableStateOf<JSONObject?>(null) }
    var cacheMb by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        info = withContext(Dispatchers.IO) {
            try {
                XpPython.ensureInit(ctx)
                XpPython.info()
            } catch (t: Throwable) {
                JSONObject().put("ok", false)
            }
        }
        cacheMb = withContext(Dispatchers.IO) { state.cacheBytes() / (1024f * 1024f) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Xp.ink.copy(alpha = 0.06f))
                    .pressable({ state.screen = Screen.Home }),
                contentAlignment = Alignment.Center
            ) { XpText("‹", size = 20) }
            Spacer(Modifier.width(12.dp))
            Column {
                XpText("Settings", size = 26, weight = FontWeight.Bold)
                Accent("performance, feel, runtime", size = 12)
            }
        }

        Spacer(Modifier.height(18.dp))

        XpPanel(Modifier.fillMaxWidth().xpEnter(0), color = Xp.paper, radius = 22.dp, shadow = 4.dp) {
            Column(Modifier.padding(16.dp)) {
                Kicker("PREVIEW QUALITY", size = 9)
                Spacer(Modifier.height(8.dp))
                Segmented(
                    items = listOf("540p", "720p", "1080p"),
                    selected = state.previewQuality,
                    onSelect = {
                        state.previewQuality = it
                        state.applyPreviewQuality()
                        state.engine.invalidate()
                        Haptics.select()
                    }
                )
                Spacer(Modifier.height(8.dp))
                Body(
                    if (state.isLowRam)
                        "Low-RAM device detected: preview is capped at 720p and 24fps."
                    else "Scrubs always read the 540p cache; playback uses the size above.",
                    size = 11
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        XpPanel(Modifier.fillMaxWidth().xpEnter(1), color = Xp.paper, radius = 22.dp, shadow = 4.dp) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Title("Custom haptics", size = 14)
                        XpText("waveforms on every snap and press", size = 11, color = Xp.inkMute)
                    }
                    XpToggle(state.hapticsOn, {
                        state.toggleHaptics()
                        Haptics.success()
                    })
                }
                Spacer(Modifier.height(14.dp))
                HairLine()
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Title("Safe guides", size = 14)
                        XpText("show the 90% frame in preview", size = 11, color = Xp.inkMute)
                    }
                    XpToggle(state.showGuides, { state.showGuides = it })
                }
                Spacer(Modifier.height(14.dp))
                HairLine()
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Title("Loop playback", size = 14)
                        XpText("restart at the end", size = 11, color = Xp.inkMute)
                    }
                    XpToggle(state.loopOn, {
                        state.loopOn = it
                        state.engine.loop = it
                    })
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        XpPanel(Modifier.fillMaxWidth().xpEnter(2), color = Xp.paper, radius = 22.dp, shadow = 4.dp) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Title("Scrub cache", size = 14)
                        XpText("${"%.1f".format(cacheMb)} MB of 160 MB", size = 11, color = Xp.inkMute)
                    }
                    MintPill("Clear", selected = false, onClick = {
                        state.clearCache()
                        cacheMb = 0f
                        Haptics.thud()
                    })
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        XpPanel(Modifier.fillMaxWidth().xpEnter(3), color = Xp.ink, radius = 22.dp, shadow = 4.dp) {
            Column(Modifier.padding(16.dp)) {
                Kicker("PYTHON RUNTIME", size = 9, color = Xp.mint.copy(alpha = 0.7f))
                Spacer(Modifier.height(10.dp))
                val libs = info?.optJSONObject("libs")
                val rows = listOf(
                    "python" to (libs?.optString("python") ?: "…"),
                    "numpy" to (libs?.optString("numpy") ?: "…"),
                    "pillow" to (libs?.optString("pillow") ?: "…"),
                    "scipy" to (libs?.optString("scipy") ?: "not bundled"),
                    "cv2" to (libs?.optString("cv2") ?: "shim"),
                    "ffmpeg" to (libs?.optString("ffmpeg") ?: "shim")
                )
                rows.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        XpText(k, size = 12, color = Xp.cream.copy(alpha = 0.55f))
                        Spacer(Modifier.weight(1f))
                        XpText(
                            if (v.isEmpty() || v == "null") "—" else v,
                            size = 12, color = Xp.mint, weight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Accent("XP 1.0.0 · Kotlin + Compose + Chaquopy", size = 12)
        }
        Spacer(Modifier.height(30.dp))
    }
}
