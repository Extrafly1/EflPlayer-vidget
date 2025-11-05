package com.example.eflplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.eflplayer.ui.theme.EFLPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val musicViewModel: MusicViewModel = viewModel()
            val context = LocalContext.current
            var dynamicPrimary by remember { mutableStateOf<Color?>(null) }

            // Устанавливаем контекст для ViewModel
            LaunchedEffect(Unit) {
                musicViewModel.setContext(context)
            }

            EFLPlayerTheme(dynamicPrimary = dynamicPrimary) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    MusicApp(
                        viewModel = musicViewModel,
                        onDominantColorChange = { color -> dynamicPrimary = color }
                    )
                }
            }
        }
    }
}