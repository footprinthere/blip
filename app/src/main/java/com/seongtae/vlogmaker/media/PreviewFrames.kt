package com.seongtae.vlogmaker.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.LruCache
import com.seongtae.vlogmaker.model.MediaItem
import com.seongtae.vlogmaker.model.MediaType
import com.seongtae.vlogmaker.model.Scene
import com.seongtae.vlogmaker.model.Slot
import com.seongtae.vlogmaker.render.FrameSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/** 화면 미리보기용 축소 프레임 캐시. 영상은 잘라낸 시작 지점의 정지 프레임을 쓴다. */
class PreviewFrames(private val context: Context) {
    private val cache = object : LruCache<String, Bitmap>(96 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun key(media: MediaItem, timeMs: Long) = "${media.uri}#$timeMs"
    private fun timeFor(slot: Slot) = if (slot.media?.type == MediaType.VIDEO) slot.trimStartMs else 0L

    fun cached(slot: Slot): Bitmap? = slot.media?.let { cache.get(key(it, timeFor(slot))) }

    val source: FrameSource = FrameSource { _, slot, _ -> cached(slot) }

    suspend fun preload(scene: Scene) {
        for (slot in scene.slots) slot.media?.let { load(it, timeFor(slot)) }
    }

    suspend fun load(media: MediaItem, timeMs: Long): Bitmap? = withContext(Dispatchers.IO) {
        val k = key(media, timeMs)
        cache.get(k) ?: runCatching { decode(media, timeMs) }.getOrNull()?.also { cache.put(k, it) }
    }

    private fun decode(media: MediaItem, timeMs: Long): Bitmap? = when (media.type) {
        MediaType.PHOTO -> MediaLoader.decodePhoto(context, media.uri, MAX_PX)
        MediaType.VIDEO -> withRetriever(media) { mmr -> scaledFrame(mmr, media, timeMs, MAX_PX, MediaMetadataRetriever.OPTION_CLOSEST) }
    }

    /** 구간 선택 화면의 필름 스트립용 작은 프레임들. */
    suspend fun filmstrip(media: MediaItem, count: Int): List<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            withRetriever(media) { mmr ->
                (0 until count).mapNotNull { i ->
                    val t = (media.durationMs * (i + 0.5) / count).toLong()
                    scaledFrame(mmr, media, t, 200, MediaMetadataRetriever.OPTION_CLOSEST)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun <T> withRetriever(media: MediaItem, block: (MediaMetadataRetriever) -> T): T {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, media.uri)
            return block(mmr)
        } finally {
            mmr.release()
        }
    }

    private fun scaledFrame(mmr: MediaMetadataRetriever, media: MediaItem, timeMs: Long, maxPx: Int, option: Int): Bitmap? {
        val us = timeMs * 1000
        if (media.width <= 0 || media.height <= 0) return mmr.getFrameAtTime(us, option)
        val s = maxPx / max(media.width, media.height).toFloat()
        val dw = if (s < 1f) (media.width * s).toInt() else media.width
        val dh = if (s < 1f) (media.height * s).toInt() else media.height
        return mmr.getScaledFrameAtTime(us, option, dw.coerceAtLeast(1), dh.coerceAtLeast(1))
    }

    companion object {
        const val MAX_PX = 720
    }
}
