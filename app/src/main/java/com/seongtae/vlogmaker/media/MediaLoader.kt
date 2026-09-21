package com.seongtae.vlogmaker.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.seongtae.vlogmaker.model.MediaItem
import com.seongtae.vlogmaker.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

object MediaLoader {
    suspend fun load(context: Context, uri: Uri): MediaItem? = withContext(Dispatchers.IO) {
        runCatching {
            val mime = context.contentResolver.getType(uri) ?: ""
            if (mime.startsWith("video/")) loadVideo(context, uri) else loadPhoto(context, uri)
        }.getOrNull()
    }

    private fun loadPhoto(context: Context, uri: Uri): MediaItem {
        var w = 0
        var h = 0
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            w = info.size.width
            h = info.size.height
            decoder.setTargetSampleSize(32)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }.recycle()
        return MediaItem(uri, MediaType.PHOTO, w, h)
    }

    private fun loadVideo(context: Context, uri: Uri): MediaItem {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)
            fun meta(key: Int) = mmr.extractMetadata(key)
            val duration = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            var w = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) { val t = w; w = h; h = t }
            val frames = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull()
            val fps = when {
                frames != null && frames > 0 && duration > 0 -> frames * 1000f / duration
                else -> meta(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 30f
            }
            val frameCount = frames ?: (duration * fps / 1000f).toInt()
            return MediaItem(uri, MediaType.VIDEO, w, h, duration, fps, frameCount)
        } finally {
            mmr.release()
        }
    }

    /** 사진을 소프트웨어 비트맵으로 읽는다. 긴 변이 maxPx를 넘으면 줄인다. */
    fun decodePhoto(context: Context, uri: Uri, maxPx: Int): Bitmap {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val longSide = max(info.size.width, info.size.height)
            if (longSide > maxPx) {
                val s = maxPx / longSide.toFloat()
                decoder.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    /** 사진을 cellW×cellH 를 덮을 수 있는 최소 크기로 읽는다 (내보내기용). */
    fun decodePhotoCover(context: Context, uri: Uri, cellW: Int, cellH: Int): Bitmap {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val s = max(cellW / info.size.width.toFloat(), cellH / info.size.height.toFloat())
            if (s < 1f) {
                decoder.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
}
