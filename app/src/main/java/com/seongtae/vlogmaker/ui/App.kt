package com.seongtae.vlogmaker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seongtae.vlogmaker.ui.theme.Black

@Composable
fun BlipApp(vm: EditorViewModel = viewModel()) {
    val screen = vm.screen
    BackHandler(enabled = screen != Screen.Editor) {
        vm.screen = when (screen) {
            is Screen.Trim -> Screen.SceneEdit(screen.sceneIndex)
            Screen.Export -> { vm.cancelExport(); Screen.Editor }
            else -> Screen.Editor
        }
    }
    Surface(Modifier.fillMaxSize(), color = Black) {
        when (screen) {
            Screen.Editor -> EditorScreen(vm)
            is Screen.SceneEdit -> SceneEditScreen(vm, screen.index)
            is Screen.Trim -> TrimScreen(vm, screen.sceneIndex, screen.slotIndex)
            is Screen.CaptionEdit -> CaptionEditScreen(vm, screen.id)
            Screen.Export -> ExportScreen(vm)
        }
    }
}
