package org.daycompass.android.usage

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import org.daycompass.android.R
import org.daycompass.android.pairing.SYMBOL_SIZE
import org.daycompass.android.pairing.ScreenColumn
import org.daycompass.android.ui.components.DcButton
import org.daycompass.android.ui.theme.DaycompassTheme
import org.daycompass.android.ui.theme.Dc
import org.daycompass.android.ui.theme.DcRadius
import org.daycompass.android.ui.theme.DcSpacing

/**
 * Explains what usage access is for before sending the user to the system settings. Nothing
 * is collected until access is granted; the app checks again whenever it comes back.
 */
@Composable
fun UsageAccessScreen(serverHost: String) {
    val context = LocalContext.current
    ScreenColumn {
        Image(
            painter = painterResource(R.drawable.dc_symbol),
            contentDescription = null,
            modifier = Modifier.size(SYMBOL_SIZE),
        )
        Text(
            text = stringResource(R.string.usage_access_title),
            style = Dc.type.heading1,
            color = Dc.colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.usage_access_intro, serverHost),
            style = Dc.type.bodyLg,
            color = Dc.colors.text,
        )
        FactList(
            title = stringResource(R.string.usage_access_sent_title),
            facts =
                listOf(
                    stringResource(R.string.usage_access_sent_apps),
                    stringResource(R.string.usage_access_sent_times),
                ),
        )
        FactList(
            title = stringResource(R.string.usage_access_never_title),
            facts =
                listOf(
                    stringResource(R.string.usage_access_never_content),
                    stringResource(R.string.usage_access_never_before),
                ),
        )
        DcButton(
            text = stringResource(R.string.usage_access_open_settings),
            onClick = { openUsageAccessSettings(context) },
            large = true,
            modifier = Modifier.fillMaxWidth().padding(top = DcSpacing.space2),
        )
        Text(
            text = stringResource(R.string.usage_access_hint),
            style = Dc.type.body,
            color = Dc.colors.textMuted,
        )
    }
}

@Composable
private fun FactList(
    title: String,
    facts: List<String>,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(DcSpacing.space2),
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Dc.colors.surface, DcRadius.xl)
                .padding(DcSpacing.space5),
    ) {
        Text(
            text = title,
            style = Dc.type.heading3,
            color = Dc.colors.text,
            modifier = Modifier.semantics { heading() },
        )
        facts.forEach { Text(text = "• $it", style = Dc.type.body, color = Dc.colors.text) }
    }
}

/**
 * Opens the usage access list, on this app's entry where the system supports that. Some
 * devices have no such screen at all; then the general settings open instead.
 */
private fun openUsageAccessSettings(context: Context) {
    val attempts =
        listOf(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
    attempts.firstOrNull { intent ->
        try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun UsageAccessScreenPreview() {
    DaycompassTheme(darkTheme = false) { UsageAccessScreen(serverHost = "example.org") }
}
