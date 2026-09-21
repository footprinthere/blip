package com.seongtae.vlogmaker.ui

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.seongtae.vlogmaker.fonts.FontCatalog
import com.seongtae.vlogmaker.fonts.FontDef
import com.seongtae.vlogmaker.fonts.Fonts
import com.seongtae.vlogmaker.fonts.Script
import com.seongtae.vlogmaker.model.Caption
import com.seongtae.vlogmaker.render.SceneRenderer
import com.seongtae.vlogmaker.ui.theme.Gray1
import com.seongtae.vlogmaker.ui.theme.Gray2
import com.seongtae.vlogmaker.ui.theme.Gray3
import com.seongtae.vlogmaker.ui.theme.Gray5
import com.seongtae.vlogmaker.ui.theme.White

private const val SNAP_THRESHOLD = 0.02f
private const val SIZE_SNAP_THRESHOLD = 0.004f

private val CAPTION_COLORS = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFF9E9E9E, 0xFFFF5252, 0xFFFF9800, 0xFFFFEB3B,
    0xFF4CAF50, 0xFF00BCD4, 0xFF2196F3, 0xFF9C27B0, 0xFFFF4081, 0xFFFFCCBC,
).map { it.toInt() }

@Composable
fun CaptionEditScreen(vm: EditorViewModel, id: String) {
    val project = vm.project
    val caption = project.captions.find { it.id == id }
    if (caption == null || project.scenes.isEmpty()) {
        LaunchedEffect(Unit) { vm.screen = Screen.Editor }
        return
    }
    val n = project.scenes.size
    val startScene = caption.startScene.coerceIn(0, n - 1)
    val context = LocalContext.current
    LaunchedEffect(caption.fontId) { Fonts.load(context, caption.fontId) }
    val update: ((Caption) -> Caption) -> Unit = { f -> vm.updateCaption(id, f) }
    val haptic = LocalHapticFeedback.current
    // 끌기 중 스냅으로 붙은 축의 위치. 가이드 선 표시와 진동 판단에 쓴다.
    var guideX by remember { mutableStateOf<Float?>(null) }
    var guideY by remember { mutableStateOf<Float?>(null) }
    val others = project.captions.filter { it.id != id }
    val ghosts = others.filter { startScene !in it.startScene..it.endScene }
    var applyDialog by remember { mutableStateOf(false) }
    var sizeSnapped by remember { mutableStateOf<Float?>(null) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        TopBar(title = "자막", onBack = { vm.screen = Screen.Editor }) {
            IconButton(onClick = { applyDialog = true }, enabled = others.isNotEmpty()) {
                Icon(Icons.Outlined.FormatPaint, contentDescription = "이 자막의 설정을 다른 자막에 적용")
            }
            IconButton(onClick = {
                vm.duplicateCaption(id)?.let { newId ->
                    val scene = vm.project.captions.find { it.id == newId }?.startScene ?: 0
                    Toast.makeText(context, "장면 ${scene + 1}에 같은 설정의 빈 자막을 만들었습니다", Toast.LENGTH_SHORT).show()
                    vm.screen = Screen.CaptionEdit(newId)
                }
            }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "같은 자리에 다음 장면 자막 만들기")
            }
            IconButton(onClick = { vm.deleteCaption(id); vm.screen = Screen.Editor }) {
                Icon(Icons.Outlined.Delete, contentDescription = "자막 삭제")
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.aspectRatio(project.aspectRatio)) {
                ScenePreview(project, startScene, vm.previews, Modifier.fillMaxSize(), showCaptions = false)
                CaptionOverlay(
                    captions = project.captionsAt(startScene),
                    ghosts = ghosts,
                    editingId = id,
                    guideX = guideX,
                    guideY = guideY,
                    modifier = Modifier.fillMaxSize().pointerInput(id) {
                        var raw = Offset.Zero
                        detectDragGestures(
                            onDragStart = {
                                val c = vm.project.captions.find { it.id == id }
                                raw = Offset(c?.x ?: 0.5f, c?.y ?: 0.5f)
                            },
                            onDragEnd = { guideX = null; guideY = null },
                            onDragCancel = { guideX = null; guideY = null },
                        ) { change, drag ->
                            change.consume()
                            raw = Offset(
                                (raw.x + drag.x / size.width).coerceIn(0.05f, 0.95f),
                                (raw.y + drag.y / size.height).coerceIn(0.05f, 0.95f),
                            )
                            val targets = vm.project.captions.filter { it.id != id }
                            val tx = Snapping.nearest(raw.x, listOf(0.5f) + targets.map { it.x }, SNAP_THRESHOLD)
                            val ty = Snapping.nearest(raw.y, listOf(0.5f) + targets.map { it.y }, SNAP_THRESHOLD)
                            if ((tx != null && tx != guideX) || (ty != null && ty != guideY)) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            guideX = tx
                            guideY = ty
                            update { it.copy(x = tx ?: raw.x, y = ty ?: raw.y) }
                        }
                    },
                )
            }
        }

        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            TextField(
                value = caption.text,
                onValueChange = { t -> update { it.copy(text = t) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("자막 입력", color = Gray5) },
                textStyle = TextStyle(color = White, fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Gray1, unfocusedContainerColor = Gray1,
                    focusedIndicatorColor = White, unfocusedIndicatorColor = Gray3, cursorColor = White,
                ),
                maxLines = 3,
            )

            // 장면 범위
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("장면", color = Gray5, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(48.dp))
                Stepper(
                    value = caption.startScene + 1,
                    onDec = { update { it.copy(startScene = (it.startScene - 1).coerceAtLeast(0)) } },
                    onInc = { update { it.copy(startScene = (it.startScene + 1).coerceAtMost(it.endScene)) } },
                    decEnabled = caption.startScene > 0, incEnabled = caption.startScene < caption.endScene,
                )
                Text("~", color = Gray5, modifier = Modifier.padding(horizontal = 4.dp))
                Stepper(
                    value = caption.endScene + 1,
                    onDec = { update { it.copy(endScene = (it.endScene - 1).coerceAtLeast(it.startScene)) } },
                    onInc = { update { it.copy(endScene = (it.endScene + 1).coerceAtMost(n - 1)) } },
                    decEnabled = caption.endScene > caption.startScene, incEnabled = caption.endScene < n - 1,
                )
            }

            // 색상
            LazyRow(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(CAPTION_COLORS) { c ->
                    val selected = c == caption.color
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(if (selected) 2.dp else 1.dp, if (selected) White else Gray3, CircleShape)
                            .clickable { update { it.copy(color = c) } },
                    )
                }
            }

            // 크기
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("크기", color = Gray5, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(48.dp))
                Slider(
                    value = caption.size,
                    onValueChange = { s ->
                        val target = Snapping.nearest(s, others.map { it.size }.distinct(), SIZE_SNAP_THRESHOLD)
                        if (target != null && target != sizeSnapped) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        sizeSnapped = target
                        update { it.copy(size = target ?: s) }
                    },
                    onValueChangeFinished = { sizeSnapped = null },
                    valueRange = 0.02f..0.12f,
                    colors = SliderDefaults.colors(thumbColor = White, activeTrackColor = White, inactiveTrackColor = Gray2),
                )
            }

            // 글꼴
            FontPicker(caption.fontId, onSelect = { f -> update { it.copy(fontId = f) } })
            Spacer(Modifier.height(16.dp))
        }
    }

    if (applyDialog) {
        ApplyToAllDialog(
            count = others.size,
            onDismiss = { applyDialog = false },
            onApply = { position, size, color, font ->
                vm.applyCaptionStyleToAll(id, position, size, color, font)
                Toast.makeText(context, "다른 자막 ${others.size}개에 적용했습니다", Toast.LENGTH_SHORT).show()
                applyDialog = false
            },
        )
    }
}

