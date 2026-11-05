package ai.xp.editor.core

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Puts a soundtrack back onto the exported video without re-encoding either
 * side: both tracks are copied sample-for-sample. If anything about the audio
 * file is odd, the video-only file is kept.
 */
object Remuxer {

    fun mux(video: File, audio: File?, out: File): Boolean {
        if (audio == null || !audio.exists()) return false
        var videoEx: MediaExtractor? = null
        var audioEx: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        return try {
            videoEx = MediaExtractor().apply { setDataSource(video.absolutePath) }
            val vIdx = firstTrack(videoEx, "video/")
            if (vIdx < 0) return false
            videoEx.selectTrack(vIdx)

            audioEx = MediaExtractor().apply { setDataSource(audio.absolutePath) }
            val aIdx = firstTrack(audioEx, "audio/")
            if (aIdx < 0) return false
            audioEx.selectTrack(aIdx)

            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val vTrack = muxer.addTrack(videoEx.getTrackFormat(vIdx))
            val aTrack = muxer.addTrack(audioEx.getTrackFormat(aIdx))
            muxer.start()

            val buf = ByteBuffer.allocateDirect(1 shl 20)
            val info = MediaCodec.BufferInfo()

            // interleave by presentation time
            var vDone = false
            var aDone = false
            while (!vDone || !aDone) {
                val vTime = if (vDone) Long.MAX_VALUE else videoEx.sampleTime
                val aTime = if (aDone) Long.MAX_VALUE else audioEx.sampleTime
                if (vTime == Long.MAX_VALUE && aTime == Long.MAX_VALUE) break
                val useVideo = vTime <= aTime
                val ex = if (useVideo) videoEx else audioEx
                if (useVideo && vTime < 0) { vDone = true; continue }
                if (!useVideo && aTime < 0) { aDone = true; continue }
                buf.clear()
                val size = ex.readSampleData(buf, 0)
                if (size <= 0) {
                    if (useVideo) vDone = true else aDone = true
                    continue
                }
                info.offset = 0
                info.size = size
                info.presentationTimeUs = ex.sampleTime
                info.flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                    MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                muxer.writeSampleData(if (useVideo) vTrack else aTrack, buf, info)
                ex.advance()
            }
            muxer.stop()
            true
        } catch (t: Throwable) {
            try {
                muxer?.stop()
            } catch (_: Throwable) {
            }
            false
        } finally {
            try {
                videoEx?.release()
            } catch (_: Throwable) {
            }
            try {
                audioEx?.release()
            } catch (_: Throwable) {
            }
            try {
                muxer?.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun firstTrack(ex: MediaExtractor, prefix: String): Int {
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(prefix)) return i
        }
        return -1
    }
}
