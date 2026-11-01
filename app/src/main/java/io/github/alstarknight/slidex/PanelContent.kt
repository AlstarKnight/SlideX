package io.github.alstarknight.slidex

import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The panel itself: swipeable pages of cards. A page is edited on the page (its cards, the pages and its look), and
 * each card on its own.
 */
@Composable
fun PanelContent(
    store: ConfigStore,
    launcher: ItemLauncher,
    initialPage: Int,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val config by store.config.collectAsState()
    val switches by launcher.switches.collectAsState()
    var editingPage by remember { mutableStateOf(false) }
    // The card being edited on its own, by id.
    var editingCard by remember { mutableStateOf<String?>(null) }
    var addingToCard by remember { mutableStateOf<String?>(null) }
    // The icon being renamed: its card's id and its index in the card.
    var renamingItem by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var showingDrawer by remember { mutableStateOf(false) }
    // The page whose scene apps are being picked.
    var pickingSceneApps by remember { mutableStateOf<Int?>(null) }
    val pagerState = rememberPagerState(initialPage.coerceIn(0, config.pages.lastIndex)) { config.pages.size }
    val scope = rememberCoroutineScope()
    // The look chosen in the settings; a page can have a look of its own on top of it.
    val baseTheme = LocalThemeExtras.current
    val appearance = LocalAppearance.current

    LaunchedEffect(Unit) { launcher.refreshSwitches() }
    BackHandler(enabled = editingPage || editingCard != null) {
        if (editingCard != null) editingCard = null else editingPage = false
    }

    Box(Modifier.fillMaxSize()) {
        // Every page brings its own background, which slides in and out with it.
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
            val page = config.pages.getOrNull(pageIndex) ?: return@HorizontalPager
            PageTheme(page.style, baseTheme) {
                Box(Modifier.fillMaxSize()) {
                    PageBackground(page.style)
                    Column(
                        Modifier
                            .fillMaxSize()
                            .safeDrawingPadding(),
                    ) {
                        // Room for the header, which stays in place above the pages.
                        Spacer(Modifier.height(HEADER_HEIGHT))
                        if (editingPage) {
                            Text(
                                stringResource(R.string.page_edit_hint),
                                Modifier.padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val listState = rememberLazyListState()
                        val drag = remember(listState) { CardDrag(listState) }
                        val cardIds by rememberUpdatedState(page.cards.map { it.id })
                        val shownCards = if (drag.cardId == null) {
                            page.cards
                        } else {
                            drag.order.mapNotNull { id -> page.cards.firstOrNull { it.id == id } }
                        }
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            state = listState,
                            contentPadding = PaddingValues(appearance.panelPaddingDp.dp),
                            verticalArrangement = Arrangement.spacedBy(appearance.cardGapDp.dp),
                        ) {
                            items(shownCards, key = { it.id }) { card ->
                                PanelCard(
                                    card = card,
                                    modifier = if (card.id == drag.cardId) {
                                        Modifier
                                            .zIndex(1f)
                                            .graphicsLayer { translationY = drag.offsetOf(card.id) }
                                    } else {
                                        Modifier.animateItem()
                                    },
                                    dragHandle = Modifier.pointerInput(card.id) {
                                        detectDragGestures(
                                            onDragStart = { drag.start(card.id, cardIds) },
                                            onDragEnd = {
                                                drag.end()?.let { (id, offset) -> store.update { it.moveCard(id, offset) } }
                                            },
                                            onDragCancel = drag::cancel,
                                            onDrag = { change, amount ->
                                                change.consume()
                                                drag.drag(amount.y)
                                            },
                                        )
                                    },
                                    editing = card.id == editingCard,
                                    pageEditing = editingPage,
                                    switches = switches,
                                    onStartEditing = { editingCard = card.id },
                                    onDoneEditing = { editingCard = null },
                                    onLaunch = { item ->
                                        when {
                                            item is ActionItem && item.action == Action.APP_DRAWER -> showingDrawer = true
                                            !launcher.launch(item) -> onClose()
                                        }
                                    },
                                    store = store,
                                    onAddItem = { addingToCard = card.id },
                                    onRenameItem = { index -> renamingItem = card.id to index },
                                    onClose = onClose,
                                    cardOpacity = 1f - page.style.cardTransparency.coerceIn(TRANSPARENCY_RANGE) / 100f,
                                )
                            }
                            if (shownCards.isEmpty() && !editingPage) {
                                item { EmptyPageHint() }
                            }
                            if (editingPage) {
                                // A new card opens for editing straight away.
                                fun addCard(card: Card) {
                                    store.update { it.addCard(pageIndex, card) }
                                    editingCard = card.id
                                }
                                item {
                                    PageEditor(
                                        canDelete = config.pages.size > 1,
                                        canMoveLeft = pageIndex > 0,
                                        canMoveRight = pageIndex < config.pages.size - 1,
                                        onAddGrid = { addCard(GridCard(ConfigStore.newId(), it)) },
                                        onAddNote = { addCard(NoteCard(ConfigStore.newId(), it)) },
                                        onAddText = { addCard(TextCard(ConfigStore.newId(), it)) },
                                        onAddHitokoto = { addCard(HitokotoCard(ConfigStore.newId(), it)) },
                                        onAddSystem = { kind, title ->
                                            addCard(SystemCard(ConfigStore.newId(), title, kind))
                                        },
                                        onAddUsage = { kind, title ->
                                            addCard(UsageCard(ConfigStore.newId(), title, kind))
                                        },
                                        onAddScreenTime = { addCard(ScreenTimeCard(ConfigStore.newId(), it)) },
                                        onAddWidget = {
                                            val intent = WidgetPickerActivity.intent(context, pageIndex)
                                            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                            onClose()
                                        },
                                        onAddPage = {
                                            store.update { it.addPage() }
                                            scope.launch { pagerState.animateScrollToPage(config.pages.size) }
                                        },
                                        onDeletePage = {
                                            store.update { it.removePage(pageIndex) }
                                            store.deleteUnused()
                                        },
                                        onMovePage = { offset ->
                                            store.update { it.movePage(pageIndex, offset) }
                                            scope.launch { pagerState.scrollToPage(pageIndex + offset) }
                                        },
                                    )
                                }
                                item {
                                    PageStyleEditor(pageIndex, page.style, store) {
                                        val intent = BackgroundPickerActivity.intent(context, pageIndex)
                                        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        onClose()
                                    }
                                }
                                item { SceneEditor(page.sceneApps) { pickingSceneApps = pageIndex } }
                            }
                        }
                    }
                }
            }
        }
        // The header takes the look of the page in view.
        PageTheme(config.pages.getOrNull(pagerState.currentPage)?.style ?: PageStyle(), baseTheme) {
            PanelHeader(
                pageLabel = if (config.pages.size > 1) "${pagerState.currentPage + 1} / ${config.pages.size}" else null,
                editing = editingPage || editingCard != null,
                onToggleEditing = {
                    if (editingPage || editingCard != null) {
                        editingPage = false
                        editingCard = null
                    } else {
                        editingPage = true
                    }
                },
                onOpenSettings = onOpenSettings,
            )
        }
        if (showingDrawer) {
            AppDrawer(
                onLaunch = { app ->
                    launcher.launch(app)
                    onClose()
                },
                onDismiss = { showingDrawer = false },
            )
        }
        pickingSceneApps?.let { pageIndex ->
            val close = { pickingSceneApps = null }
            BackHandler(onBack = close)
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
                AppChecklist(
                    title = stringResource(R.string.scene_apps),
                    selected = config.pages.getOrNull(pageIndex)?.sceneApps.orEmpty(),
                    onDone = { apps ->
                        store.update { it.setSceneApps(pageIndex, apps) }
                        close()
                    },
                    onDismiss = close,
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
        }
        addingToCard?.let { cardId ->
            AddItemSheet(
                onAdd = { items ->
                    store.update { it.addItems(cardId, items) }
                    addingToCard = null
                },
                onDismiss = { addingToCard = null },
            )
        }
        renamingItem?.let { (cardId, index) ->
            val card = config.pages.flatMap { it.cards }.firstOrNull { it.id == cardId } as? GridCard
            val item = card?.items?.getOrNull(index)
            if (item != null) {
                RenameItemDialog(
                    label = item.label,
                    onRename = { label ->
                        store.update { it.renameItem(cardId, index, label) }
                        renamingItem = null
                    },
                    onDismiss = { renamingItem = null },
                )
            }
        }
    }
}

/** Asks for an icon's new name over the panel; the panel is a window of its own, so this is not a dialog window. */
@Composable
private fun RenameItemDialog(label: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(label) }
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Card(
            Modifier
                .fillMaxWidth()
                .padding(24.dp)
                // Taps on the card stay on it instead of closing it.
                .pointerInput(Unit) { detectTapGestures() },
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.item_rename), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onRename(text.trim()) }),
                )
                Row(Modifier.align(Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = { onRename(text.trim()) }) { Text(stringResource(R.string.done)) }
                }
            }
        }
    }
}

