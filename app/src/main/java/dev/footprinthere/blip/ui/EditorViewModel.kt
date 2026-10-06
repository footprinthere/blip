package dev.footprinthere.blip.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.footprinthere.blip.export.VideoExporter
import dev.footprinthere.blip.media.MediaLoader
import dev.footprinthere.blip.media.PreviewFrames
import dev.footprinthere.blip.model.Caption
import dev.footprinthere.blip.model.LayoutTemplate
import dev.footprinthere.blip.model.MediaItem
import dev.footprinthere.blip.model.MediaType
import dev.footprinthere.blip.model.Project
import dev.footprinthere.blip.model.Scene
import dev.footprinthere.blip.model.Slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Editor : Screen
    data class SceneEdit(val index: Int, val slot: Int = -1) : Screen
    data class Trim(val sceneIndex: Int, val slotIndex: Int) : Screen
    data class CaptionEdit(val id: String) : Screen
    data object Play : Screen
    data object Export : Screen
}

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val progress: Float) : ExportState
    data class Done(val uri: Uri) : ExportState
    data class Failed(val message: String) : ExportState
}

class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val context get() = getApplication<Application>()

    var project by mutableStateOf(Project())
        private set
    var screen by mutableStateOf<Screen>(Screen.Editor)
    var selectedScene by mutableIntStateOf(0)
    var loading by mutableIntStateOf(0)
        private set
    var exportState by mutableStateOf<ExportState>(ExportState.Idle)
    val previews = PreviewFrames(app)
    private var exportJob: Job? = null

    fun addMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            loading++
            try {
                val items = uris.mapNotNull { MediaLoader.load(context, it) }
                val wasEmpty = project.scenes.isEmpty()
                project = project.copy(scenes = project.scenes + items.map { newScene(it) })
                if (wasEmpty) selectedScene = 0
            } finally {
                loading--
            }
        }
    }

    private fun newScene(media: MediaItem): Scene {
        val units = if (media.type == MediaType.VIDEO) (media.durationMs / project.unitMs).toInt().coerceIn(1, 3) else 2
        return Scene(slots = listOf(Slot(media)), units = units)
    }

    fun setUnit(ms: Long) {
        val updated = project.copy(unitMs = ms)
        project = updated.copy(scenes = updated.scenes.map { it.fitTrims(updated.sceneDurationMs(it)) })
    }

    fun moveScene(from: Int, to: Int) {
        val list = project.scenes.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        project = project.copy(scenes = list)
        selectedScene = to
    }

    fun deleteScene(index: Int) {
        if (index !in project.scenes.indices) return
        val scenes = project.scenes.filterIndexed { i, _ -> i != index }
        val captions = project.captions.mapNotNull { c ->
            if (c.startScene == index && c.endScene == index) return@mapNotNull null
            val start = if (c.startScene > index) c.startScene - 1 else c.startScene
            val end = if (c.endScene >= index) c.endScene - 1 else c.endScene
            c.copy(startScene = start.coerceIn(0, scenes.lastIndex), endScene = end.coerceIn(0, scenes.lastIndex))
        }
        project = project.copy(scenes = scenes, captions = if (scenes.isEmpty()) emptyList() else captions)
        selectedScene = selectedScene.coerceIn(0, (scenes.size - 1).coerceAtLeast(0))
    }

    private fun updateScene(index: Int, transform: (Scene) -> Scene) {
        project = project.copy(scenes = project.scenes.mapIndexed { i, s -> if (i == index) transform(s) else s })
    }

    fun setTemplate(index: Int, template: LayoutTemplate) = updateScene(index) { s ->
        s.copy(template = template, slots = List(template.slotCount) { k -> s.slots.getOrNull(k) ?: Slot() })
    }

    fun setUnits(index: Int, units: Int) = updateScene(index) { s ->
        val resized = s.copy(units = units.coerceIn(1, 120))
        resized.fitTrims(project.sceneDurationMs(resized))
    }

    /** 장면이 길어지면 잘라 둔 시작 지점 뒤로 영상이 모자랄 수 있다. 그러면 남은 시간이 마지막 프레임으로 멈추므로 시작 지점을 앞으로 당긴다. */
    private fun Scene.fitTrims(durationMs: Long): Scene = copy(slots = slots.map { slot ->
        val media = slot.media
        if (media?.type != MediaType.VIDEO) slot
        else slot.copy(trimStartMs = slot.trimStartMs.coerceIn(0L, (media.durationMs - durationMs).coerceAtLeast(0L)))
    })

    fun fillSlot(sceneIndex: Int, slotIndex: Int, uri: Uri) {
        viewModelScope.launch {
            loading++
            try {
                val media = MediaLoader.load(context, uri) ?: return@launch
                updateScene(sceneIndex) { s ->
                    s.copy(slots = s.slots.mapIndexed { k, slot -> if (k == slotIndex) Slot(media) else slot })
                }
            } finally {
                loading--
            }
        }
    }

    fun clearSlot(sceneIndex: Int, slotIndex: Int) = updateScene(sceneIndex) { s ->
        s.copy(slots = s.slots.mapIndexed { k, slot -> if (k == slotIndex) Slot() else slot })
    }

    fun updateSlot(sceneIndex: Int, slotIndex: Int, transform: (Slot) -> Slot) = updateScene(sceneIndex) { s ->
        s.copy(slots = s.slots.mapIndexed { k, slot -> if (k == slotIndex) transform(slot) else slot })
    }

    fun setTrim(sceneIndex: Int, slotIndex: Int, trimStartMs: Long) =
        updateSlot(sceneIndex, slotIndex) { it.copy(trimStartMs = trimStartMs) }

    fun addCaption(): String {
        val at = selectedScene.coerceIn(0, (project.scenes.size - 1).coerceAtLeast(0))
        val caption = Caption(startScene = at, endScene = at)
        project = project.copy(captions = project.captions + caption)
        return caption.id
    }

    /** 위치·색·글꼴·크기는 그대로 두고 내용만 비운 자막을 다음 장면에 만든다. 여러 장면에 같은 자리로 자막을 넣을 때 쓴다. */
    fun duplicateCaption(id: String): String? {
        val source = project.captions.find { it.id == id } ?: return null
        val last = project.scenes.lastIndex
        val next = (source.endScene + 1).coerceAtMost(last)
        val copy = source.copy(id = java.util.UUID.randomUUID().toString(), text = "", startScene = next, endScene = next)
        project = project.copy(captions = project.captions + copy)
        return copy.id
    }

    /** 기준 자막의 선택한 속성을 나머지 모든 자막에 복사한다. */
    fun applyCaptionStyleToAll(id: String, position: Boolean, size: Boolean, color: Boolean, font: Boolean) {
        val source = project.captions.find { it.id == id } ?: return
        project = project.copy(captions = project.captions.map { c ->
            if (c.id == id) c else c.copy(
                x = if (position) source.x else c.x,
                y = if (position) source.y else c.y,
                size = if (size) source.size else c.size,
                color = if (color) source.color else c.color,
                fontId = if (font) source.fontId else c.fontId,
            )
        })
    }

    fun updateCaption(id: String, transform: (Caption) -> Caption) {
        project = project.copy(captions = project.captions.map { if (it.id == id) transform(it) else it })
    }

    fun deleteCaption(id: String) {
        project = project.copy(captions = project.captions.filterNot { it.id == id })
    }

    fun startExport() {
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            exportState = ExportState.Running(0f)
            try {
                val uri = VideoExporter(context).export(project) { exportState = ExportState.Running(it) }
                exportState = ExportState.Done(uri)
            } catch (e: CancellationException) {
                exportState = ExportState.Idle
                throw e
            } catch (e: Throwable) {
                exportState = ExportState.Failed(e.message ?: e.toString())
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        exportState = ExportState.Idle
    }
}
