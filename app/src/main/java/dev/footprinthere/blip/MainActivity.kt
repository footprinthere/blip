package dev.footprinthere.blip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.footprinthere.blip.ui.BlipApp
import dev.footprinthere.blip.ui.theme.BlipTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlipTheme { BlipApp() }
        }
    }
}
