package dev.footprinthere.blip.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import dev.footprinthere.blip.model.MediaType
import dev.footprinthere.blip.model.formatClock
import dev.footprinthere.blip.model.formatSec
import dev.footprinthere.blip.ui.theme.Black
import dev.footprinthere.blip.ui.theme.Gray1
import dev.footprinthere.blip.ui.theme.Gray5
import dev.footprinthere.blip.ui.theme.White
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun TrimScreen(vm: EditorViewModel, sceneIndex: Int, slotIndex: Int) {
    val project = vm.project
    val scene = project.scenes.getOrNull(sceneIndex)
    val slot = scene?.slots?.getOrNull(slotIndex)
    val media = slot?.media
    if (scene == null || media == null || media.type != MediaType.VIDEO) {
        LaunchedEffect(Unit) { vm.screen = Screen.SceneEdit(sceneIndex) }
        return
    }
    val context = LocalContext.current
    val windowMs = project.sceneDurationMs(scene)
    val maxStart = (media.durationMs - windowMs).coerceAtLeast(0L)
    var start by remember { mutableLongStateOf(slot.trimStartMs.coerceIn(0L, maxStart)) }
    var playing by remember { mutableStateOf(true) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            setMediaItem(MediaItem.fromUri(media.uri))
            prepare()
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    // 선택 구간 안에서만 반복 재생한다.
    LaunchedEffect(start, playing) {
        player.seekTo(start)
        player.playWhenReady = playing
        while (playing) {
            delay(40)
            if (player.currentPosition >= start + windowMs || player.playbackState == Player.STATE_ENDED) player.seekTo(start)
        }
    }

    var strip by remember { mutableStateOf<List<android.graphics.Bitmap>>(emptyList()) }
    LaunchedEffect(media.uri) { strip = vm.previews.filmstrip(media, 10) }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar(title = "구간 선택", onBack = { vm.screen = Screen.SceneEdit(sceneIndex) }) {
            IconButton(onClick = { vm.setTrim(sceneIndex, slotIndex, start); vm.screen = Screen.SceneEdit(sceneIndex) }) {
                Icon(Icons.Outlined.Check, contentDescription = "확인")
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            val ratio = if (media.width > 0 && media.height > 0) media.width.toFloat() / media.height else 9f / 16f
            Box(Modifier.aspectRatio(ratio).background(Black).clickable { playing = !playing }) {
                PlayerSurface(player = player, modifier = Modifier.fillMaxSize())
            }
        }

        Text(
            "${formatClock(start)} – ${formatClock(start + windowMs)}   ·   ${formatSec(windowMs)}",
            Modifier.fillMaxWidth(), color = White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
        )
        if (media.durationMs < windowMs) {
            Text(
                "영상이 장면보다 짧아 끝부분은 마지막 프레임으로 채워집니다",
                Modifier.fillMaxWidth().padding(top = 4.dp), color = Gray5, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp).height(64.dp)) {
            val widthPx = constraints.maxWidth.toFloat()
            val duration = media.durationMs.coerceAtLeast(1L)
            val winPx = (windowMs.toFloat() / duration * widthPx).coerceIn(12f, widthPx)
            val xPx = (start.toFloat() / duration * widthPx).coerceIn(0f, widthPx - winPx)
            val density = LocalDensity.current

            Row(Modifier.fillMaxSize().background(Gray1)) {
                strip.forEach { bmp ->
                    Image(bmp.asImageBitmap(), contentDescription = null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                }
            }
            Box(
                Modifier
                    .offset { IntOffset(xPx.roundToInt(), 0) }
                    .width(with(density) { winPx.toDp() })
                    .fillMaxHeight()
                    .border(2.dp, White),
            )
            Box(
                Modifier.fillMaxSize().pointerInput(maxStart, widthPx) {
                    var wasPlaying = playing
                    detectHorizontalDragGestures(
                        onDragStart = { wasPlaying = playing; playing = false },
                        onDragEnd = { playing = wasPlaying },
                        onDragCancel = { playing = wasPlaying },
                    ) { change, dx ->
                        change.consume()
                        start = (start + (dx / widthPx * duration).toLong()).coerceIn(0L, maxStart)
                    }
                },
            )
        }
        Text(
            "흰 상자를 좌우로 끌어 시작 지점을 정하세요. 화면을 누르면 재생/정지",
            Modifier.fillMaxWidth().padding(bottom = 16.dp), color = Gray5, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
