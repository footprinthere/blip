package com.seongtae.vlogmaker.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seongtae.vlogmaker.fonts.Fonts
import com.seongtae.vlogmaker.media.PreviewFrames
import com.seongtae.vlogmaker.model.Project
import com.seongtae.vlogmaker.render.SceneRenderer
import com.seongtae.vlogmaker.ui.theme.Gray1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

/** 장면 하나를 비율 그대로 그린 정지 미리보기. 크기에 맞춰 부모 안에 들어간다. */
@Composable
fun ScenePreview(
    project: Project,
    sceneIndex: Int,
    previews: PreviewFrames,
    modifier: Modifier = Modifier,
    showCaptions: Boolean = true,
    renderWidth: Int = 540,
) {
    val scene = project.scenes.getOrNull(sceneIndex)
    val captions = if (showCaptions && scene != null) project.captionsAt(sceneIndex) else emptyList()
    val context = LocalContext.current
    val fontIds = captions.map { it.fontId }.distinct()
    LaunchedEffect(fontIds) { fontIds.forEach { Fonts.load(context, it) } }
    val fontKey = fontIds.map { Fonts.typefaces[it] }
    val renderHeight = (renderWidth / project.aspectRatio).toInt()

    val bitmap by produceState<ImageBitmap?>(null, scene, captions, fontKey, renderWidth) {
        if (scene == null) {
            value = null
            return@produceState
        }
        previews.preload(scene)
        value = withContext(Dispatchers.Default) {
            val bmp = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
            SceneRenderer.drawScene(Canvas(bmp), renderWidth, renderHeight, scene, captions, previews.source, 0L, Fonts::get, placeholders = true)
            bmp.asImageBitmap()
        }
    }
    Box(modifier.aspectRatio(project.aspectRatio).background(Gray1)) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}
