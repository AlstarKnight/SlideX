package io.github.alstarknight.slidex

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Today's time on screen: the total, then the apps used longest, each with a bar against the longest; a tap opens
 * the app. Asks for usage access first.
 */
@Composable
fun ScreenTimeContent(onLaunch: (Item) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    if (!UsageApps.hasAccess(context)) {
        Box(Modifier.padding(bottom = 12.dp)) {
            PermissionHint(R.string.needs_usage_access) {
                context.startActivity(UsageApps.settingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                onClose()
            }
        }
        return
    }
    val screenTime by produceState<ScreenTime?>(null) {
        value = withContext(Dispatchers.IO) { UsageApps.loadToday(context, SCREEN_TIME_APPS) }
    }
    val today = screenTime ?: return
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
    if (today.apps.isEmpty()) {
        Text(
            stringResource(R.string.screen_time_empty),
            Modifier.padding(bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = hintColor,
        )
        return
    }
    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.screen_time_total, durationText(today.totalMillis)),
            style = MaterialTheme.typography.titleMedium,
        )
        val longest = today.apps.first().second
        today.apps.forEach { (app, time) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onLaunch(app) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ItemIcon(app, 32.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row {
                        Text(app.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(durationText(time), color = hintColor, style = MaterialTheme.typography.bodySmall)
                    }
                    LinearProgressIndicator(progress = { time.toFloat() / longest }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun durationText(millis: Long): String {
    val minutes = millis / MINUTE_MS
    return when {
        minutes < 1 -> stringResource(R.string.duration_under_minute)
        minutes < 60 -> stringResource(R.string.duration_minutes, minutes)
        else -> stringResource(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
    }
}

private const val SCREEN_TIME_APPS = 5
private const val MINUTE_MS = 60_000L
