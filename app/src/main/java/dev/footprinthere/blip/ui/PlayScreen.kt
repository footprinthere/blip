package dev.footprinthere.blip.ui

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import dev.footprinthere.blip.fonts.Fonts
import dev.footprinthere.blip.model.MediaType
import dev.footprinthere.blip.model.Project
import dev.footprinthere.blip.model.Scene
import dev.footprinthere.blip.model.formatClock
import dev.footprinthere.blip.render.SceneRenderer
import dev.footprinthere.blip.ui.theme.Gray2
import dev.footprinthere.blip.ui.theme.Gray5
import dev.footprinthere.blip.ui.theme.White
import kotlin.math.roundToInt

/**
 * 내보내기 전에 전체 흐름을 이어서 재생한다. 영상 칸은 플레이어로 실제 재생하고,
 * 그 아래에는 정지 프레임을, 위에는 자막을 겹쳐 내보내기 결과와 같은 모습을 만든다.
 */
@Composable
fun PlayScreen(vm: EditorViewModel) {
    val project = vm.project
    if (project.scenes.isEmpty()) {
        LaunchedEffect(Unit) { vm.screen = Screen.Editor }
        return
    }
    val context = LocalContext.current
    val players = remember { ScenePlayers(context) }
    DisposableEffect(Unit) { onDispose { players.releaseAll() } }

    var sceneIndex by remember { mutableIntStateOf(0) }
    var sceneElapsed by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    val scene = project.scenes[sceneIndex]

    val fontIds = project.captions.map { it.fontId }.distinct()
    LaunchedEffect(fontIds) { fontIds.forEach { Fonts.load(context, it) } }

    // 지금 장면과 다음 장면의 플레이어만 준비해 둔다. 다음 장면을 미리 열어 두어야 넘어갈 때 끊기지 않는다.
    LaunchedEffect(sceneIndex) {
        players.retain(project, setOf(sceneIndex, sceneIndex + 1))
    }
    LaunchedEffect(sceneIndex, playing) {
        val current = players.forScene(project, sceneIndex)
        if (sceneElapsed == 0L) current.forEachIndexed { k, p -> p?.seekTo(scene.slots[k].trimStartMs) }
        current.forEach { it?.playWhenReady = playing }
        if (!playing) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            sceneElapsed += now - last
            last = now
            if (sceneElapsed >= project.sceneDurationMs(scene)) {
                if (sceneIndex < project.scenes.lastIndex) {
                    current.forEach { it?.pause() }
                    sceneElapsed = 0L
                    sceneIndex++
                } else {
                    sceneElapsed = project.sceneDurationMs(scene)
                    playing = false
                }
                break
            }
        }
    }

    val passedMs = project.scenes.take(sceneIndex).sumOf { project.sceneDurationMs(it) }
    val positionMs = passedMs + sceneElapsed
    val ended = !playing && sceneIndex == project.scenes.lastIndex && sceneElapsed >= project.sceneDurationMs(scene)
    val onTap = {
        if (ended) {
            players.retain(project, setOf(0, 1))
            sceneElapsed = 0L
            sceneIndex = 0
            playing = true
        } else {
            playing = !playing
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar(title = "미리보기", onBack = { vm.screen = Screen.Editor })

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxHeight().aspectRatio(project.aspectRatio).clipToBounds().clickable(onClick = onTap)) {
                ScenePreview(project, sceneIndex, vm.previews, Modifier.fillMaxSize(), showCaptions = false)
                VideoLayer(project, scene, players.forScene(project, sceneIndex))
                CaptionLayer(project, sceneIndex)
            }
        }

        LinearProgressIndicator(
            progress = { (positionMs.toFloat() / project.totalMs.coerceAtLeast(1L)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 12.dp).height(2.dp),
            color = White, trackColor = Gray2, gapSize = 0.dp, drawStopIndicator = {},
        )
        Text(
            "${formatClock(positionMs)} / ${formatClock(project.totalMs)}   ·   장면 ${sceneIndex + 1} / ${project.scenes.size}",
            Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            when {
                ended -> "화면을 누르면 처음부터 다시 재생"
                playing -> "화면을 누르면 일시정지"
                else -> "화면을 누르면 이어서 재생"
            },
            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), color = Gray5, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** 영상 칸마다 플레이어 화면을 칸을 덮는 크기로 놓는다. 크기와 위치 계산은 SceneRenderer 의 drawCover 와 같다. */
@OptIn(UnstableApi::class)
@Composable
private fun VideoLayer(project: Project, scene: Scene, players: List<ExoPlayer?>) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        scene.template.cells.forEachIndexed { i, cell ->
            val slot = scene.slots.getOrNull(i) ?: return@forEachIndexed
            val media = slot.media ?: return@forEachIndexed
            val player = players.getOrNull(i) ?: return@forEachIndexed
            if (media.width <= 0 || media.height <= 0) return@forEachIndexed
            val cellW = cell.width() * w
            val cellH = cell.height() * h
            val scale = SceneRenderer.coverScale(media.width, media.height, cellW, cellH, slot.zoom)
            val sw = (media.width * scale).roundToInt()
            val sh = (media.height * scale).roundToInt()
            val left = (cellW / 2f + slot.offsetX * cellW - sw / 2f).roundToInt()
            val top = (cellH / 2f + slot.offsetY * cellH - sh / 2f).roundToInt()
            Box(
                with(density) {
                    Modifier
                        .offset(x = (cell.left * w).toDp(), y = (cell.top * h).toDp())
                        .size(cellW.toDp(), cellH.toDp())
                        .clipToBounds()
                },
            ) {
                PlayerSurface(
                    player = player,
                    modifier = Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(Constraints.fixed(sw, sh))
                        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(left, top) }
                    },
                    surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                )
            }
        }
    }
}

@Composable
private fun CaptionLayer(project: Project, sceneIndex: Int) {
    val captions = project.captionsAt(sceneIndex)
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width.toInt()
        val h = size.height.toInt()
        if (w <= 0 || h <= 0) return@Canvas
        drawIntoCanvas { SceneRenderer.drawCaptions(it.nativeCanvas, w, h, captions, Fonts::get) }
    }
}

/** 장면 번호별로 칸마다 하나씩 만든 소리 없는 플레이어. 사진이나 빈 칸은 null. */
private class ScenePlayers(private val context: Context) {
    private val byScene = HashMap<String, List<ExoPlayer?>>()

    fun forScene(project: Project, index: Int): List<ExoPlayer?> {
        val scene = project.scenes.getOrNull(index) ?: return emptyList()
        return byScene.getOrPut(scene.id) {
            scene.slots.map { slot ->
                val media = slot.media?.takeIf { it.type == MediaType.VIDEO } ?: return@map null
                ExoPlayer.Builder(context).build().apply {
                    volume = 0f
                    setMediaItem(MediaItem.fromUri(media.uri))
                    seekTo(slot.trimStartMs)
                    prepare()
                }
            }
        }
    }

    fun retain(project: Project, indices: Set<Int>) {
        val keep = indices.mapNotNull { project.scenes.getOrNull(it)?.id }.toSet()
        byScene.keys.filter { it !in keep }.forEach { id -> byScene.remove(id)?.forEach { it?.release() } }
        indices.forEach { forScene(project, it) }
    }

    fun releaseAll() {
        byScene.values.forEach { list -> list.forEach { it?.release() } }
        byScene.clear()
    }
}
