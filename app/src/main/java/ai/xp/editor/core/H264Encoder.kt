package ai.xp.editor.core

import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * H.264 encoder over MediaCodec, fed straight from ARGB bitmaps.
 *
 * The Python runtime produces RGBA frames; this converts them to the encoder's
 * YUV420 plane layout (planar or semi-planar, whichever the device offers) and
 * muxes to MP4. No ffmpeg binary, no native build — the hardware encoder does
 * 1080p faster than software x264 ever would on a phone.
 */
class H264Encoder(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val bitrate: Int,
    private val out: File
) {
    private lateinit var codec: MediaCodec
    private lateinit var muxer: MediaMuxer
    private var trackIndex = -1
    private var started = false
    private val info = MediaCodec.BufferInfo()
    private val pixels = IntArray(width * height)
    private var frames = 0

    fun start() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    fun encode(bitmap: Bitmap) {
        val index = codec.dequeueInputBuffer(20_000)
        if (index < 0) {
            drain(false)
            return
        }
        val pts = (frames * 1_000_000L) / fps
        val image: Image? = try {
            codec.getInputImage(index)
        } catch (t: Throwable) {
            null
        }
        if (image != null) {
            fillImage(image, bitmap)
            codec.queueInputBuffer(index, 0, width * height * 3 / 2, pts, 0)
        } else {
            val buf = codec.getInputBuffer(index)
            if (buf != null) {
                buf.clear()
                fillBuffer(buf, bitmap)
                codec.queueInputBuffer(index, 0, width * height * 3 / 2, pts, 0)
            } else {
                codec.queueInputBuffer(index, 0, 0, pts, 0)
            }
        }
        frames++
        drain(false)
    }

    fun finish() {
        try {
            val index = codec.dequeueInputBuffer(50_000)
            if (index >= 0) {
                codec.queueInputBuffer(
                    index, 0, 0,
                    (frames * 1_000_000L) / fps,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }
            drain(true)
        } catch (_: Throwable) {
        }
        try {
            codec.stop()
        } catch (_: Throwable) {
        }
        try {
            codec.release()
        } catch (_: Throwable) {
        }
        try {
            if (started) muxer.stop()
        } catch (_: Throwable) {
        }
        try {
            muxer.release()
        } catch (_: Throwable) {
        }
    }

    private fun drain(eos: Boolean) {
        while (true) {
            val index = try {
                codec.dequeueOutputBuffer(info, if (eos) 20_000 else 0)
            } catch (t: Throwable) {
                return
            }
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!started) {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        started = true
                    }
                }
                index >= 0 -> {
                    val buf = codec.getOutputBuffer(index)
                    if (buf != null) {
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && started) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, buf, info)
                        }
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    // ------------------------------------------------------------- pixel paths
    private fun fillImage(image: Image, bitmap: Bitmap) {
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val y = image.planes[0]
        val u = image.planes[1]
        val v = image.planes[2]
        val yb = y.buffer
        val ub = u.buffer
        val vb = v.buffer
        val yRow = y.rowStride
        val yPix = y.pixelStride

        for (row in 0 until height) {
            var offset = row * yRow
            var idx = row * width
            for (col in 0 until width) {
                val c = pixels[idx]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val luma = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yb.put(offset, luma.coerceIn(0, 255).toByte())
                offset += yPix
                idx++
            }
        }

        val uvH = height / 2
        val uvW = width / 2
        for (row in 0 until uvH) {
            var offU = row * u.rowStride
            var offV = row * v.rowStride
            for (col in 0 until uvW) {
                val i1 = (row * 2) * width + col * 2
                val i2 = i1 + 1
                val i3 = i1 + width
                val i4 = i3 + 1
                val c1 = pixels[i1]
                val c2 = pixels[i2]
                val c3 = pixels[i3]
                val c4 = pixels[i4]
                val r = (((c1 shr 16) and 0xFF) + ((c2 shr 16) and 0xFF) + ((c3 shr 16) and 0xFF) + ((c4 shr 16) and 0xFF)) shr 2
                val g = (((c1 shr 8) and 0xFF) + ((c2 shr 8) and 0xFF) + ((c3 shr 8) and 0xFF) + ((c4 shr 8) and 0xFF)) shr 2
                val b = ((c1 and 0xFF) + (c2 and 0xFF) + (c3 and 0xFF) + (c4 and 0xFF)) shr 2
                val uu = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val vv = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                ub.put(offU, uu.coerceIn(0, 255).toByte())
                vb.put(offV, vv.coerceIn(0, 255).toByte())
                offU += u.pixelStride
                offV += v.pixelStride
            }
        }
    }

    /** NV12 fallback for devices that hand back a plain byte buffer. */
    private fun fillBuffer(buf: ByteBuffer, bitmap: Bitmap) {
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val ySize = width * height
        buf.position(0)
        for (i in 0 until ySize) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            buf.put((((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255).toByte())
        }
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val i1 = (row * 2) * width + col * 2
                val c1 = pixels[i1]
                val c2 = pixels[i1 + 1]
                val c3 = pixels[i1 + width]
                val c4 = pixels[i1 + width + 1]
                val r = (((c1 shr 16) and 0xFF) + ((c2 shr 16) and 0xFF) + ((c3 shr 16) and 0xFF) + ((c4 shr 16) and 0xFF)) shr 2
                val g = (((c1 shr 8) and 0xFF) + ((c2 shr 8) and 0xFF) + ((c3 shr 8) and 0xFF) + ((c4 shr 8) and 0xFF)) shr 2
                val b = ((c1 and 0xFF) + (c2 and 0xFF) + (c3 and 0xFF) + (c4 and 0xFF)) shr 2
                buf.put((((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte())
                buf.put((((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte())
            }
        }
    }
}
