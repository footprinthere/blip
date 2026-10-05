package dev.footprinthere.blip.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.footprinthere.blip.fonts.Fonts
import dev.footprinthere.blip.media.PreviewFrames
import dev.footprinthere.blip.model.Project
import dev.footprinthere.blip.render.SceneRenderer
import dev.footprinthere.blip.ui.theme.Gray1

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로") }
        } else {
            Spacer(Modifier.padding(start = 8.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/** 장면 하나를 비율 그대로 그린 정지 미리보기. 캐시된 프레임을 바로 그려서 편집 조작에 즉시 반응한다. */
@Composable
fun ScenePreview(
    project: Project,
    sceneIndex: Int,
    previews: PreviewFrames,
    modifier: Modifier = Modifier,
    showCaptions: Boolean = true,
) {
    val scene = project.scenes.getOrNull(sceneIndex)
    val captions = if (showCaptions && scene != null) project.captionsAt(sceneIndex) else emptyList()
    val context = LocalContext.current
    val fontIds = captions.map { it.fontId }.distinct()
    LaunchedEffect(fontIds) { fontIds.forEach { Fonts.load(context, it) } }

    var loaded by remember { mutableIntStateOf(0) }
    val mediaKey = scene?.slots?.map { it.media?.uri to it.trimStartMs }
    LaunchedEffect(mediaKey) {
        if (scene != null) {
            previews.preload(scene)
            loaded++
        }
    }
    Canvas(modifier.aspectRatio(project.aspectRatio).clipToBounds().background(Gray1)) {
        @Suppress("UNUSED_EXPRESSION") loaded
        if (scene == null) return@Canvas
        val w = size.width.toInt()
        val h = size.height.toInt()
        if (w <= 0 || h <= 0) return@Canvas
        drawIntoCanvas { c ->
            SceneRenderer.drawScene(c.nativeCanvas, w, h, scene, captions, previews.source, 0L, Fonts::get, placeholders = true)
        }
    }
}
