package ai.xp.editor.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.xp.editor.core.Screen
import ai.xp.editor.core.XpPython
import ai.xp.editor.core.XpState
import ai.xp.editor.design.Accent
import ai.xp.editor.design.Body
import ai.xp.editor.design.Haptics
import ai.xp.editor.design.HairLine
import ai.xp.editor.design.InkButton
import ai.xp.editor.design.Kicker
import ai.xp.editor.design.MintPill
import ai.xp.editor.design.Ring
import ai.xp.editor.design.StarField
import ai.xp.editor.design.Title
import ai.xp.editor.design.Xp
import ai.xp.editor.design.XpPanel
import ai.xp.editor.design.XpText
import ai.xp.editor.design.pressable
import ai.xp.editor.design.xpEnter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun HomeScreen(state: XpState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var scripts by remember { mutableStateOf<List<File>>(emptyList()) }

    LaunchedEffect(Unit) {
        scripts = withContext(Dispatchers.IO) {
            try {
                XpPython.ensureInit(ctx)
                XpPython.scripts()
            } catch (t: Throwable) {
                emptyList()
            }
        }
    }

    fun open(f: File) {
        state.screen = Screen.Editor
        state.loading = true
        scope.launch {
            val (scene, err) = withContext(Dispatchers.IO) { XpPython.load(f.absolutePath) }
            state.onLoaded(scene, err, f.absolutePath)
            if (scene == null) state.screen = Screen.Import
        }
    }

    Box(Modifier.fillMaxSize()) {
        StarField(Modifier.fillMaxSize(), count = 22, color = Xp.mintDeep, alpha = 0.45)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(18.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).xpEnter(0)) {
                    XpText("XP", size = 46, weight = FontWeight.Bold, letterSpacing = (-2).sp)
                    Accent("edit the video, not the code", size = 14, color = Xp.inkMute)
                }
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Xp.ink)
                        .pressable({ state.screen = Screen.Settings })
                        .xpEnter(1),
                    contentAlignment = Alignment.Center
                ) {
                    XpText("⚙", size = 16, color = Xp.mint)
                }
            }

            Spacer(Modifier.height(22.dp))

            // hero card
            XpPanel(
                modifier = Modifier.fillMaxWidth().xpEnter(2),
                color = Xp.ink,
                radius = 26.dp,
                shadow = 5.dp
            ) {
                Column(Modifier.padding(20.dp)) {
                    Kicker("PYTHON 3.11 · CHAQUOPY", size = 9, color = Xp.mint.copy(alpha = 0.75f))
                    Spacer(Modifier.height(10.dp))
                    XpText(
                        "Drop in a\nvideo script.",
                        size = 30, color = Xp.cream, weight = FontWeight.Bold, lineHeight = 34
                    )
                    Spacer(Modifier.height(8.dp))
                    XpText(
                        "XP reads its scene, gives you a timeline, and renders the MP4.",
                        size = 13, color = Xp.cream.copy(alpha = 0.62f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MintPill("Import .py", dark = false, onClick = { state.screen = Screen.Import })
                        MintPill("Settings", dark = true, onClick = { state.screen = Screen.Settings })
                    }
                    Spacer(Modifier.height(16.dp))
                    MiniTimelineMock()
                }
            }

            Spacer(Modifier.height(26.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Kicker("SAMPLE SCRIPTS", size = 10)
                Spacer(Modifier.weight(1f))
                XpText("${scripts.size} files", size = 11, color = Xp.inkMute)
            }
            Spacer(Modifier.height(10.dp))

            if (scripts.isEmpty()) {
                XpPanel(Modifier.fillMaxWidth(), color = Xp.paper, radius = 18.dp, shadow = 3.dp) {
                    Column(Modifier.padding(18.dp)) {
                        Body("Preparing the Python runtime…", size = 13)
                        Spacer(Modifier.height(6.dp))
                        Accent("first launch copies fonts and samples", size = 12)
                    }
                }
            } else {
                scripts.take(6).forEachIndexed { i, f ->
                    ScriptRow(
                        name = f.name.removeSuffix(".py"),
                        meta = "${f.length() / 1024} KB · .py",
                        accent = when (i % 3) {
                            0 -> Xp.mint
                            1 -> Xp.rose
                            else -> Xp.sky
                        },
                        modifier = Modifier.xpEnter(3 + i, rise = 14f),
                        onClick = { open(f) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(20.dp))
            HairLine(alpha = 0.1f)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Accent("minSdk 28 · Android 9+", size = 12, color = Xp.inkMute)
                Spacer(Modifier.weight(1f))
                XpText("v1.0", size = 11, color = Xp.inkMute)
            }
            Spacer(Modifier.height(30.dp))
        }

        if (state.loading) LoadingOverlay(state.loadNote)
    }
}

@Composable
fun MiniTimelineMock() {
    Column(Modifier.fillMaxWidth()) {
        listOf(0.62f, 0.34f, 0.48f).forEachIndexed { i, w ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Box(Modifier.width(26.dp).height(10.dp).clip(RoundedCornerShape(3.dp))
                    .background(Xp.cream.copy(alpha = 0.14f)))
                Spacer(Modifier.width(8.dp))
                Row(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth(w)
                            .height(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when (i) {
                                    0 -> Xp.mint
                                    1 -> Xp.rose
                                    else -> Xp.sky
                                }.copy(alpha = 0.85f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun ScriptRow(
    name: String,
    meta: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    XpPanel(modifier.fillMaxWidth(), color = Xp.paper, radius = 18.dp, shadow = 3.dp) {
        Row(
            Modifier.pressable(onClick).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(accent),
                contentAlignment = Alignment.Center
            ) {
                XpText("py", size = 13, weight = FontWeight.Bold, color = Xp.ink)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Title(name, size = 15)
                Spacer(Modifier.height(2.dp))
                XpText(meta, size = 11, color = Xp.inkMute)
            }
            XpText("›", size = 22, color = Xp.inkMute)
        }
    }
}

@Composable
fun LoadingOverlay(note: String = "") {
    Box(
        Modifier.fillMaxSize().background(Xp.cream.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Ring(0.72f, Modifier.size(58.dp), tint = Xp.ink, stroke = 6.dp)
            Spacer(Modifier.height(14.dp))
            Kicker("READING SCRIPT", size = 10)
            Spacer(Modifier.height(4.dp))
            Accent(note.ifEmpty { "auditing draw calls" }, size = 12)
        }
    }
}

@Composable
fun ImportScreen(state: XpState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var scripts by remember { mutableStateOf<List<File>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            scripts = withContext(Dispatchers.IO) {
                try {
                    XpPython.ensureInit(ctx)
                    XpPython.scripts()
                } catch (t: Throwable) {
                    emptyList()
                }
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun load(f: File) {
        state.loading = true
        state.loadNote = "auditing ${f.name}"
        scope.launch {
            val (scene, err) = withContext(Dispatchers.IO) { XpPython.load(f.absolutePath) }
            state.onLoaded(scene, err, f.absolutePath)
            if (scene == null) {
                error = err ?: "unknown error"
                Haptics.warn()
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                try {
                    val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
                    } ?: "imported.py"
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: ByteArray(0)
                    XpPython.ensureInit(ctx)
                    XpPython.importScript(ctx, name, bytes)
                } catch (t: Throwable) {
                    null
                }
            }
            if (file != null) {
                refresh()
                load(file)
            } else {
                error = "That file could not be read."
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Xp.ink.copy(alpha = 0.06f))
                        .pressable({ state.screen = Screen.Home }),
                    contentAlignment = Alignment.Center
                ) { XpText("‹", size = 20) }
                Spacer(Modifier.width(12.dp))
                Column {
                    XpText("Import", size = 26, weight = FontWeight.Bold)
                    Accent("a .py script with a render_frame(t, canvas)", size = 12)
                }
            }
            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MintPill("Pick a file", onClick = {
                    picker.launch(arrayOf("*/*"))
                })
                MintPill("Reload list", selected = false, onClick = { refresh() })
            }

            Spacer(Modifier.height(16.dp))

            error?.let {
                XpPanel(Modifier.fillMaxWidth(), color = Xp.rose.copy(alpha = 0.25f), radius = 16.dp) {
                    Column(Modifier.padding(14.dp)) {
                        XpText("Script error", size = 12, weight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        XpText(it.take(420), size = 11, color = Xp.ink.copy(alpha = 0.8f))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                scripts.forEachIndexed { i, f ->
                    ScriptRow(
                        name = f.name.removeSuffix(".py"),
                        meta = "${f.length() / 1024} KB",
                        accent = when (i % 3) {
                            0 -> Xp.mint
                            1 -> Xp.rose
                            else -> Xp.sky
                        },
                        modifier = Modifier.xpEnter(i, rise = 12f, delayMs = 20L),
                        onClick = { load(f) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(24.dp))
                XpPanel(Modifier.fillMaxWidth(), color = Xp.paper, radius = 18.dp) {
                    Column(Modifier.padding(16.dp)) {
                        Kicker("SCRIPT CONTRACT", size = 9)
                        Spacer(Modifier.height(8.dp))
                        XpText(
                            "DURATION = 6.0\nFPS = 30\n\ndef render_frame(t, c):\n    c.bg(\"#F2F0DC\")\n    c.ptag(\"HELLO\", 0.5, 0.5, name=\"title\")",
                            size = 11, color = Xp.ink.copy(alpha = 0.78f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Accent("cv2 and ffmpeg imports are shimmed by XP", size = 11)
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }

        if (state.loading) LoadingOverlay(state.loadNote)
    }
}
