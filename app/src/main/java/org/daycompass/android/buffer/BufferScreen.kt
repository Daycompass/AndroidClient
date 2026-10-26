package org.daycompass.android.buffer

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import org.daycompass.android.R
import org.daycompass.android.ui.components.DcButton
import org.daycompass.android.ui.components.DcButtonVariant
import org.daycompass.android.ui.theme.DaycompassTheme
import org.daycompass.android.ui.theme.Dc
import org.daycompass.android.ui.theme.DcRadius
import org.daycompass.android.ui.theme.DcSpacing
import java.util.Date

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val EVENT_ID_HEX_LENGTH = 32

/**
 * What is still sitting in the local buffer, waiting to be sent. Purely a read of that table —
 * there is no server readback to show synced events (`GET /api/v1/events` is session auth, a
 * device token can't use it), so entries disappear from this list once the server accepts them.
 */
@Composable
fun BufferScreen(
    events: List<BufferedEvent>,
    totalCount: Int,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(DcSpacing.space2),
            modifier = Modifier.fillMaxWidth().padding(DcSpacing.space4),
        ) {
            Text(
                text = stringResource(R.string.buffer_title),
                style = Dc.type.heading1,
                color = Dc.colors.text,
                modifier = Modifier.semantics { heading() },
            )
            if (totalCount > 0) {
                Text(
                    text = pluralStringResource(R.plurals.buffer_count, totalCount, totalCount),
                    style = Dc.type.body,
                    color = Dc.colors.textMuted,
                )
                if (events.size < totalCount) {
                    Text(
                        text = pluralStringResource(R.plurals.buffer_truncated, events.size, events.size),
                        style = Dc.type.caption,
                        color = Dc.colors.textMuted,
                    )
                }
            } else {
                Text(text = stringResource(R.string.buffer_empty), style = Dc.type.body, color = Dc.colors.textMuted)
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = DcSpacing.space4, vertical = DcSpacing.space2),
            verticalArrangement = Arrangement.spacedBy(DcSpacing.space2),
        ) {
            items(events, key = { it.eventId }) { event -> BufferRow(event) }
        }
        DcButton(
            text = stringResource(R.string.buffer_back),
            onClick = onBack,
            variant = DcButtonVariant.Neutral,
            modifier = Modifier.fillMaxWidth().padding(DcSpacing.space4),
        )
    }
}

@Composable
private fun BufferRow(event: BufferedEvent) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Dc.colors.surface, DcRadius.lg)
                .padding(DcSpacing.space3),
    ) {
        Column {
            Text(text = event.appId, style = Dc.type.body, color = Dc.colors.text)
            Text(text = formatCollectedAt(event.startTs), style = Dc.type.caption, color = Dc.colors.textMuted)
        }
        Text(text = formatDuration(event.durationSecs), style = Dc.type.body, color = Dc.colors.textMuted)
    }
}

@Composable
private fun formatCollectedAt(startTs: Long): String {
    val context = LocalContext.current
    val date = Date(startTs * MILLIS_PER_SECOND)
    val datePart = DateFormat.getDateFormat(context).format(date)
    val timePart = DateFormat.getTimeFormat(context).format(date)
    return "$datePart $timePart"
}

@Composable
private fun formatDuration(durationSecs: Long): String {
    val minutes = durationSecs / SECONDS_PER_MINUTE
    return if (minutes > 0) {
        stringResource(R.string.buffer_duration_minutes, minutes)
    } else {
        pluralStringResource(R.plurals.buffer_duration_seconds, durationSecs.toInt(), durationSecs)
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun BufferScreenPreview() {
    DaycompassTheme(darkTheme = false) {
        BufferScreen(
            events =
                listOf(
                    BufferedEvent(
                        eventId = "0".repeat(EVENT_ID_HEX_LENGTH),
                        startTs = 1_800_000_000L,
                        durationSecs = 125,
                        appId = "org.mozilla.firefox",
                    ),
                    BufferedEvent(
                        eventId = "1".repeat(EVENT_ID_HEX_LENGTH),
                        startTs = 1_799_999_000L,
                        durationSecs = 45,
                        appId = "com.android.chrome",
                    ),
                ),
            totalCount = 212,
            onBack = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun BufferScreenEmptyPreview() {
    DaycompassTheme(darkTheme = false) {
        BufferScreen(events = emptyList(), totalCount = 0, onBack = {})
    }
}