/** The title row above the pages, with the page number and the edit and settings buttons. */
@Composable
private fun PanelHeader(pageLabel: String?, editing: Boolean, onToggleEditing: () -> Unit, onOpenSettings: () -> Unit) {
    val extras = LocalThemeExtras.current
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .height(HEADER_HEIGHT)
            .drawBehind { if (extras.stars) drawStars() }
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.app_name),
            style = extras.titleBrush?.let { MaterialTheme.typography.titleLarge.copy(brush = it) }
                ?: MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.weight(1f))
        if (pageLabel != null) {
            Text(
                pageLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onToggleEditing) {
            Icon(
                if (editing) Icons.Default.Check else Icons.Default.Edit,
                contentDescription = stringResource(if (editing) R.string.edit_done else R.string.edit_start),
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.panel_open_settings))
        }
    }
}

private val HEADER_HEIGHT = 56.dp

/** The page's color (the theme's, or the one set in the settings) or picture, as see-through as the page is set. */
@Composable
private fun PageBackground(style: PageStyle) {
    val color = LocalAppearance.current.backgroundColor?.let(::appearanceColor) ?: MaterialTheme.colorScheme.surfaceContainer
    val picture = style.background?.let { rememberBackground(it) }
    val opacity = 1f - style.backgroundTransparency.coerceIn(TRANSPARENCY_RANGE) / 100f
    Canvas(Modifier.fillMaxSize()) { drawPageBackground(color, picture, style.backgroundFit, opacity) }
}

