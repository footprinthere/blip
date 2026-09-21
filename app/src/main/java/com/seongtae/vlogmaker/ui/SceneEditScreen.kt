package com.seongtae.vlogmaker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.seongtae.vlogmaker.model.LayoutTemplate
import com.seongtae.vlogmaker.model.MediaType
import com.seongtae.vlogmaker.model.formatSec
import com.seongtae.vlogmaker.ui.theme.Gray1
import com.seongtae.vlogmaker.ui.theme.Gray5
import com.seongtae.vlogmaker.ui.theme.White

@Composable
fun SceneEditScreen(vm: EditorViewModel, index: Int) {
    val project = vm.project
    val scene = project.scenes.getOrNull(index)
    if (scene == null) {
        LaunchedEffect(Unit) { vm.screen = Screen.Editor }
        return
    }
    var pendingSlot by remember { mutableIntStateOf(-1) }
    var menuSlot by remember { mutableIntStateOf(-1) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && pendingSlot >= 0) vm.fillSlot(index, pendingSlot, uri)
        pendingSlot = -1
    }
    val pickInto = { slot: Int ->
        pendingSlot = slot
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar(title = "장면 ${index + 1}", onBack = { vm.screen = Screen.Editor }) {
            IconButton(onClick = { vm.deleteScene(index); vm.screen = Screen.Editor }) {
                Icon(Icons.Outlined.Delete, contentDescription = "장면 삭제")
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            ScenePreview(
                project, index, vm.previews,
                Modifier.fillMaxHeight().pointerInput(scene) {
                    detectTapGestures { offset ->
                        val nx = offset.x / size.width
                        val ny = offset.y / size.height
                        val cell = scene.template.cells.indexOfFirst { it.contains(nx, ny) }
                        if (cell >= 0) {
                            if (scene.slots.getOrNull(cell)?.media == null) pickInto(cell) else menuSlot = cell
                        }
                    }
                },
                showCaptions = false,
            )
        }
        Text(
            "빈 칸을 누르면 미디어를 넣고, 채운 칸을 누르면 편집합니다",
            Modifier.fillMaxWidth(), color = Gray5, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
        )

        LazyRow(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(LayoutTemplate.entries) { t ->
                TemplateIcon(t, selected = t == scene.template, onClick = { vm.setTemplate(index, t) })
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.setUnits(index, scene.units - 1) }, enabled = scene.units > 1) {
                Icon(Icons.Outlined.Remove, contentDescription = "짧게")
            }
            Text(
                "${formatSec(project.sceneDurationMs(scene))}   (${scene.units} × ${formatSec(project.unitMs)})",
                Modifier.width(200.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge,
            )
            IconButton(onClick = { vm.setUnits(index, scene.units + 1) }) {
                Icon(Icons.Outlined.Add, contentDescription = "길게")
            }
        }
    }

    if (menuSlot >= 0) {
        val slot = scene.slots.getOrNull(menuSlot)
        val isVideo = slot?.media?.type == MediaType.VIDEO
        Dialog(onDismissRequest = { menuSlot = -1 }) {
            Surface(color = Gray1, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(8.dp)) {
                    if (isVideo) {
                        TextButton(onClick = { vm.screen = Screen.Trim(index, menuSlot); menuSlot = -1 }, Modifier.fillMaxWidth()) {
                            Text("구간 자르기", color = White)
                        }
                    }
                    TextButton(onClick = { val s = menuSlot; menuSlot = -1; pickInto(s) }, Modifier.fillMaxWidth()) {
                        Text("다른 미디어로 교체", color = White)
                    }
                    TextButton(onClick = { vm.clearSlot(index, menuSlot); menuSlot = -1 }, Modifier.fillMaxWidth()) {
                        Text("비우기", color = Gray5)
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateIcon(template: LayoutTemplate, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) White else Gray5
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Canvas(Modifier.size(width = 36.dp, height = 64.dp)) {
            val gap = 2.dp.toPx()
            val stroke = Stroke(width = if (selected) 2.dp.toPx() else 1.dp.toPx())
            template.cells.forEach { c ->
                val left = c.left * size.width + gap / 2
                val top = c.top * size.height + gap / 2
                val w = c.width() * size.width - gap
                val h = c.height() * size.height - gap
                drawRect(color, Offset(left, top), Size(w, h), style = stroke)
            }
        }
        Text(template.label, color = color, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}
