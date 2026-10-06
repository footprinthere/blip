package dev.footprinthere.blip.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.footprinthere.blip.ui.theme.Black

@Composable
fun BlipApp(vm: EditorViewModel = viewModel()) {
    val screen = vm.screen
    BackHandler(enabled = screen != Screen.Editor) {
        vm.screen = when (screen) {
            is Screen.Trim -> Screen.SceneEdit(screen.sceneIndex, screen.slotIndex)
            Screen.Export -> { vm.cancelExport(); Screen.Editor }
            else -> Screen.Editor
        }
    }
    Surface(Modifier.fillMaxSize(), color = Black) {
        when (screen) {
            Screen.Editor -> EditorScreen(vm)
            is Screen.SceneEdit -> SceneEditScreen(vm, screen.index, screen.slot)
            is Screen.Trim -> TrimScreen(vm, screen.sceneIndex, screen.slotIndex)
            is Screen.CaptionEdit -> CaptionEditScreen(vm, screen.id)
            Screen.Play -> PlayScreen(vm)
            Screen.Export -> ExportScreen(vm)
        }
    }
}
