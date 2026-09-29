package io.github.intealma.skyddsrum.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.intealma.skyddsrum.BuildConfig
import io.github.intealma.skyddsrum.R
import io.github.intealma.skyddsrum.SkyddsrumApp
import io.github.intealma.skyddsrum.ui.theme.Line
import io.github.intealma.skyddsrum.ui.theme.TextFaint
import io.github.intealma.skyddsrum.ui.theme.TextMuted
import io.github.intealma.skyddsrum.ui.theme.White

@Composable
fun SettingsScreen(
    isPremium: Boolean,
    restoring: Boolean,
    onSupport: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text(stringResource(R.string.settings_title).uppercase(), style = MaterialTheme.typography.titleMedium)
        }
        item {
            SectionLabel(stringResource(R.string.premium))
            Spacer(Modifier.height(6.dp))
            Column(Modifier.fillMaxWidth().panel()) {
                SettingsRow(
                    stringResource(R.string.premium),
                    value = stringResource(if (isPremium) R.string.status_premium else R.string.status_free),
                )
                HorizontalDivider(color = Line)
                SettingsRow(stringResource(R.string.support_project), value = stringResource(R.string.support_project_sub), onClick = onSupport)
                HorizontalDivider(color = Line)
                SettingsRow(stringResource(R.string.restore_purchases), onClick = if (restoring) null else onRestore)
            }
        }
        item {
            SectionLabel(stringResource(R.string.about_title))
            Spacer(Modifier.height(6.dp))
            Column(Modifier.fillMaxWidth().panel().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyMedium)
                SectionLabel(stringResource(R.string.data_source_title))
                Text(stringResource(R.string.data_source_body), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                Text(stringResource(R.string.map_attribution), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                Text(stringResource(R.string.unofficial), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                Text(
                    "${stringResource(R.string.license_body)} · ${stringResource(R.string.version, BuildConfig.VERSION_NAME)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextFaint,
                )
                Text(
                    stringResource(R.string.source_code).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = White,
                    modifier = Modifier.clickable {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SkyddsrumApp.SOURCE_URL))) }
                    }.padding(vertical = 4.dp),
                )
            }
        }
        item {
            Text(stringResource(R.string.language_hint), style = MaterialTheme.typography.bodySmall, color = TextFaint)
        }
    }
}

@Composable
private fun SettingsRow(title: String, value: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}
