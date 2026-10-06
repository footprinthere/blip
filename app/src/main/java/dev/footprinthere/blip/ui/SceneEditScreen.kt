package dev.footprinthere.blip.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculateCentroid
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
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.SwapHoriz
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.footprinthere.blip.model.LayoutTemplate
import dev.footprinthere.blip.model.MediaType
import dev.footprinthere.blip.model.formatSec
import dev.footprinthere.blip.render.SceneRenderer
import dev.footprinthere.blip.ui.theme.Gray3
import dev.footprinthere.blip.ui.theme.Gray5
import dev.footprinthere.blip.ui.theme.White

@Composable
fun SceneEditScreen(vm: EditorViewModel, index: Int, initialSlot: Int = -1) {
    val project = vm.project
    val scene = project.scenes.getOrNull(index)
    if (scene == null) {
        LaunchedEffect(Unit) { vm.screen = Screen.Editor }
        return
    }
    var pendingSlot by remember { mutableIntStateOf(-1) }
    var selectedSlot by remember(scene.id) { mutableIntStateOf(initialSlot.takeIf { it >= 0 } ?: scene.slots.indexOfFirst { it.media != null }.coerceAtLeast(0)) }
    if (selectedSlot >= scene.template.slotCount) selectedSlot = 0
    val selected = scene.slots.getOrNull(selectedSlot)
    val multiSlot = scene.template.slotCount > 1
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && pendingSlot >= 0) vm.fillSlot(index, pendingSlot, uri)
        pendingSlot = -1
    }
    val pickInto = { slot: Int ->
        pendingSlot = slot
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar(title = "장면 ${index + 1}", onBack = { vm.screen = Screen.Editor }) {
            IconButton(onClick = { vm.deleteScene(index); vm.screen = Screen.Editor }) {
                Icon(Icons.Outlined.Delete, contentDescription = "장면 삭제")
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            ScenePreview(
                project, index, vm.previews,
                Modifier
                    .fillMaxHeight()
                    .drawWithContent {
                        drawContent()
                        val cell = scene.template.cells.getOrNull(selectedSlot)
                        if (multiSlot && cell != null) {
                            val stroke = 2.dp.toPx()
                            drawRect(
                                White,
                                Offset(cell.left * size.width + stroke / 2, cell.top * size.height + stroke / 2),
                                Size(cell.width() * size.width - stroke, cell.height() * size.height - stroke),
                                style = Stroke(stroke),
                            )
                        }
                    }
                    .pointerInput(scene.id, scene.template) {
                        detectTapGestures { offset ->
                            val cell = cellAt(scene, offset.x / size.width, offset.y / size.height)
                            if (cell >= 0) {
                                selectedSlot = cell
                                if (vm.project.scenes.getOrNull(index)?.slots?.getOrNull(cell)?.media == null) pickInto(cell)
                            }
                        }
                    }
                    .pointerInput(scene.id, scene.template) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val cellIndex = cellAt(scene, down.position.x / size.width, down.position.y / size.height)
                            val current = vm.project.scenes.getOrNull(index)?.slots?.getOrNull(cellIndex) ?: return@awaitEachGesture
                            if (current.media == null) return@awaitEachGesture
                            val bmp = vm.previews.cached(current) ?: return@awaitEachGesture
                            val cell = scene.template.cells[cellIndex]
                            val cellW = cell.width() * size.width
                            val cellH = cell.height() * size.height
                            val cellCenter = Offset(cell.centerX() * size.width, cell.centerY() * size.height)
                            val threshold = 10.dp.toPx()

                            var zoom = current.zoom
                            var rawX = current.offsetX * cellW
                            var rawY = current.offsetY * cellH
                            var snappedX = 0
                            var snappedY = 0
                            var pastSlop = false
                            var travelled = Offset.Zero

                            // 한계값(=이미지 경계가 칸 경계에 닿는 위치) 근처면 붙인다. 0 은 스냅 없음, ±1 은 어느 쪽 경계인지.
                            fun snap(raw: Float, limit: Float): Pair<Float, Int> = when {
                                limit < threshold -> raw to 0
                                raw > limit - threshold -> limit to 1
                                raw < -limit + threshold -> -limit to -1
                                else -> raw to 0
                            }

                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                val zoomChange = event.calculateZoom()
                                val pan = event.calculatePan()
                                if (pressed.size >= 2) pastSlop = true
                                if (!pastSlop) {
                                    travelled += pan
                                    if (travelled.getDistance() > viewConfiguration.touchSlop) pastSlop = true
                                }
                                if (pastSlop && (zoomChange != 1f || pan != Offset.Zero)) {
                                    val newZoom = (zoom * zoomChange).coerceIn(1f, 4f)
                                    val applied = newZoom / zoom
                                    if (applied != 1f) {
                                        // 두 손가락 가운데 지점 아래의 이미지가 고정되도록 중심을 보정한다.
                                        val pivot = event.calculateCentroid() - cellCenter
                                        rawX = pivot.x + (rawX - pivot.x) * applied
                                        rawY = pivot.y + (rawY - pivot.y) * applied
                                    }
                                    zoom = newZoom
                                    rawX += pan.x
                                    rawY += pan.y
                                    val (limX, limY) = SceneRenderer.maxOffsetPx(bmp.width, bmp.height, cellW, cellH, zoom)
                                    rawX = rawX.coerceIn(-limX, limX)
                                    rawY = rawY.coerceIn(-limY, limY)
                                    val (sx, stateX) = snap(rawX, limX)
                                    val (sy, stateY) = snap(rawY, limY)
                                    if ((stateX != 0 && stateX != snappedX) || (stateY != 0 && stateY != snappedY)) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    snappedX = stateX
                                    snappedY = stateY
                                    vm.updateSlot(index, cellIndex) { it.copy(zoom = zoom, offsetX = sx / cellW, offsetY = sy / cellH) }
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
                showCaptions = false,
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
            val hasMedia = selected?.media != null
            SlotAction(Icons.Outlined.ContentCut, "구간 자르기", enabled = selected?.media?.type == MediaType.VIDEO) {
                vm.screen = Screen.Trim(index, selectedSlot)
            }
            SlotAction(Icons.Outlined.SwapHoriz, if (hasMedia) "교체" else "넣기") { pickInto(selectedSlot) }
            SlotAction(Icons.Outlined.HideImage, "비우기", enabled = hasMedia) { vm.clearSlot(index, selectedSlot) }
        }
        val media = selected?.media
        val hint = if (media?.type == MediaType.VIDEO && media.durationMs - selected.trimStartMs < project.sceneDurationMs(scene)) {
            "영상이 장면보다 짧아 끝부분은 마지막 프레임으로 멈춥니다"
        } else if (multiSlot) {
            "칸을 눌러 고른 뒤 아래 버튼으로 편집합니다. 끌거나 두 손가락으로 벌려 위치와 크기를 조정합니다"
        } else {
            "끌거나 두 손가락으로 벌려 위치와 크기를 조정합니다"
        }
        Text(
            hint,
            Modifier.fillMaxWidth().padding(horizontal = 24.dp), color = Gray5, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
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
}

private fun cellAt(scene: dev.footprinthere.blip.model.Scene, nx: Float, ny: Float): Int =
    scene.template.cells.indexOfFirst { it.contains(nx, ny) }

@Composable
private fun SlotAction(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val color = if (enabled) White else Gray3
    Column(
        Modifier.width(88.dp).clickable(enabled = enabled, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = color)
        Text(label, color = color, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
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
