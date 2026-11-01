package io.github.alstarknight.slidex

fun PanelConfig.addPage(): PanelConfig = copy(pages = pages + Page())

/** Removes a page, but always keeps at least one. */
fun PanelConfig.removePage(index: Int): PanelConfig =
    if (pages.size <= 1) this else copy(pages = pages.filterIndexed { i, _ -> i != index })

fun PanelConfig.movePage(index: Int, offset: Int): PanelConfig = copy(pages = pages.move(index, index + offset))

fun PanelConfig.setPageStyle(index: Int, transform: (PageStyle) -> PageStyle): PanelConfig =
    copy(pages = pages.mapIndexed { i, page -> if (i == index) page.copy(style = transform(page.style)) else page })

/** Makes the panel open on the page at [index] in [apps]; an app can open only one page, so other pages drop it. */
fun PanelConfig.setSceneApps(index: Int, apps: Set<String>): PanelConfig =
    copy(pages = pages.mapIndexed { i, page -> page.copy(sceneApps = if (i == index) apps else page.sceneApps - apps) })

/** The page the panel opens on while [app] is in front: the one that lists it, or else the first. */
fun PanelConfig.scenePage(app: String?): Int =
    pages.indexOfFirst { app != null && app in it.sceneApps }.coerceAtLeast(0)

/** Pictures still used as a page background; any other saved picture can be deleted. */
fun PanelConfig.backgrounds(): Set<String> = pages.mapNotNullTo(mutableSetOf()) { it.style.background }

fun PanelConfig.addCard(pageIndex: Int, card: Card): PanelConfig =
    copy(pages = pages.mapIndexed { i, page -> if (i == pageIndex) page.copy(cards = page.cards + card) else page })

fun PanelConfig.removeCard(cardId: String): PanelConfig =
    copy(pages = pages.map { page -> page.copy(cards = page.cards.filterNot { it.id == cardId }) })

/** Moves a card up (negative offset) or down within its page. */
fun PanelConfig.moveCard(cardId: String, offset: Int): PanelConfig =
    copy(pages = pages.map { page ->
        val index = page.cards.indexOfFirst { it.id == cardId }
        if (index < 0) page else page.copy(cards = page.cards.move(index, index + offset))
    })

fun PanelConfig.renameCard(cardId: String, title: String): PanelConfig = mapCard(cardId) {
    when (it) {
        is GridCard -> it.copy(title = title)
        is NoteCard -> it.copy(title = title)
        is TextCard -> it.copy(title = title)
        is HitokotoCard -> it.copy(title = title)
        is SystemCard -> it.copy(title = title)
        is UsageCard -> it.copy(title = title)
        is ScreenTimeCard -> it.copy(title = title)
        is WidgetCard -> it.copy(title = title)
    }
}

fun PanelConfig.setShowTitle(cardId: String, show: Boolean): PanelConfig = mapCard(cardId) {
    when (it) {
        is GridCard -> it.copy(showTitle = show)
        is NoteCard -> it.copy(showTitle = show)
        is HitokotoCard -> it.copy(showTitle = show)
        is SystemCard -> it.copy(showTitle = show)
        is UsageCard -> it.copy(showTitle = show)
        is ScreenTimeCard -> it.copy(showTitle = show)
        is WidgetCard -> it.copy(showTitle = show)
        is TextCard -> it
    }
}

/** Shows or hides the names under the icons of an icon or usage card. */
fun PanelConfig.setShowLabels(cardId: String, show: Boolean): PanelConfig = mapCard(cardId) {
    when (it) {
        is GridCard -> it.copy(showLabels = show)
        is UsageCard -> it.copy(showLabels = show)
        else -> it
    }
}

/** Sets the space between rows of icons of an icon or usage card. */
fun PanelConfig.setRowGap(cardId: String, rowGapDp: Int): PanelConfig = mapCard(cardId) {
    when (it) {
        is GridCard -> it.copy(rowGapDp = rowGapDp.coerceIn(ROW_GAP_RANGE))
        is UsageCard -> it.copy(rowGapDp = rowGapDp.coerceIn(ROW_GAP_RANGE))
        else -> it
    }
}

