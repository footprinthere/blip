package com.seongtae.vlogmaker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.seongtae.vlogmaker.ui.TempoApp
import com.seongtae.vlogmaker.ui.theme.TempoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TempoTheme { TempoApp() }
        }
    }
}