/** 현재 자막의 어떤 설정을 다른 자막들에 맞출지 고르는 창. */
@Composable
private fun ApplyToAllDialog(count: Int, onDismiss: () -> Unit, onApply: (Boolean, Boolean, Boolean, Boolean) -> Unit) {
    var position by remember { mutableStateOf(true) }
    var size by remember { mutableStateOf(true) }
    var color by remember { mutableStateOf(false) }
    var font by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = Gray1, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
                Text(
                    "다른 자막 ${count}개에 맞출 항목",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
                CheckRow("위치", position) { position = it }
                CheckRow("크기", size) { size = it }
                CheckRow("색상", color) { color = it }
                CheckRow("글꼴", font) { font = it }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("취소", color = Gray5) }
                    TextButton(onClick = { onApply(position, size, color, font) }, enabled = position || size || color || font) {
                        Text("적용", color = White)
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked, onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(checkedColor = White, checkmarkColor = Gray1, uncheckedColor = Gray5),
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Stepper(value: Int, onDec: () -> Unit, onInc: () -> Unit, decEnabled: Boolean, incEnabled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDec, enabled = decEnabled) { Icon(Icons.Outlined.Remove, contentDescription = null) }
        Text("$value", Modifier.width(28.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = onInc, enabled = incEnabled) { Icon(Icons.Outlined.Add, contentDescription = null) }
    }
}

/**
 * 미리보기 위에 자막을 실제 렌더러로 그린다. 편집 중인 자막에는 테두리를, 다른 장면에만 있는 자막은
 * 점선 상자로 자리만 보여 주고, 스냅으로 붙은 축에는 가이드 선을 그린다.
 */
@Composable
private fun CaptionOverlay(
    captions: List<Caption>,
    ghosts: List<Caption>,
    editingId: String,
    guideX: Float?,
    guideY: Float?,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val w = size.width.toInt()
        val h = size.height.toInt()
        if (w <= 0 || h <= 0) return@Canvas
        drawIntoCanvas { c -> SceneRenderer.drawCaptions(c.nativeCanvas, w, h, captions, Fonts::get) }
        val thin = Stroke(width = 1.dp.toPx())
        val dashed = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
        val pad = 6.dp.toPx()
        ghosts.forEach { g ->
            val b = SceneRenderer.captionBounds(w, h, g, Fonts.get(g.fontId))
            drawRect(Gray5, Offset(b.left - pad, b.top - pad), Size(b.width() + pad * 2, b.height() + pad * 2), style = dashed)
        }
        captions.find { it.id == editingId }?.let { editing ->
            val b = SceneRenderer.captionBounds(w, h, editing, Fonts.get(editing.fontId))
            drawRect(Gray5, Offset(b.left - pad, b.top - pad), Size(b.width() + pad * 2, b.height() + pad * 2), style = thin)
        }
        guideX?.let { drawLine(White, Offset(it * w, 0f), Offset(it * w, h.toFloat()), strokeWidth = 1.dp.toPx()) }
        guideY?.let { drawLine(White, Offset(0f, it * h), Offset(w.toFloat(), it * h), strokeWidth = 1.dp.toPx()) }
    }
}

@Composable
private fun FontPicker(selectedId: String, onSelect: (String) -> Unit) {
    var script by remember { mutableStateOf(FontCatalog.find(selectedId)?.script ?: Script.KO) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Script.entries.forEach { s ->
                Text(
                    s.label,
                    color = if (s == script) White else Gray5,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable { script = s }.padding(vertical = 4.dp),
                )
            }
        }
        LazyRow(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(FontCatalog.byScript(script), key = { it.id }) { f ->
                FontChip(f, selected = f.id == selectedId, onClick = { onSelect(f.id) })
            }
        }
    }
}

@Composable
private fun FontChip(font: FontDef, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(font.id) { Fonts.load(context, font.id) }
    val typeface = Fonts.typefaces[font.id]
    val failed = Fonts.failed[font.id] == true
    Box(
        Modifier
            .border(1.dp, if (selected) White else Gray3, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            font.label,
            color = if (failed) Gray3 else White,
            fontFamily = typeface?.let { FontFamily(Typeface(it)) },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
