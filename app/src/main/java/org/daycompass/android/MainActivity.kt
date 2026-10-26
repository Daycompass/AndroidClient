package org.daycompass.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.daycompass.android.buffer.BufferScreen
import org.daycompass.android.pairing.ConnectedScreen
import org.daycompass.android.pairing.PairingLinkConfirmScreen
import org.daycompass.android.pairing.PairingScreen
import org.daycompass.android.pairing.PairingStep
import org.daycompass.android.pairing.QrScanScreen
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
        consumePairingLink(intent)?.let { viewModel.onLinkScanned(it) }
        setContent {
            DaycompassTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Dc.colors.bg) {
                    DaycompassApp(viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Deliberately not setIntent(intent): a pairing link must not linger as "the"
        // activity intent beyond this one handoff.
        consumePairingLink(intent)?.let { viewModel.onLinkScanned(it) }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }
}

/** The link is a secret until redeemed, so it is read once and scrubbed from the intent. */
private fun consumePairingLink(intent: Intent?): String? {
    val uri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return null
    intent.data = null
    return uri.toString()
}

@Composable
private fun DaycompassApp(viewModel: SetupViewModel) {
    when (val screen = viewModel.screen) {
        Screen.Loading -> {
            // Reading the stored token takes a moment; the window background shows meanwhile.
        }

        is Screen.Pairing -> {
            when (val step = screen.step) {
                PairingStep.ManualEntry -> {
                    PairingScreen(
                        form = viewModel.form,
                        repairing = screen.repairing,
                        onServerUrlChange = viewModel::onServerUrlChange,
                        onPairingCodeChange = viewModel::onPairingCodeChange,
                        onDeviceNameChange = viewModel::onDeviceNameChange,
                        onSubmit = viewModel::submit,
                        onScanQrCode = viewModel::startScanning,
                    )
                }

                PairingStep.Scanning -> {
                    QrScanScreen(
                        onScanned = viewModel::onLinkScanned,
                        onCancel = viewModel::cancelPairingStep,
                    )
                }

                is PairingStep.Confirming -> {
                    PairingLinkConfirmScreen(
                        step = step,
                        deviceName = viewModel.form.deviceName,
                        onDeviceNameChange = viewModel::onDeviceNameChange,
                        onConfirm = viewModel::confirmLinkPairing,
                        onCancel = viewModel::cancelPairingStep,
                    )
                }
            }
        }

        is Screen.Connected -> {
            when {
                // Paired but not allowed to read usage yet, or access was taken away again.
                !viewModel.usageAccess -> {
                    UsageAccessScreen(screen.serverHost)
                }

                screen.showingBuffer -> {
                    BufferScreen(
                        events = viewModel.bufferedEvents,
                        totalCount = screen.status.pendingEvents,
                        onBack = viewModel::hideBufferedEvents,
                    )
                }

                else -> {
                    ConnectedScreen(
                        serverHost = screen.serverHost,
                        status = screen.status,
                        onShowBuffer = viewModel::showBufferedEvents,
                    )
                }
            }
        }
    }
}
