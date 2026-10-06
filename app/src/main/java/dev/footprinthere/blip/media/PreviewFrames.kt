package dev.footprinthere.blip.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import dev.footprinthere.blip.model.MediaItem
import dev.footprinthere.blip.model.MediaType
import dev.footprinthere.blip.model.Scene
import dev.footprinthere.blip.model.Slot
import dev.footprinthere.blip.render.FrameSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/** 화면 미리보기용 축소 프레임 캐시. 영상은 잘라낸 시작 지점의 정지 프레임을 쓴다. */
class PreviewFrames(private val context: Context) {
    private val cache = object : LruCache<String, Bitmap>(96 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** 미디어마다 마지막으로 읽어 둔 프레임의 키. 구간을 바꾼 직후 새 프레임을 읽는 동안 칸이 비어 보이지 않게 대신 그린다. */
    private val latestKey = ConcurrentHashMap<Uri, String>()

    private fun key(media: MediaItem, timeMs: Long) = "${media.uri}#$timeMs"
    private fun timeFor(slot: Slot) = if (slot.media?.type == MediaType.VIDEO) slot.trimStartMs else 0L

    fun cached(slot: Slot): Bitmap? = slot.media?.let { media ->
        cache.get(key(media, timeFor(slot))) ?: latestKey[media.uri]?.let { cache.get(it) }
    }

    val source: FrameSource = FrameSource { _, slot, _ -> cached(slot) }

    suspend fun preload(scene: Scene) {
        for (slot in scene.slots) slot.media?.let { load(it, timeFor(slot)) }
    }

    /** 직전 화면(구간 선택)의 플레이어가 디코더를 아직 놓지 않았으면 실패할 수 있어, 잠시 기다렸다 다시 시도한다. */
    suspend fun load(media: MediaItem, timeMs: Long): Bitmap? = withContext(Dispatchers.IO) {
        val k = key(media, timeMs)
        cache.get(k)?.let { return@withContext it }
        for (attempt in 0 until 3) {
            if (attempt > 0) delay(300L * attempt)
            val bmp = runCatching { decode(media, timeMs) }.getOrNull() ?: continue
            cache.put(k, bmp)
            latestKey[media.uri] = k
            return@withContext bmp
        }
        null
    }

    private fun decode(media: MediaItem, timeMs: Long): Bitmap? = when (media.type) {
        MediaType.PHOTO -> MediaLoader.decodePhoto(context, media.uri, MAX_PX)
        MediaType.VIDEO -> withRetriever(media) { mmr ->
            scaledFrame(mmr, media, timeMs, MAX_PX, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: scaledFrame(mmr, media, timeMs, MAX_PX, MediaMetadataRetriever.OPTION_PREVIOUS_SYNC)
        }
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
