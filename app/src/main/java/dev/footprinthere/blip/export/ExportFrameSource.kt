package dev.footprinthere.blip.export

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import dev.footprinthere.blip.media.MediaLoader
import dev.footprinthere.blip.model.MediaItem
import dev.footprinthere.blip.model.MediaType
import dev.footprinthere.blip.model.Scene
import dev.footprinthere.blip.model.Slot
import dev.footprinthere.blip.render.FrameSource
import java.io.Closeable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private interface SlotFrames : Closeable {
    fun frame(sceneTimeMs: Long): Bitmap?
}

/** 내보내기용 프레임 공급자. 장면 하나에 대해 슬롯별 디코더를 연다. */
class ExportFrameSource(context: Context, scene: Scene, outW: Int, outH: Int) : FrameSource, Closeable {
    private val slots: List<SlotFrames?> = scene.template.cells.mapIndexed { i, cell ->
        val slot = scene.slots.getOrNull(i)
        val media = slot?.media ?: return@mapIndexed null
        val cellW = (cell.width() * outW * slot.zoom).roundToInt().coerceAtLeast(1)
        val cellH = (cell.height() * outH * slot.zoom).roundToInt().coerceAtLeast(1)
        runCatching {
            when (media.type) {
                MediaType.PHOTO -> PhotoFrames(MediaLoader.decodePhotoCover(context, media.uri, cellW, cellH))
                MediaType.VIDEO -> SequentialVideoFrames(context, media, slot.trimStartMs, cellW, cellH)
            }
        }.getOrNull()
    }

    override fun frame(slotIndex: Int, slot: Slot, sceneTimeMs: Long): Bitmap? = slots.getOrNull(slotIndex)?.frame(sceneTimeMs)

    override fun close() {
        slots.forEach { runCatching { it?.close() } }
    }
}

private class PhotoFrames(private val bitmap: Bitmap) : SlotFrames {
    override fun frame(sceneTimeMs: Long): Bitmap = bitmap
    override fun close() = bitmap.recycle()
}

/**
 * 영상 프레임을 시간 순서대로 꺼낸다. getFramesAtIndex 로 여러 장을 한 번에 디코딩해 두고
 * 셀 크기로 줄여 메모리를 아낀다. 영상이 장면보다 짧으면 마지막 프레임을 유지한다.
 */
private class SequentialVideoFrames(
    context: Context, private val media: MediaItem, private val startMs: Long, private val cellW: Int, private val cellH: Int,
) : SlotFrames {
    private val mmr = MediaMetadataRetriever().apply { setDataSource(context, media.uri) }
    private val frameCount = media.frameCount.coerceAtLeast(1)
    private val srcFps = if (media.frameRate > 1f) media.frameRate else 30f
    private val params = MediaMetadataRetriever.BitmapParams().apply { preferredConfig = Bitmap.Config.RGB_565 }
    private val chunkSize: Int = run {
        val frameBytes = max(1, media.width) * max(1, media.height) * 2L
        (48L * 1024 * 1024 / frameBytes).toInt().coerceIn(2, 16)
    }
    private var chunkStart = 0
    private var chunk = ArrayList<Bitmap?>()
    private var lastIndex = -1
    private var lastBitmap: Bitmap? = null
    private var indexMode = true

    override fun frame(sceneTimeMs: Long): Bitmap? {
        val t = startMs + sceneTimeMs
        var index = (t * srcFps / 1000f).toInt().coerceIn(0, frameCount - 1)
        if (index < lastIndex) index = lastIndex
        if (index == lastIndex) return lastBitmap
        val bmp = if (indexMode) byIndex(index) else null
        val result = bmp ?: byTime(t) ?: lastBitmap
        lastIndex = index
        if (result !== lastBitmap) {
            lastBitmap?.takeIf { it !in chunk }?.recycle()
            lastBitmap = result
        }
        return result
    }

    private fun byIndex(index: Int): Bitmap? {
        if (index < chunkStart || index >= chunkStart + chunk.size) {
            if (!loadChunk(index)) return null
        }
        val pos = index - chunkStart
        for (i in 0 until pos) chunk[i]?.let { if (it !== lastBitmap) it.recycle() }.also { chunk[i] = null }
        return chunk.getOrNull(pos)
    }

    private fun loadChunk(index: Int): Boolean {
        chunk.forEach { if (it != null && it !== lastBitmap) it.recycle() }
        chunk = ArrayList()
        chunkStart = index
        val n = min(chunkSize, frameCount - index)
        if (n <= 0) return false
        val frames = runCatching { mmr.getFramesAtIndex(index, n, params) }.getOrNull()
        if (frames.isNullOrEmpty()) {
            indexMode = false
            return false
        }
        frames.forEach { chunk.add(shrink(it)) }
        return true
    }

    private fun byTime(timeMs: Long): Bitmap? {
        val us = min(timeMs, max(0L, media.durationMs - 1)) * 1000
        val bmp = runCatching { mmr.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST) }.getOrNull() ?: return null
        return shrink(bmp)
    }

    private fun shrink(bmp: Bitmap): Bitmap {
        val s = max(cellW / bmp.width.toFloat(), cellH / bmp.height.toFloat())
        if (s >= 0.9f) return bmp
        val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * s).roundToInt().coerceAtLeast(1), (bmp.height * s).roundToInt().coerceAtLeast(1), true)
        if (scaled !== bmp) bmp.recycle()
        return scaled
    }

    override fun close() {
        chunk.forEach { it?.recycle() }
        chunk.clear()
        lastBitmap?.recycle()
        lastBitmap = null
        mmr.release()
    }
}
