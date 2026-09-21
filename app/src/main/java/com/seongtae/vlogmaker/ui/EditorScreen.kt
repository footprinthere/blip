package com.seongtae.vlogmaker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seongtae.vlogmaker.model.UNIT_OPTIONS_MS
import com.seongtae.vlogmaker.model.formatSec
import com.seongtae.vlogmaker.ui.theme.Gray3
import com.seongtae.vlogmaker.ui.theme.Gray5
import com.seongtae.vlogmaker.ui.theme.White
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun EditorScreen(vm: EditorViewModel) {
    val project = vm.project
    val scenes = project.scenes
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris -> vm.addMedia(uris) }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
    var unitMenu by remember { mutableStateOf(false) }
    val selected = vm.selectedScene.coerceIn(0, (scenes.size - 1).coerceAtLeast(0))

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar(title = "Blip") {
            Box {
                TextButton(onClick = { unitMenu = true }) { Text("단위 ${formatSec(project.unitMs)}", color = White) }
                DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                    UNIT_OPTIONS_MS.forEach { ms ->
                        DropdownMenuItem(text = { Text(formatSec(ms)) }, onClick = { vm.setUnit(ms); unitMenu = false })
                    }
                }
            }
            if (vm.loading > 0) {
                CircularProgressIndicator(Modifier.padding(12.dp).width(20.dp).height(20.dp), color = White, strokeWidth = 2.dp)
            }
            IconButton(
                onClick = { vm.screen = Screen.Export; vm.startExport() },
                enabled = scenes.isNotEmpty() && vm.loading == 0,
            ) { Icon(Icons.Outlined.FileDownload, contentDescription = "내보내기") }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            if (scenes.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("사진과 영상을 불러와\n브이로그를 시작하세요", color = Gray5, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(24.dp))
                    OutlinedButton(onClick = pick) { Text("미디어 불러오기", color = White) }
                }
            } else {
                ScenePreview(
                    project, selected, vm.previews,
                    Modifier.fillMaxHeight().clickable { vm.screen = Screen.SceneEdit(selected) },
                )
            }
        }

        if (scenes.isNotEmpty()) {
            val scene = scenes[selected]
            Text(
                "장면 ${selected + 1} / ${scenes.size}  ·  ${formatSec(project.sceneDurationMs(scene))}  ·  전체 ${formatSec(project.totalMs)}",
                Modifier.fillMaxWidth(), color = Gray5, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(8.dp))
        SceneStrip(vm, selected, onAdd = pick)
        Spacer(Modifier.height(8.dp))
        CaptionList(vm)
    }
}

@Composable
private fun SceneStrip(vm: EditorViewModel, selected: Int, onAdd: () -> Unit) {
    val project = vm.project
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = project.scenes.indexOfFirst { it.id == from.key }
        val toIndex = project.scenes.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) vm.moveScene(fromIndex, toIndex)
    }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().height(112.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(project.scenes, key = { _, s -> s.id }) { i, scene ->
            ReorderableItem(reorderState, key = scene.id) { dragging ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ScenePreview(
                        project, i, vm.previews,
                        Modifier
                            .height(88.dp)
                            .border(if (i == selected || dragging) 2.dp else 1.dp, if (i == selected || dragging) White else Gray3)
                            .longPressDraggableHandle()
                            .clickable { vm.selectedScene = i },
                        showCaptions = false,
                        renderWidth = 216,
                    )
                    Text(formatSec(project.sceneDurationMs(scene)), color = Gray5, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item(key = "add") {
            Box(
                Modifier.width(50.dp).height(88.dp).border(1.dp, Gray3).clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Add, contentDescription = "미디어 추가", tint = White) }
        }
    }
}

@Composable
private fun CaptionList(vm: EditorViewModel) {
    val project = vm.project
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("자막", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { vm.screen = Screen.CaptionEdit(vm.addCaption()) },
                enabled = project.scenes.isNotEmpty(),
            ) { Icon(Icons.Outlined.Add, contentDescription = "자막 추가") }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 132.dp)) {
            items(project.captions, key = { it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.screen = Screen.CaptionEdit(c.id) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        c.text.ifBlank { "(빈 자막)" }, Modifier.weight(1f),
                        color = if (c.text.isBlank()) Gray5 else White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        if (c.startScene == c.endScene) "장면 ${c.startScene + 1}" else "장면 ${c.startScene + 1}–${c.endScene + 1}",
                        color = Gray5, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (project.captions.isEmpty()) {
                item { Text("아직 자막이 없습니다", color = Gray3, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 12.dp)) }
            }
        }
    }
}
