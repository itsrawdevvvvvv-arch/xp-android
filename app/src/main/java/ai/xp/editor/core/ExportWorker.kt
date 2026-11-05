package ai.xp.editor.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 1080p export. Runs off the UI thread as a foreground worker: render one
 * frame in Python, hand it to the hardware encoder, repeat, mux.
 */
class ExportWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val script = inputData.getString(KEY_SCRIPT) ?: return@withContext Result.failure()
        val width = inputData.getInt(KEY_W, 1080)
        val height = inputData.getInt(KEY_H, 1920)
        val fps = inputData.getInt(KEY_FPS, 30).coerceIn(12, 60)
        val bitrate = inputData.getInt(KEY_BITRATE, 8_000_000)
        val duration = inputData.getFloat(KEY_DURATION, 8f)
        val editsJson = inputData.getString(KEY_EDITS) ?: "{}"
        val audioPath = inputData.getString(KEY_AUDIO)?.takeIf { it.isNotEmpty() }
        val name = inputData.getString(KEY_NAME) ?: "XP"

        try {
            setForegroundQuietly(0)
        } catch (_: Throwable) {
        }

        var encoder: H264Encoder? = null
        var tmp: File? = null
        try {
            XpPython.ensureInit(applicationContext)
            val scene = XpPython.load(script).first ?: return@withContext Result.failure(
                workDataOf(KEY_ERROR to "script failed to load")
            )
            XpPython.applyEditsRaw(editsJson)

            val movies = applicationContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: applicationContext.filesDir
            movies.mkdirs()
            val safe = name.replace(Regex("[^A-Za-z0-9 _-]"), "_").take(40).ifEmpty { "XP" }
            val finalFile = File(movies, "XP_${safe}_${System.currentTimeMillis() / 1000}.mp4")
            val tmpFile = File(applicationContext.cacheDir, "xp_export_tmp.mp4")
            tmp = tmpFile
            if (tmpFile.exists()) tmpFile.delete()

            val enc = H264Encoder(width, height, fps, bitrate, tmpFile)
            encoder = enc
            enc.start()

            val total = (duration * fps).toInt().coerceAtLeast(1)
            for (i in 0 until total) {
                if (isStopped) break
                val t = i.toFloat() / fps
                val bytes = XpPython.renderFrame(t, width, height)
                if (bytes != null) {
                    val bmp = PreviewEngine.bitmapFromRgba(bytes, width, height)
                    if (bmp != null) {
                        enc.encode(bmp)
                        bmp.recycle()
                    }
                }
                if (i % 3 == 0) {
                    setProgress(workDataOf(KEY_PCT to (i * 100 / total), KEY_FRAME to i, KEY_TOTAL to total))
                    try {
                        setForegroundQuietly(i * 100 / total)
                    } catch (_: Throwable) {
                    }
                }
            }
            enc.finish()
            encoder = null

            val audio = audioPath?.let { File(it) }
            val muxed = if (audio != null && audio.exists()) {
                Remuxer.mux(tmpFile, audio, finalFile)
            } else false
            if (!muxed) {
                tmpFile.copyTo(finalFile, overwrite = true)
            }
            tmpFile.delete()

            setProgress(workDataOf(KEY_PCT to 100, KEY_FRAME to total, KEY_TOTAL to total))
            Result.success(workDataOf(KEY_PATH to finalFile.absolutePath, KEY_PCT to 100))
        } catch (t: Throwable) {
            try {
                encoder?.finish()
            } catch (_: Throwable) {
            }
            Result.failure(workDataOf(KEY_ERROR to (t.message ?: t.javaClass.simpleName)))
        }
    }

    private suspend fun setForegroundQuietly(pct: Int) {
        try {
            setForeground(createForegroundInfo(pct))
        } catch (_: Throwable) {
        }
    }

    private fun createForegroundInfo(pct: Int): ForegroundInfo {
        val ctx = applicationContext
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "Export", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
        val notification: Notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setContentTitle("Rendering at 1080p")
            .setContentText(if (pct in 1..99) "$pct%" else "Starting encoder")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setProgress(100, pct.coerceIn(0, 100), pct <= 0)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIF_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, notification)
        }
    }

    companion object {
        const val CHANNEL = "xp_export"
        const val NOTIF_ID = 4211
        const val KEY_SCRIPT = "script"
        const val KEY_EDITS = "edits"
        const val KEY_W = "w"
        const val KEY_H = "h"
        const val KEY_FPS = "fps"
        const val KEY_BITRATE = "bitrate"
        const val KEY_DURATION = "duration"
        const val KEY_AUDIO = "audio"
        const val KEY_NAME = "name"
        const val KEY_PCT = "pct"
        const val KEY_FRAME = "frame"
        const val KEY_TOTAL = "total"
        const val KEY_PATH = "path"
        const val KEY_ERROR = "error"

        fun request(
            script: String, editsJson: String, w: Int, h: Int, fps: Int,
            bitrate: Int, duration: Float, name: String, audio: String?
        ): Data = workDataOf(
            KEY_SCRIPT to script,
            KEY_EDITS to editsJson,
            KEY_W to w,
            KEY_H to h,
            KEY_FPS to fps,
            KEY_BITRATE to bitrate,
            KEY_DURATION to duration,
            KEY_NAME to name,
            KEY_AUDIO to (audio ?: "")
        )
    }
}