@Composable
private fun EmptyPageHint() {
    Text(
        stringResource(R.string.panel_empty_page),
        Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PanelCard(
    card: Card,
    modifier: Modifier,
    dragHandle: Modifier,
    /** Whether this card is being edited. */
    editing: Boolean,
    /** Whether its page is being edited, which shows every card's title row with buttons to edit, delete and move. */
    pageEditing: Boolean,
    switches: Set<Action>,
    onStartEditing: () -> Unit,
    onDoneEditing: () -> Unit,
    onLaunch: (Item) -> Unit,
    store: ConfigStore,
    onAddItem: () -> Unit,
    onRenameItem: (index: Int) -> Unit,
    onClose: () -> Unit,
    cardOpacity: Float,
) {
    val padding = LocalAppearance.current.cardPaddingDp
    // Outside edit mode a text card is only its text, straight on the page unless it asks for a card.
    if (card is TextCard && !editing && !pageEditing && !card.cardBackground) {
        CardText(card, onStartEditing, modifier.padding(horizontal = padding.dp))
        return
    }
    val colors = MaterialTheme.colorScheme
    Card(
        modifier.fillMaxWidth(),
        // The text color is given too: it cannot be worked out from a see-through card color.
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceContainerHighest.copy(alpha = cardOpacity),
            contentColor = colors.onSurface,
        ),
        border = LocalThemeExtras.current.cardBorder,
    ) {
        Column(Modifier.padding(horizontal = padding.dp, vertical = (padding * 2 / 3).dp)) {
            if (editing || pageEditing || card.showTitle) {
                CardHeader(card, editing, pageEditing, dragHandle, onStartEditing, onDoneEditing, store)
            }
            when (card) {
                is GridCard -> {
                    if (editing) {
                        ColumnsStepper(card.columns) { n -> store.update { it.setColumns(card.id, n) } }
                        Hint(stringResource(R.string.edit_drag_hint))
                    }
                    ItemGrid(
                        items = card.items,
                        columns = card.columns,
                        editing = editing,
                        switches = switches,
                        onLaunch = onLaunch,
                        onRemove = { index -> store.update { it.removeItem(card.id, index) } },
                        onAdd = onAddItem,
                        onMove = { from, to -> store.update { it.moveItem(card.id, from, to) } },
                        onEdit = onRenameItem,
                        showLabels = card.showLabels,
                        rowGap = card.rowGapDp.dp,
                        columnGap = card.columnGapDp?.dp,
                    )
                }
                is NoteCard -> NoteField(card, store)
                is TextCard -> if (editing) TextCardEditor(card, store) else CardText(card, onStartEditing)
                is HitokotoCard -> HitokotoContent(onStartEditing)
                is SystemCard -> SystemCardContent(card.kind, onClose)
                is UsageCard -> UsageCardContent(card, editing, store, onLaunch, onClose)
                is ScreenTimeCard -> ScreenTimeContent(onLaunch, onClose)
                is WidgetCard -> WidgetCardContent(card, editing, onClose)
            }
            if (editing) CardStyleControls(card, store)
        }
    }
}

/** Recently or frequently used apps; asks for usage access first. */
@Composable
private fun UsageCardContent(
    card: UsageCard,
    editing: Boolean,
    store: ConfigStore,
    onLaunch: (Item) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    if (editing) ColumnsStepper(card.columns) { n -> store.update { it.setColumns(card.id, n) } }
    if (!UsageApps.hasAccess(context)) {
        Box(Modifier.padding(bottom = 12.dp)) {
            PermissionHint(R.string.needs_usage_access) {
                context.startActivity(UsageApps.settingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                onClose()
            }
        }
        return
    }
    val count = card.columns * USAGE_ROWS
    val apps by produceState<List<AppItem>?>(null, card.kind, count) {
        value = withContext(Dispatchers.IO) { UsageApps.load(context, card.kind, count) }
    }
    val list = apps ?: return
    if (list.isEmpty()) {
        Text(
            stringResource(R.string.usage_empty),
            Modifier.padding(bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    ItemGrid(
        items = list,
        columns = card.columns,
        editing = false,
        switches = emptySet(),
        onLaunch = onLaunch,
        onRemove = {},
        onAdd = {},
        showLabels = card.showLabels,
        rowGap = card.rowGapDp.dp,
        columnGap = card.columnGapDp?.dp,
    )
}

private const val USAGE_ROWS = 2

/** Another app's widget. A tap on it usually opens that app, so the panel closes to show it. */
@Composable
private fun WidgetCardContent(card: WidgetCard, editing: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val info = remember(card.widgetId) { AppWidgetManager.getInstance(context).getAppWidgetInfo(card.widgetId) }
    if (info == null) {
        Text(
            stringResource(R.string.widget_missing),
            Modifier.padding(bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(card.heightDp.dp)
            .padding(bottom = 8.dp)
            .then(if (editing) Modifier else Modifier.observeTaps(onClose)),
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        AndroidView(
            factory = { WidgetHost.get(it).createView(it, card.widgetId, info) },
            modifier = Modifier.fillMaxSize(),
            update = { it.updateSize(width, height) },
        )
    }
}

/** Calls [onTap] after a tap anywhere inside, without taking the touch from the views underneath. */
private fun Modifier.observeTaps(onTap: () -> Unit) = pointerInput(onTap) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            val moved = (change.position - down.position).getDistance() > viewConfiguration.touchSlop
            val held = change.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis
            if (moved || held) break
            if (!change.pressed) {
                onTap()
                break
            }
        }
    }
}

@Composable
private fun ColumnsStepper(columns: Int, onChange: (Int) -> Unit) {
    Stepper(stringResource(R.string.card_columns, columns), columns, COLUMN_RANGE, onChange = onChange)
}

/** A label with "fewer" and "more" buttons that change [value] by [step] within [range]. */
@Composable
private fun Stepper(text: String, value: Int, range: IntRange, step: Int = 1, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { onChange(value - step) }, enabled = value > range.first) {
            Text(stringResource(R.string.card_columns_fewer))
        }
        TextButton(onClick = { onChange(value + step) }, enabled = value < range.last) {
            Text(stringResource(R.string.card_columns_more))
        }
    }
}

/**
 * A card's title row. While the card is edited it has buttons to rename, delete, move and finish; while only its
 * page is, buttons to edit, delete and move it. A long press on the title edits the card.
 */
@Composable
private fun CardHeader(
    card: Card,
    editing: Boolean,
    pageEditing: Boolean,
    dragHandle: Modifier,
    onStartEditing: () -> Unit,
    onDoneEditing: () -> Unit,
    store: ConfigStore,
) {
    var renaming by remember(card.id) { mutableStateOf(false) }
    if (editing && renaming) {
        var title by remember(card.id) { mutableStateOf(card.title) }
        val commit = {
            store.update { it.renameCard(card.id, title.trim()) }
            renaming = false
        }
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            trailingIcon = {
                IconButton(onClick = commit) {
                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.edit_done))
                }
            },
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            card.title,
            Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
                .pointerInput(Unit) { detectTapGestures(onLongPress = { onStartEditing() }) },
            style = MaterialTheme.typography.titleMedium,
        )
        if (editing) {
            SmallIconButton(rememberVectorPainter(Icons.Default.Edit), R.string.card_rename) { renaming = true }
        } else if (pageEditing) {
            SmallIconButton(painterResource(R.drawable.ic_action_tune), R.string.card_edit, onStartEditing)
        }
        if (editing || pageEditing) {
            SmallIconButton(rememberVectorPainter(Icons.Default.Delete), R.string.card_delete) {
                store.update { it.removeCard(card.id) }
                if (card is WidgetCard) store.deleteUnused()
            }
            Icon(
                Icons.Default.Menu,
                contentDescription = stringResource(R.string.card_drag),
                modifier = dragHandle
                    .size(36.dp)
                    .padding(8.dp),
            )
        }
        if (editing) SmallIconButton(rememberVectorPainter(Icons.Default.Check), R.string.edit_done, onDoneEditing)
    }
}

