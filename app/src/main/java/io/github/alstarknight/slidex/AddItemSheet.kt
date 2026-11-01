package io.github.alstarknight.slidex

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-panel sheet for picking what to add to a grid card: apps, shortcut actions but [excludedActions], ready-made
 * links and links of your own. Several can be ticked, across tabs, and added together.
 */
@Composable
fun AddItemSheet(onAdd: (List<Item>) -> Unit, onDismiss: () -> Unit, excludedActions: Set<Action> = emptySet()) {
    BackHandler(onBack = onDismiss)
    var tab by remember { mutableIntStateOf(0) }
    val selected = remember { mutableStateListOf<Item>() }
    val toggle = { item: Item -> if (!selected.remove(item)) selected.add(item) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.safeDrawingPadding()) {
            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                }
                Text(stringResource(R.string.item_add), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Button(
                    onClick = { onAdd(selected.toList()) },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(stringResource(R.string.add_selected, selected.size))
                }
            }
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.add_tab_apps, R.string.add_tab_actions, R.string.add_tab_link).forEachIndexed { i, label ->
                    FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(stringResource(label)) })
                }
            }
            when (tab) {
                0 -> AppPicker(selected, toggle)
                1 -> ActionPicker(selected, toggle, excludedActions)
                else -> LinkPicker(selected, toggle, onAdd = { link -> onAdd(selected + link) })
            }
        }
    }
}

@Composable
private fun AppPicker(selected: List<Item>, onToggle: (Item) -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) { InstalledApps.load(context) }
    }
    var query by remember { mutableStateOf("") }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        placeholder = { Text(stringResource(R.string.search_apps)) },
    )
    val list = apps
    if (list == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val filtered = searchApps(list, query) { it.search }
    LazyColumn {
        items(filtered, key = { "${it.packageName}/${it.activityName}" }) { app ->
            val item = AppItem(app.label, app.packageName, app.activityName)
            PickerRow(item, app.label, item in selected) { onToggle(item) }
        }
    }
}

@Composable
private fun ActionPicker(selected: List<Item>, onToggle: (Item) -> Unit, excluded: Set<Action>) {
    val context = LocalContext.current
    val (switches, shortcuts) = (Action.entries - excluded).partition { it in SWITCH_ACTIONS }
    LazyColumn {
        listOf(R.string.action_group_shortcuts to shortcuts, R.string.action_group_switches to switches).forEach { (title, actions) ->
            item(key = title) { GroupTitle(stringResource(title)) }
            items(actions, key = { it.name }) { action ->
                val item = ActionItem(context.getString(action.labelRes), action)
                PickerRow(item, item.label, item in selected) { onToggle(item) }
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun PickerRow(item: Item, label: String, checked: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { ItemIcon(item, 40.dp) },
        trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/**
 * A search box, a form for a link of your own, then ready-made links into the installed apps that have them, the
 * common ones first, and the shortcuts the apps declare for a long press on their icons.
 */
@Composable
private fun LinkPicker(selected: List<Item>, onToggle: (Item) -> Unit, onAdd: (LinkItem) -> Unit) {
    val context = LocalContext.current
    val presets by produceState(emptyList<AppLinks>() to emptyList<AppLinks>()) {
        value = withContext(Dispatchers.IO) {
            installedPresets(context, COMMON_PRESETS) to installedPresets(context, MORE_PRESETS)
        }
    }
    val shortcuts by produceState(emptyList<AppLinks>()) {
        value = withContext(Dispatchers.IO) { installedAppShortcuts(context) }
    }
    var query by remember { mutableStateOf("") }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        placeholder = { Text(stringResource(R.string.search_links)) },
    )
    LazyColumn {
        // The form is for links of your own, so it makes way while the listed ones are searched.
        if (query.isBlank()) item(key = "form") { LinkForm(onAdd) }
        linkSection("common", R.string.preset_section, null, presets.first, query, selected, onToggle)
        linkSection("more", R.string.preset_more_section, null, presets.second, query, selected, onToggle)
        linkSection(
            key = "shortcuts",
            title = R.string.app_shortcut_section,
            hint = R.string.app_shortcut_hint,
            apps = shortcuts,
            query = query,
            selected = selected,
            onToggle = onToggle,
        )
    }
}

/** A titled list of apps' links, leaving out the ones [query] does not find, and the title when none are left. */
private fun LazyListScope.linkSection(
    key: String,
    @StringRes title: Int,
    @StringRes hint: Int?,
    apps: List<AppLinks>,
    query: String,
    selected: List<Item>,
    onToggle: (Item) -> Unit,
) {
    val found = apps.mapNotNull { app ->
        val links = app.links.filter { searchScore(query, it.search) != null }
        if (links.isEmpty()) null else app to links
    }
    if (found.isEmpty()) return
    item(key = key) {
        Column {
            GroupTitle(stringResource(title))
            if (hint != null) {
                Text(
                    stringResource(hint),
                    Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    found.forEach { (app, links) ->
        item(key = "$key:${app.packageName}") { AppName(app.appLabel) }
        items(links, key = { "$key:${it.item.uri}" }) { link ->
            PickerRow(link.item, link.item.label, link.item in selected) { onToggle(link.item) }
        }
    }
}

@Composable
private fun AppName(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, top = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LinkForm(onAdd: (LinkItem) -> Unit) {
    var label by remember { mutableStateOf("") }
    var uri by remember { mutableStateOf("") }
    val valid = label.isNotBlank() && ItemLauncher.linkIntent(uri) != null
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.link_label)) },
        )
        OutlinedTextField(
            value = uri,
            onValueChange = { uri = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.link_uri)) },
            supportingText = { Text(stringResource(R.string.link_uri_hint)) },
        )
        Button(
            onClick = { onAdd(LinkItem(label.trim(), uri.trim())) },
            enabled = valid,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.item_add))
        }
    }
}

/** Full-panel list of every app, like the original app's app drawer; tapping one opens it. */
@Composable
fun AppDrawer(onLaunch: (AppItem) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) { InstalledApps.load(context) }
    }
    var query by remember { mutableStateOf("") }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.safeDrawingPadding()) {
            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                }
                Text(stringResource(R.string.action_app_drawer), style = MaterialTheme.typography.titleLarge)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.search_apps)) },
            )
            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                AppGrid(searchApps(list, query) { it.search }, onLaunch)
            }
        }
    }
}

@Composable
private fun AppGrid(apps: List<AppEntry>, onLaunch: (AppItem) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(DRAWER_COLUMNS),
        contentPadding = PaddingValues(8.dp),
    ) {
        items(apps, key = { "${it.packageName}/${it.activityName}" }) { app ->
            val item = AppItem(app.label, app.packageName, app.activityName)
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onLaunch(item) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ItemIcon(item, 44.dp)
                Text(
                    app.label,
                    Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val DRAWER_COLUMNS = 4
