package dev.footprinthere.blip.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import dev.footprinthere.blip.fonts.Fonts
import dev.footprinthere.blip.model.Project
import dev.footprinthere.blip.render.SceneRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoExporter(private val context: Context) {

    /** 프로젝트를 mp4 로 만들어 갤러리(Movies/Blip)에 저장하고 그 Uri 를 돌려준다. */
    suspend fun export(project: Project, onProgress: (Float) -> Unit): Uri = withContext(Dispatchers.Default) {
        require(project.scenes.isNotEmpty()) { "장면이 없습니다" }
        for (id in project.captions.map { it.fontId }.distinct()) Fonts.load(context, id)

        val w = project.width
        val h = project.height
        val fps = project.fps
        val frameDurUs = 1_000_000L / fps
        val totalFrames = project.scenes.sumOf { framesFor(project, it) }.coerceAtLeast(1)

        val tmp = File(context.cacheDir, "export_${System.currentTimeMillis()}.mp4")
        val encoder = FrameEncoder(w, h, fps, bitrate = 12_000_000, outputPath = tmp.absolutePath)
        val frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)
        var frameIndex = 0L
        try {
            project.scenes.forEachIndexed { sceneIndex, scene ->
                val captions = project.captionsAt(sceneIndex)
                ExportFrameSource(context, scene, w, h).use { frames ->
                    val n = framesFor(project, scene)
                    for (f in 0 until n) {
                        coroutineContext.ensureActive()
                        val t = f * 1000L / fps
                        SceneRenderer.drawScene(canvas, w, h, scene, captions, frames, t, Fonts::get, placeholders = false)
                        encoder.encodeFrame(frame, frameIndex * frameDurUs)
                        frameIndex++
                        onProgress(frameIndex.toFloat() / totalFrames)
                    }
                }
            }
            encoder.finish()
            saveToGallery(tmp)
        } catch (e: Throwable) {
            encoder.release()
            throw e
        } finally {
            frame.recycle()
            tmp.delete()
        }
    }

    private fun framesFor(project: Project, scene: dev.footprinthere.blip.model.Scene): Int =
        (project.sceneDurationMs(scene) * project.fps / 1000L).toInt().coerceAtLeast(1)

    private fun saveToGallery(file: File): Uri {
        val name = "Blip_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Blip")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: error("갤러리에 항목을 만들 수 없습니다")
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("파일을 쓸 수 없습니다")
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Throwable) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }
}