@Composable
private fun SmallIconButton(icon: Painter, description: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = stringResource(description), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ItemGrid(
    items: List<Item>,
    columns: Int,
    editing: Boolean,
    switches: Set<Action>,
    onLaunch: (Item) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit = { _, _ -> },
    /** Called with an icon's index when it is tapped in edit mode. */
    onEdit: (Int) -> Unit = {},
    showLabels: Boolean = true,
    rowGap: Dp = DEFAULT_ROW_GAP.dp,
    /** Space between icons side by side, the icons filling the rest of their cells; null sizes them by [columns]. */
    columnGap: Dp? = null,
) {
    // In edit mode an icon can be long-pressed and dragged: dragFrom is its index, dragOffset how far it has moved.
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var gridSize by remember { mutableStateOf(IntSize.Zero) }
    val itemCount by rememberUpdatedState(items.size)
    val move by rememberUpdatedState(onMove)
    val haptics = LocalHapticFeedback.current

    // Cells are indexes into items; -1 is the "add" cell shown in edit mode.
    val cellCount = items.size + if (editing) 1 else 0
    if (cellCount == 0) {
        Text(
            stringResource(R.string.card_empty),
            Modifier.padding(bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    fun cellWidth() = gridSize.width.toFloat() / columns
    fun cellHeight(): Float {
        val rows = (itemCount + (if (editing) 1 else 0) + columns - 1) / columns
        return gridSize.height.toFloat() / rows
    }
    fun cellAt(index: Int) = Offset(index % columns * cellWidth(), index / columns * cellHeight())
    fun dropIndex(from: Int) =
        gridDropIndex(from, dragOffset.x, dragOffset.y, cellWidth(), cellHeight(), columns, itemCount)

    val from = dragFrom
    val target = from?.let(::dropIndex)
    val order = if (from != null && target != null) items.indices.toList().move(from, target) else items.indices.toList()
    val cells = order + if (editing) listOf(-1) else emptyList()
    // Each cell takes half the gap above and below its icon, so rows stay equally tall for dragging.
    val cellPadding = rowGap / 2
    val dragModifier = if (!editing) Modifier else Modifier.pointerInput(columns) {
        detectDragGesturesAfterLongPress(
            onDragStart = { position ->
                val index = (position.y / cellHeight()).toInt() * columns + (position.x / cellWidth()).toInt()
                if (index in 0 until itemCount) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    dragFrom = index
                    dragOffset = Offset.Zero
                }
            },
            onDragEnd = {
                dragFrom?.let { start ->
                    val end = dropIndex(start)
                    if (end != start) move(start, end)
                }
                dragFrom = null
            },
            onDragCancel = { dragFrom = null },
            onDrag = { change, amount ->
                if (dragFrom != null) {
                    change.consume()
                    dragOffset += amount
                }
            },
        )
    }
    BoxWithConstraints {
        val iconSize = gridIconSize(maxWidth / columns, columns, columnGap)
        Column(
            Modifier
                .onSizeChanged { gridSize = it }
                .then(dragModifier),
        ) {
            cells.chunked(columns).forEachIndexed { rowIndex, row ->
                // The dragged icon's row is drawn last so the icon stays above the other rows.
                val holdsDragged = from != null && from in row
                Row(Modifier.zIndex(if (holdsDragged) 1f else 0f)) {
                    row.forEachIndexed { columnIndex, index ->
                        val cellModifier = Modifier.weight(1f)
                        if (index < 0) {
                            AddCell(iconSize, cellPadding, onAdd, cellModifier)
                        } else {
                            val item = items[index]
                            val action = (item as? ActionItem)?.action
                            val dragged = index == from
                            ItemCell(
                                item = item,
                                iconSize = iconSize,
                                switchedOn = action?.takeIf { it in SWITCH_ACTIONS }?.let { it in switches },
                                editing = editing,
                                showLabel = showLabels,
                                verticalPadding = cellPadding,
                                onClick = {
                                    when {
                                        !editing -> onLaunch(item)
                                        // Letting go of an icon picked up to drag is not a tap on it.
                                        dragFrom == null -> onEdit(index)
                                    }
                                },
                                onRemove = { onRemove(index) },
                                modifier = if (!dragged) cellModifier else {
                                    // Drawn in the cell it would drop into, shifted back under the finger.
                                    val shift = cellAt(index) + dragOffset - cellAt(rowIndex * columns + columnIndex)
                                    cellModifier
                                        .zIndex(1f)
                                        .graphicsLayer {
                                            translationX = shift.x
                                            translationY = shift.y
                                            scaleX = DRAG_SCALE
                                            scaleY = DRAG_SCALE
                                        }
                                },
                            )
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private const val DRAG_SCALE = 1.1f

/** Icons fill their cells but for [columnGap]; without one they have a set size for the number of [columns]. */
private fun gridIconSize(cellWidth: Dp, columns: Int, columnGap: Dp?): Dp = when {
    columnGap != null -> (cellWidth - columnGap).coerceAtLeast(MIN_ICON_SIZE)
    columns >= 6 -> 36.dp
    columns == 5 -> 40.dp
    else -> 44.dp
}

private val MIN_ICON_SIZE = 16.dp

@Composable
private fun ItemCell(
    item: Item,
    iconSize: Dp,
    switchedOn: Boolean?,
    editing: Boolean,
    showLabel: Boolean,
    verticalPadding: Dp,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = verticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            ItemIcon(item, iconSize, switchedOn)
            if (editing) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.item_remove),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onError,
                    )
                }
            }
        }
        if (showLabel) {
            Text(
                item.label,
                Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = LocalAppearance.current.labelSizeSp.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AddCell(iconSize: Dp, verticalPadding: Dp, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = verticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(iconSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
        }
        Text(
            stringResource(R.string.item_add),
            Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * App or link icon when one can be loaded, otherwise a round badge with a symbol or the label's first letter.
 * [switchedOn] colors a switch's badge by its state; null shows the plain badge.
 */
@Composable
fun ItemIcon(item: Item, size: Dp, switchedOn: Boolean? = null) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val key = InstalledApps.iconKey(item)
    val bitmap by produceState(key?.let { InstalledApps.cachedIcon(it, sizePx) }, key, sizePx) {
        // The state outlives a change of icon or size, so it starts over from the cache.
        value = key?.let { InstalledApps.cachedIcon(it, sizePx) }
        if (value == null && key != null) {
            value = withContext(Dispatchers.IO) {
                when (item) {
                    is AppItem -> InstalledApps.appIcon(context, item.packageName, item.activityName, sizePx)
                    is LinkItem -> InstalledApps.linkIcon(context, item.uri, sizePx)
                    is ActionItem -> null
                }
            }
        }
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(loaded, contentDescription = null, modifier = Modifier.size(size))
    } else {
        BadgeIcon(item, size, switchedOn)
    }
}

@Composable
private fun BadgeIcon(item: Item, size: Dp, switchedOn: Boolean?) {
    val colors = MaterialTheme.colorScheme
    val appearance = LocalAppearance.current
    // A color set in the settings, with text that reads on it.
    fun custom(hex: String) = appearanceColor(hex).let { it to contentColorOn(it) }
    val (background, content) = when (switchedOn) {
        null -> colors.primaryContainer to colors.onPrimaryContainer
        true -> appearance.switchOnColor?.let(::custom) ?: (colors.primary to colors.onPrimary)
        false -> appearance.switchOffColor?.let(::custom) ?: (colors.surfaceVariant to colors.onSurfaceVariant)
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (item is ActionItem) {
            Icon(painterResource(item.action.iconRes), contentDescription = null, tint = content)
        } else {
            Text(item.label.take(1), style = MaterialTheme.typography.titleMedium, color = content)
        }
    }
}

@Composable
private fun NoteField(card: NoteCard, store: ConfigStore) {
    var text by remember(card.id) { mutableStateOf(card.text) }
    TextField(
        value = text,
        onValueChange = {
            text = it
            store.update { c -> c.setNoteText(card.id, it) }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .cardHeight(card.heightDp),
        placeholder = { Text(stringResource(R.string.note_placeholder)) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
    )
}

/** A fixed height, or the content's own height for [AUTO_HEIGHT]. */
private fun Modifier.cardHeight(heightDp: Int) = if (heightDp == AUTO_HEIGHT) this else height(heightDp.dp)

/** A text card's text, in the card's size, style and color; a long press on it starts edit mode. */
@Composable
private fun CardText(card: TextCard, onStartEditing: () -> Unit, modifier: Modifier = Modifier) {
    val empty = card.text.isEmpty()
    Box(
        modifier
            .fillMaxWidth()
            .cardHeight(card.heightDp)
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onStartEditing() }) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            if (empty) stringResource(R.string.text_card_empty) else card.text,
            Modifier.fillMaxWidth(),
            color = if (empty) MaterialTheme.colorScheme.onSurfaceVariant else textColor(card),
            overflow = TextOverflow.Ellipsis,
            style = if (empty) MaterialTheme.typography.bodyMedium else cardTextStyle(card),
        )
    }
}

/** A text card in edit mode: its text in a field, in the card's size and style. */
@Composable
private fun TextCardEditor(card: TextCard, store: ConfigStore) {
    var text by remember(card.id) { mutableStateOf(card.text) }
    TextField(
        value = text,
        onValueChange = {
            text = it
            store.update { c -> c.updateTextCard(card.id) { t -> t.copy(text = it) } }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        // The theme's text color, since the card's own may not show on the card behind it.
        textStyle = cardTextStyle(card),
        placeholder = { Text(stringResource(R.string.text_card_hint)) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
    )
}

@Composable
private fun cardTextStyle(card: TextCard) = MaterialTheme.typography.bodyLarge.copy(
    fontSize = card.textSize.sp,
    lineHeight = (card.textSize * 1.4f).sp,
    fontWeight = if (card.bold) FontWeight.Bold else FontWeight.Normal,
    fontStyle = if (card.italic) FontStyle.Italic else FontStyle.Normal,
    textAlign = if (card.centered) TextAlign.Center else TextAlign.Start,
)

/** The card's own text color, or the theme's when it has none or it is not a color. */
@Composable
private fun textColor(card: TextCard): Color {
    val own = card.color?.let {
        try {
            Color(it.toColorInt())
        } catch (e: IllegalArgumentException) {
            null
        }
    }
    return own ?: MaterialTheme.colorScheme.onSurface
}

/**
 * A sentence from hitokoto.cn and where it is from. It starts with the last one fetched while the app runs and asks
 * for a new one when that is a minute old; a tap asks for another, a long press starts edit mode.
 */
@Composable
private fun HitokotoContent(onStartEditing: () -> Unit) {
    var sentence by remember { mutableStateOf(HitokotoCache.last) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun load() {
        if (loading) return
        loading = true
        failed = false
        scope.launch {
            val fetched = withContext(Dispatchers.IO) { fetchHitokoto() }
            if (fetched != null) {
                HitokotoCache.put(fetched, System.currentTimeMillis())
                sentence = fetched
            }
            failed = fetched == null
            loading = false
        }
    }
    LaunchedEffect(Unit) { if (HitokotoCache.isStale(System.currentTimeMillis())) load() }
    val hintStyle = MaterialTheme.typography.bodySmall
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onTap = { load() }, onLongPress = { onStartEditing() }) }
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val shown = sentence
        if (shown == null) {
            Text(
                stringResource(if (failed) R.string.hitokoto_failed else R.string.hitokoto_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = hintColor,
            )
        } else {
            Text(shown.hitokoto, style = MaterialTheme.typography.bodyLarge)
            shown.attribution()?.let {
                Text(it, Modifier.fillMaxWidth(), style = hintStyle, color = hintColor, textAlign = TextAlign.End)
            }
            if (failed) Text(stringResource(R.string.hitokoto_failed), style = hintStyle, color = hintColor)
        }
    }
}

@Composable
private fun PageEditor(
    canDelete: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onAddGrid: (String) -> Unit,
    onAddNote: (String) -> Unit,
    onAddText: (String) -> Unit,
    onAddHitokoto: (String) -> Unit,
    onAddSystem: (SystemKind, String) -> Unit,
    onAddUsage: (UsageKind, String) -> Unit,
    onAddScreenTime: (String) -> Unit,
    onAddWidget: () -> Unit,
    onAddPage: () -> Unit,
    onDeletePage: () -> Unit,
    onMovePage: (Int) -> Unit,
) {
    val gridTitle = stringResource(R.string.new_grid_card_title)
    val noteTitle = stringResource(R.string.default_card_note)
    val textTitle = stringResource(R.string.default_card_text)
    val hitokotoTitle = stringResource(R.string.default_card_hitokoto)
    val systemTitles = SystemKind.entries.associateWith { stringResource(it.labelRes) }
    val recentTitle = stringResource(R.string.usage_recent)
    val frequentTitle = stringResource(R.string.usage_frequent)
    val screenTimeTitle = stringResource(R.string.default_card_screen_time)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ButtonRow(
            R.string.page_add_grid_card to { onAddGrid(gridTitle) },
            R.string.page_add_note_card to { onAddNote(noteTitle) },
        )
        SystemKind.entries.chunked(2).forEach { (first, second) ->
            ButtonRow(
                first.addLabelRes to { onAddSystem(first, systemTitles.getValue(first)) },
                second.addLabelRes to { onAddSystem(second, systemTitles.getValue(second)) },
            )
        }
        ButtonRow(
            R.string.page_add_recent_card to { onAddUsage(UsageKind.RECENT, recentTitle) },
            R.string.page_add_frequent_card to { onAddUsage(UsageKind.FREQUENT, frequentTitle) },
        )
        ButtonRow(
            R.string.page_add_text_card to { onAddText(textTitle) },
            R.string.page_add_hitokoto_card to { onAddHitokoto(hitokotoTitle) },
        )
        ButtonRow(
            R.string.page_add_screen_time_card to { onAddScreenTime(screenTimeTitle) },
            R.string.page_add_widget_card to onAddWidget,
        )
        ButtonRow(
            R.string.page_add to onAddPage,
            R.string.page_delete to onDeletePage.takeIf { canDelete },
        )
        ButtonRow(
            R.string.page_move_left to { onMovePage(-1) }.takeIf { canMoveLeft },
            R.string.page_move_right to { onMovePage(1) }.takeIf { canMoveRight },
        )
    }
}

private val SystemKind.addLabelRes: Int
    get() = when (this) {
        SystemKind.BATTERY -> R.string.page_add_battery_card
        SystemKind.BRIGHTNESS -> R.string.page_add_brightness_card
        SystemKind.VOLUME -> R.string.page_add_volume_card
        SystemKind.STORAGE -> R.string.page_add_storage_card
    }

/** Equal-width buttons; a null action shows the button disabled. */
@Composable
private fun ButtonRow(vararg buttons: Pair<Int, (() -> Unit)?>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        buttons.forEach { (label, action) ->
            OutlinedButton(
                onClick = { action?.invoke() },
                enabled = action != null,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
