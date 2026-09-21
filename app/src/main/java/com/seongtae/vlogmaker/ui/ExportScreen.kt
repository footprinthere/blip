package com.seongtae.vlogmaker.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.seongtae.vlogmaker.ui.theme.Gray2
import com.seongtae.vlogmaker.ui.theme.Gray5
import com.seongtae.vlogmaker.ui.theme.White

@Composable
fun ExportScreen(vm: EditorViewModel) {
    val state = vm.exportState
    val context = LocalContext.current
    val close = { vm.exportState = ExportState.Idle; vm.screen = Screen.Editor }

    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (state) {
                is ExportState.Running -> {
                    Text("내보내는 중", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(24.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = White, trackColor = Gray2, gapSize = 0.dp, drawStopIndicator = {},
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("${(state.progress * 100).toInt()}%", color = Gray5, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(32.dp))
                    TextButton(onClick = { vm.cancelExport(); vm.screen = Screen.Editor }) { Text("취소", color = Gray5) }
                }
                is ExportState.Done -> {
                    Text("저장 완료", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("갤러리 › Movies/Tempo", color = Gray5, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(32.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(state.uri, "video/mp4")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            runCatching { context.startActivity(intent) }
                        }) { Text("열기", color = White) }
                        TextButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).setType("video/mp4")
                                .putExtra(Intent.EXTRA_STREAM, state.uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            runCatching { context.startActivity(Intent.createChooser(intent, null)) }
                        }) { Text("공유", color = White) }
                        TextButton(onClick = close) { Text("닫기", color = Gray5) }
                    }
                }
                is ExportState.Failed -> {
                    Text("내보내기 실패", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(state.message, color = Gray5, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(32.dp))
                    TextButton(onClick = close) { Text("닫기", color = White) }
                }
                ExportState.Idle -> {
                    TextButton(onClick = close) { Text("닫기", color = White) }
                }
            }
        }
    }
}
