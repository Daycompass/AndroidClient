package org.daycompass.android.pairing

import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import org.daycompass.android.R
import org.daycompass.android.sync.SyncResult
import org.daycompass.android.ui.theme.DaycompassTheme
import org.daycompass.android.ui.theme.Dc
import java.util.Date
import kotlin.math.abs

/** Skew the server tolerates before it refuses events from the future. */
private const val TOLERATED_SKEW_SECONDS = 300L
private const val SECONDS_PER_MINUTE = 60L

@Composable
fun ConnectedScreen(
    serverHost: String,
    status: SyncStatus,
) {
    ScreenColumn {
        Image(
            painter = painterResource(R.drawable.dc_symbol),
            contentDescription = null,
            modifier = Modifier.size(SYMBOL_SIZE),
        )
        Text(
            text = stringResource(R.string.connected_title),
            style = Dc.type.heading1,
            color = Dc.colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.connected_body, serverHost),
            style = Dc.type.bodyLg,
            color = Dc.colors.text,
        )
        Text(
            text = syncLine(status),
            style = Dc.type.body,
            color = if (status.lastResult.isProblem()) Dc.colors.warning else Dc.colors.textMuted,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (status.pendingEvents > 0) {
            Text(
                text = pluralStringResource(R.plurals.sync_pending, status.pendingEvents, status.pendingEvents),
                style = Dc.type.body,
                color = Dc.colors.textMuted,
            )
        }
        status.clockSkewSeconds?.takeIf { abs(it) > TOLERATED_SKEW_SECONDS }?.let { skew ->
            val minutes = (abs(skew) / SECONDS_PER_MINUTE).toInt()
            Text(
                text = pluralStringResource(R.plurals.sync_clock_skew, minutes, minutes),
                style = Dc.type.body,
                color = Dc.colors.warning,
            )
        }
    }
}

@Composable
private fun syncLine(status: SyncStatus): String {
    val lastSync = status.lastSyncMillis?.let { DateFormat.getTimeFormat(LocalContext.current).format(Date(it)) }
    return when {
        status.syncing -> stringResource(R.string.sync_running)
        status.lastResult == SyncResult.Unreachable -> stringResource(R.string.sync_unreachable)
        status.lastResult is SyncResult.RateLimited -> stringResource(R.string.sync_rate_limited)
        status.lastResult == SyncResult.ServerError -> stringResource(R.string.sync_server_error)
        lastSync != null -> stringResource(R.string.sync_done_at, lastSync)
        else -> stringResource(R.string.sync_never)
    }
}

private fun SyncResult?.isProblem() =
    this == SyncResult.Unreachable || this is SyncResult.RateLimited || this == SyncResult.ServerError

@Preview(showBackground = true)
@Composable
private fun ConnectedScreenPreview() {
    DaycompassTheme(darkTheme = false) {
        ConnectedScreen(
            serverHost = "example.org",
            status = SyncStatus(syncing = false, lastResult = SyncResult.Unreachable, pendingEvents = 42),
        )
    }
}
