package io.github.intealma.skyddsrum.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import io.github.intealma.skyddsrum.BuildConfig
import io.github.intealma.skyddsrum.R
import io.github.intealma.skyddsrum.SkyddsrumApp

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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Card(colors = CardDefaults.cardColors(
                containerColor = if (isPremium) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            )) {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Star, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.premium), fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(stringResource(if (isPremium) R.string.status_premium else R.string.status_free)) },
                )
            }
        }
        item {
            Card {
                ListItem(
                    modifier = Modifier.clickable(onClick = onSupport),
                    leadingContent = { Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    headlineContent = { Text(stringResource(R.string.support_project)) },
                    supportingContent = { Text(stringResource(R.string.support_project_sub)) },
                )
                HorizontalDivider()
                ListItem(
                    modifier = Modifier.clickable(enabled = !restoring, onClick = onRestore),
                    leadingContent = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.restore_purchases)) },
                )
            }
        }
        item {
            Text(stringResource(R.string.language_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.about_body))
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.data_source_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.data_source_body), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.map_attribution), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.unofficial), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.license_body), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SkyddsrumApp.SOURCE_URL))) }
                    }) { Text(stringResource(R.string.source_code)) }
                }
            }
        }
    }
}
