package ai.xp.editor.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.nio.ByteBuffer

/**
 * Disk LRU of 540p scrub frames. Scrubbing a Python render at 60Hz is not
 * possible, so every half second is pre-rendered once and reused; the head of
 * the timeline is always warm.
 */
class ScrubCache(private val dir: File, private val maxBytes: Long = 160L * 1024 * 1024) {

    init {
        dir.mkdirs()
    }

    private fun fileFor(t: Float, tag: String): File =
        File(dir, "s_${tag}_%05d.jpg".format((t * 2f).toInt().coerceAtLeast(0)))

    fun get(t: Float, tag: String): Bitmap? {
        val f = fileFor(t, tag)
        if (!f.exists()) return null
        return try {
            f.setLastModified(System.currentTimeMillis())
            BitmapFactory.decodeFile(f.absolutePath)
        } catch (t2: Throwable) {
            null
        }
    }

    fun put(t: Float, tag: String, w: Int, h: Int): Bitmap? {
        val f = fileFor(t, tag)
        if (f.exists() && f.length() > 0) return get(t, tag)
        val ok = XpPython.renderJpeg(t, w, h, f.absolutePath)
        if (!ok) return null
        trim()
        return try {
            BitmapFactory.decodeFile(f.absolutePath)
        } catch (t2: Throwable) {
            null
        }
    }

    fun size(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun trim() {
        var total = size()
        if (total <= maxBytes) return
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        for (f in files) {
            if (total <= maxBytes * 8 / 10) break
            total -= f.length()
            f.delete()
        }
    }
}

/**
 * Preview renderer. One dedicated thread owns the Python call; the UI only
 * ever reads the latest ImageBitmap, so a slow frame never blocks the frame
 * clock of the rest of the app.
 */
class PreviewEngine(private val ctx: Context) {

    val frame = mutableStateOf<ImageBitmap?>(null)
    val playhead = mutableStateOf(0f)
    val playing = mutableStateOf(false)
    val renderMs = mutableStateOf(0)
    val renderedFrames = mutableStateOf(0)

    var duration: Float = 8f
    var sceneFps: Float = 30f
    var previewW: Int = 720
    var previewH: Int = 1280
    var targetFps: Int = 30
    var loop: Boolean = true
    var useScrubCache: Boolean = true

    val scrub = ScrubCache(File(ctx.cacheDir, "xp/scrub"))

    @Volatile private var running = false
    @Volatile private var dirty = true
    @Volatile private var paused = false
    private var thread: Thread? = null
    private var tag = "720"

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "xp-preview").also {
            it.priority = Thread.NORM_PRIORITY + 1
            it.start()
        }
    }

    fun stop() {
        running = false
        thread?.join(1200)
        thread = null
    }

    /** Export takes the Python lock — pause preview so it does not fight. */
    fun setPaused(p: Boolean) {
        paused = p
    }

    fun seek(t: Float) {
        playhead.value = t.coerceIn(0f, duration)
        dirty = true
    }

    fun togglePlay() {
        playing.value = !playing.value
        if (playing.value && playhead.value >= duration - 0.01f) playhead.value = 0f
        dirty = true
    }

    fun invalidate() {
        dirty = true
        scrub.clear()
    }

    fun setPreviewSize(w: Int, h: Int, tagName: String) {
        previewW = w
        previewH = h
        tag = tagName
        dirty = true
    }

    private fun loop() {
        while (running) {
            val start = System.nanoTime()
            try {
                if (paused) {
                    Thread.sleep(80)
                    continue
                }
                if (playing.value) {
                    val step = 1f / targetFps.toFloat()
                    var next = playhead.value + step
                    if (next >= duration) {
                        if (loop) {
                            next = 0f
                        } else {
                            next = duration
                            playing.value = false
                        }
                    }
                    playhead.value = next
                    dirty = true
                }
                if (dirty) {
                    render(playhead.value, playing.value)
                    dirty = false
                }
                val spent = (System.nanoTime() - start) / 1_000_000L
                val budget = 1000L / targetFps.coerceIn(12, 60)
                val rest = budget - spent
                Thread.sleep(if (rest > 2) rest else 2)
            } catch (_: InterruptedException) {
                return
            } catch (t: Throwable) {
                try {
                    Thread.sleep(60)
                } catch (_: InterruptedException) {
                    return
                }
            }
        }
    }

    private fun render(t: Float, live: Boolean) {
        val t0 = System.nanoTime()
        var bmp: Bitmap? = null
        if (!live && useScrubCache) {
            // scrubbing reads the 540p cache; a miss renders and stores it
            bmp = scrub.get(t, tag) ?: scrub.put(t, tag, previewW / 2, previewH / 2)
        }
        if (bmp == null) {
            val bytes = XpPython.renderFrame(t, previewW, previewH)
            if (bytes != null && bytes.size >= previewW * previewH * 4) {
                bmp = Bitmap.createBitmap(previewW, previewH, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
            }
        }
        if (bmp != null) {
            frame.value = bmp.asImageBitmap()
            renderedFrames.value = renderedFrames.value + 1
            renderMs.value = ((System.nanoTime() - t0) / 1_000_000L).toInt()
        }
    }

    companion object {
        fun bitmapFromRgba(bytes: ByteArray, w: Int, h: Int): Bitmap? {
            if (bytes.size < w * h * 4) return null
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
            return bmp
        }
    }
}
