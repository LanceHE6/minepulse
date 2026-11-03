package com.hycer.minepulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.hycer.minepulse.ui.screen.ServerDetailScreen
import com.hycer.minepulse.ui.screen.ServerListScreen
import com.hycer.minepulse.ui.theme.DarkBackground
import com.hycer.minepulse.ui.theme.MinePulseTheme
import com.hycer.minepulse.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MinePulseTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    color = DarkBackground
                ) {
                    val activeServerId by viewModel.activeServerId.collectAsState()

                    if (activeServerId == null) {
                        ServerListScreen(
                            viewModel = viewModel,
                            onServerClick = { server ->
                                viewModel.selectServer(server)
                            }
                        )
                    } else {
                        BackHandler {
                            viewModel.clearActiveServer()
                        }
                        ServerDetailScreen(
                            viewModel = viewModel,
                            serverId = activeServerId!!,
                            onBack = {
                                viewModel.clearActiveServer()
                            }
                        )
                    }
                }
            }
        }
    }
}