/** Sets the space between icons side by side on an icon or usage card; null sizes the icons by their number. */
fun PanelConfig.setColumnGap(cardId: String, columnGapDp: Int?): PanelConfig = mapCard(cardId) {
    val gap = columnGapDp?.coerceIn(COLUMN_GAP_RANGE)
    when (it) {
        is GridCard -> it.copy(columnGapDp = gap)
        is UsageCard -> it.copy(columnGapDp = gap)
        else -> it
    }
}

/** Sets the height of a text, note or widget card; other cards always fit their content. */
fun PanelConfig.setCardHeight(cardId: String, heightDp: Int): PanelConfig = mapCard(cardId) {
    when (it) {
        is TextCard -> it.copy(heightDp = heightDp.coerceIn(CARD_HEIGHT_RANGE))
        is NoteCard -> it.copy(heightDp = heightDp.coerceIn(CARD_HEIGHT_RANGE))
        is WidgetCard -> it.copy(heightDp = heightDp.coerceIn(WIDGET_HEIGHT_RANGE))
        else -> it
    }
}

/** Widget ids still used by a card; any other id the panel's widget host holds can be freed. */
fun PanelConfig.widgetIds(): Set<Int> =
    pages.flatMap { it.cards }.filterIsInstance<WidgetCard>().mapTo(mutableSetOf()) { it.widgetId }

fun PanelConfig.setColumns(cardId: String, columns: Int): PanelConfig = mapCard(cardId) {
    when (it) {
        is GridCard -> it.copy(columns = columns.coerceIn(COLUMN_RANGE))
        is UsageCard -> it.copy(columns = columns.coerceIn(COLUMN_RANGE))
        else -> it
    }
}

fun PanelConfig.addItems(cardId: String, items: List<Item>): PanelConfig = mapCard(cardId) {
    if (it is GridCard) it.copy(items = it.items + items) else it
}

/** Moves the icon at [from] to [to] within a grid card. */
fun PanelConfig.moveItem(cardId: String, from: Int, to: Int): PanelConfig = mapCard(cardId) {
    if (it is GridCard) it.copy(items = it.items.move(from, to)) else it
}

/** Changes the name shown under one icon of a grid card. */
fun PanelConfig.renameItem(cardId: String, index: Int, label: String): PanelConfig = mapCard(cardId) { card ->
    if (card !is GridCard) return@mapCard card
    card.copy(items = card.items.mapIndexed { i, item ->
        if (i != index) {
            item
        } else {
            when (item) {
                is AppItem -> item.copy(label = label)
                is LinkItem -> item.copy(label = label)
                is ActionItem -> item.copy(label = label)
            }
        }
    })
}

fun PanelConfig.removeItem(cardId: String, index: Int): PanelConfig = mapCard(cardId) {
    if (it is GridCard) it.copy(items = it.items.filterIndexed { i, _ -> i != index }) else it
}

fun PanelConfig.setNoteText(cardId: String, text: String): PanelConfig = mapCard(cardId) {
    if (it is NoteCard) it.copy(text = text) else it
}

/** Changes a text card's text or look; the size is kept within [TEXT_SIZE_RANGE]. */
fun PanelConfig.updateTextCard(cardId: String, transform: (TextCard) -> TextCard): PanelConfig = mapCard(cardId) {
    if (it is TextCard) transform(it).let { card -> card.copy(textSize = card.textSize.coerceIn(TEXT_SIZE_RANGE)) } else it
}

private fun PanelConfig.mapCard(cardId: String, transform: (Card) -> Card): PanelConfig =
    copy(pages = pages.map { page -> page.copy(cards = page.cards.map { if (it.id == cardId) transform(it) else it }) })

/** Moves the element at [from] to [to]; out-of-range moves leave the list unchanged. */
internal fun <T> List<T>.move(from: Int, to: Int): List<T> =
    if (from !in indices || to !in indices) this else toMutableList().apply { add(to, removeAt(from)) }
