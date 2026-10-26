package org.daycompass.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import org.daycompass.android.pairing.ConnectedScreen
import org.daycompass.android.pairing.PairingScreen
import org.daycompass.android.pairing.Screen
import org.daycompass.android.pairing.SetupViewModel
import org.daycompass.android.ui.theme.DaycompassTheme
import org.daycompass.android.ui.theme.Dc

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DaycompassTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Dc.colors.bg) {
                    DaycompassApp()
                }
            }
        }
    }
}

@Composable
private fun DaycompassApp(viewModel: SetupViewModel = viewModel()) {
    when (val screen = viewModel.screen) {
        Screen.Loading -> {
            // Reading the stored token takes a moment; the window background shows meanwhile.
        }

        is Screen.Pairing -> {
            PairingScreen(
                form = viewModel.form,
                repairing = screen.repairing,
                onServerUrlChange = viewModel::onServerUrlChange,
                onPairingCodeChange = viewModel::onPairingCodeChange,
                onDeviceNameChange = viewModel::onDeviceNameChange,
                onSubmit = viewModel::submit,
            )
        }

        is Screen.Connected -> {
            ConnectedScreen(screen.serverHost, screen.status)
        }
    }
}
