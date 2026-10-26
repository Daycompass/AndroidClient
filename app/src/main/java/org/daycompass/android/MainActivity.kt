package org.daycompass.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.daycompass.android.pairing.ConnectedScreen
import org.daycompass.android.pairing.PairingScreen
import org.daycompass.android.pairing.Screen
import org.daycompass.android.pairing.SetupViewModel
import org.daycompass.android.ui.theme.DaycompassTheme
import org.daycompass.android.ui.theme.Dc
import org.daycompass.android.usage.UsageAccessScreen

class MainActivity : ComponentActivity() {
    private val viewModel: SetupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DaycompassTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Dc.colors.bg) {
                    DaycompassApp(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }
}

@Composable
private fun DaycompassApp(viewModel: SetupViewModel) {
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
            // Paired but not allowed to read usage yet, or access was taken away again.
            if (viewModel.usageAccess) {
                ConnectedScreen(screen.serverHost, screen.status)
            } else {
                UsageAccessScreen(screen.serverHost)
            }
        }
    }
}
