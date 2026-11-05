package ai.xp.editor.ui

import android.content.ContentValues
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import ai.xp.editor.core.ExportWorker
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
import ai.xp.editor.design.Segmented
import ai.xp.editor.design.Title
import ai.xp.editor.design.Xp
import ai.xp.editor.design.XpPanel
import ai.xp.editor.design.XpSlider
import ai.xp.editor.design.XpText
import ai.xp.editor.design.pressable
import ai.xp.editor.design.xpEnter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ExportScreen(state: XpState) {
    val ctx = LocalContext.current
    val scene = state.scene ?: return

    var pct by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var workId by remember { mutableStateOf<java.util.UUID?>(null) }

    var audioName by remember { mutableStateOf<String?>(null) }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // content:// URIs cannot be opened by MediaExtractor directly — copy once
        val copied = try {
            val dir = File(ctx.cacheDir, "xp/audio").apply { mkdirs() }
            val out = File(dir, "soundtrack_${System.currentTimeMillis()}.bin")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            if (out.length() > 0) out else null
        } catch (t: Throwable) {
            null
        }
        if (copied != null) {
            state.audioPath = copied.absolutePath
            audioName = copied.name
            Haptics.success()
        }
    }

    LaunchedEffect(workId) {
        val id = workId ?: return@LaunchedEffect
        while (true) {
            val info = withContext(Dispatchers.IO) {
                try {
                    WorkManager.getInstance(ctx).getWorkInfoById(id).get()
                } catch (t: Throwable) {
                    null
                }
            }
            if (info != null) {
                pct = info.progress.getInt(ExportWorker.KEY_PCT, pct)
                when (info.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        output = info.outputData.getString(ExportWorker.KEY_PATH)
                        state.lastExport = output
                        running = false
                        Haptics.success()
                        break
                    }
                    WorkInfo.State.FAILED -> {
                        error = info.outputData.getString(ExportWorker.KEY_ERROR) ?: "export failed"
                        running = false
                        Haptics.warn()
                        break
                    }
                    WorkInfo.State.CANCELLED -> {
                        running = false
                        break
                    }
                    else -> Unit
                }
            }
            delay(450)
        }
    }

    fun startExport() {
        val w = state.exportWidth
        val h = (w.toFloat() * scene.height / scene.width).toInt().let { it - (it % 2) }
        val fps = state.exportFps
        val bitrate = (w * h * fps * 0.085f * (0.6f + state.exportQuality)).toInt()
            .coerceIn(2_000_000, 26_000_000)

        val data = ExportWorker.request(
            script = scene.path,
            editsJson = state.editsJson(),
            w = w, h = h, fps = fps, bitrate = bitrate,
            duration = scene.duration,
            name = scene.name,
            audio = state.audioPath
        )
        val req = OneTimeWorkRequestBuilder<ExportWorker>()
            .setInputData(data)
            .build()
        WorkManager.getInstance(ctx).enqueue(req)
        workId = req.id
        running = true
        pct = 0
        output = null
        error = null
        state.engine.setPaused(true)
        Haptics.tap()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Xp.ink.copy(alpha = 0.06f))
                    .pressable({
                        state.engine.setPaused(false)
                        state.screen = Screen.Editor
                    }),
                contentAlignment = Alignment.Center
            ) { XpText("‹", size = 20) }
            Spacer(Modifier.width(12.dp))
            Column {
                XpText("Export", size = 26, weight = FontWeight.Bold)
                Accent("1080p · H.264 · hardware encoder", size = 12)
            }
        }

        Spacer(Modifier.height(18.dp))

        XpPanel(Modifier.fillMaxWidth().xpEnter(0), color = Xp.paper, radius = 22.dp, shadow = 4.dp) {
            Column(Modifier.padding(16.dp)) {
                Kicker("RESOLUTION", size = 9)
                Spacer(Modifier.height(8.dp))
                Segmented(
                    items = listOf("540p", "720p", "1080p"),
                    selected = when (state.exportWidth) {
                        540 -> 0
                        720 -> 1
                        else -> 2
                    },
                    onSelect = {
                        state.exportWidth = when (it) {
                            0 -> 540
                            1 -> 720
                            else -> 1080
                        }
                    }
                )
                Spacer(Modifier.height(14.dp))
                Kicker("FRAME RATE", size = 9)
                Spacer(Modifier.height(8.dp))
                Segmented(
                    items = listOf("24", "30", "60"),
                    selected = when (state.exportFps) {
                        24 -> 0
                        60 -> 2
                        else -> 1
                    },
                    onSelect = {
                        state.exportFps = when (it) {
                            0 -> 24
                            2 -> 60
                            else -> 30
                        }
                    }
                )
                Spacer(Modifier.height(14.dp))
                XpSlider(
                    value = state.exportQuality,
                    onValueChange = { state.exportQuality = it },
                    range = 0f..1f,
                    label = "Quality",
                    format = { "${(it * 100).toInt()}%" }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        XpPanel(Modifier.fillMaxWidth().xpEnter(1), color = Xp.paper, radius = 22.dp, shadow = 4.dp) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Title("Soundtrack", size = 14)
                        XpText(
                            state.audioPath?.let { audioName ?: "attached" } ?: "none — video only",
                            size = 11, color = Xp.inkMute, maxLines = 1
                        )
                    }
                    MintPill("Pick", selected = state.audioPath == null, onClick = {
                        audioPicker.launch(arrayOf("audio/*"))
                    })
                }
                if (state.audioPath != null) {
                    Spacer(Modifier.height(8.dp))
                    MintPill("Remove", selected = false, onClick = { state.audioPath = null })
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        if (running || output != null || error != null) {
            XpPanel(Modifier.fillMaxWidth(), color = Xp.ink, radius = 24.dp, shadow = 5.dp) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                        Ring(
                            progress = pct / 100f,
                            modifier = Modifier.fillMaxSize(),
                            tint = Xp.mint,
                            track = Xp.cream.copy(alpha = 0.15f),
                            stroke = 7.dp
                        )
                        XpText("$pct%", size = 20, color = Xp.cream, weight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    XpText(
                        when {
                            error != null -> "Export failed"
                            output != null -> "Saved"
                            else -> "Rendering at ${state.exportWidth}p"
                        },
                        size = 14, color = Xp.cream, weight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    XpText(
                        error ?: output?.substringAfterLast('/') ?: "keep the app open",
                        size = 11, color = Xp.cream.copy(alpha = 0.6f), maxLines = 2
                    )
                    if (output != null) {
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MintPill("Share", onClick = {
                                share(ctx, File(output!!))
                            })
                            MintPill("To gallery", dark = true, onClick = {
                                if (copyToGallery(ctx, File(output!!)) != null) Haptics.success()
                            })
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        if (!running && output == null) {
            InkButton(
                text = "Export ${state.exportWidth}p · ${state.exportFps}fps",
                modifier = Modifier.fillMaxWidth().xpEnter(2),
                onClick = { startExport() }
            )
        }

        Spacer(Modifier.height(14.dp))
        HairLine(alpha = 0.1f)
        Spacer(Modifier.height(12.dp))
        Body(
            "Frames are rendered by your script, converted to YUV and handed to MediaCodec. " +
                "Expect roughly 1-3 frames per second of export on a 2 GB phone.",
            size = 11
        )
        Spacer(Modifier.height(30.dp))
    }
}

private fun resolveToFile(uriString: String): File? {
    return try {
        val uri = Uri.parse(uriString)
        if (uri.scheme == "file") File(uri.path ?: "") else null
    } catch (t: Throwable) {
        null
    }
}

private fun share(ctx: android.content.Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Share video"))
    } catch (t: Throwable) {
    }
}

private fun copyToGallery(ctx: android.content.Context, file: File): Uri? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/XP")
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            uri
        } else {
            MediaScannerConnection.scanFile(ctx, arrayOf(file.absolutePath), arrayOf("video/mp4"), null)
            Uri.fromFile(file)
        }
    } catch (t: Throwable) {
        null
    }
}
