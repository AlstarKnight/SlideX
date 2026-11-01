package io.github.alstarknight.slidex

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigTest {

    private val note = NoteCard(id = "n", title = "便签", text = "hello")
    private val grid = GridCard(
        id = "g",
        title = "应用",
        items = listOf(
            AppItem("微信", "com.tencent.mm", "com.tencent.mm.ui.LauncherUI"),
            LinkItem("扫一扫", "alipays://platformapi/startapp?saId=10000007"),
            ActionItem("锁屏", Action.LOCK_SCREEN),
        ),
    )
    private val config = PanelConfig(pages = listOf(Page(listOf(grid, note)), Page()))

    @Test
    fun jsonRoundTripKeepsEverything() {
        assertEquals(config, parseConfig(config.toJson()))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val text = """{"version":1,"pages":[{"cards":[{"type":"note","id":"n","title":"t","text":"x","color":"red"}]}],"extra":1}"""
        assertEquals(PanelConfig(pages = listOf(Page(listOf(NoteCard("n", "t", "x"))))), parseConfig(text))
    }

    @Test
    fun configWithoutPagesGetsOneEmptyPage() {
        assertEquals(listOf(Page()), parseConfig("""{"pages":[]}""").pages)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidJsonIsRejected() {
        parseConfig("not json")
    }

    @Test
    fun pagesCanBeAddedRemovedAndMoved() {
        val three = config.addPage()
        assertEquals(3, three.pages.size)
        assertEquals(listOf(Page(), Page(listOf(grid, note)), Page()), three.movePage(0, 1).pages)
        assertEquals(three.pages, three.movePage(2, 1).pages)
        assertEquals(listOf(Page(), Page()), three.removePage(0).pages)
    }

    @Test
    fun lastPageIsNeverRemoved() {
        val single = PanelConfig()
        assertEquals(single, single.removePage(0))
    }

    @Test
    fun cardsCanBeAddedMovedRenamedAndRemoved() {
        val added = config.addCard(1, NoteCard("m", "新"))
        assertEquals(listOf(NoteCard("m", "新")), added.pages[1].cards)
        assertEquals(listOf(note, grid), config.moveCard("g", 1).pages[0].cards)
        assertEquals(config, config.moveCard("g", -1))
        assertEquals("工具", config.renameCard("g", "工具").pages[0].cards[0].title)
        assertEquals(listOf(note), config.removeCard("g").pages[0].cards)
    }

    @Test
    fun gridItemsCanBeAddedMovedAndRemoved() {
        val link = LinkItem("百度", "https://www.baidu.com")
        val screenshot = ActionItem("截屏", Action.SCREENSHOT)
        val added = config.addItems("g", listOf(link, screenshot))
        assertEquals(grid.items + link + screenshot, (added.pages[0].cards[0] as GridCard).items)
        val moved = config.moveItem("g", 2, 0)
        assertEquals(listOf(grid.items[2], grid.items[0], grid.items[1]), (moved.pages[0].cards[0] as GridCard).items)
        assertEquals(config, config.moveItem("g", 0, 5))
        val removed = config.removeItem("g", 0)
        assertEquals(grid.items.drop(1), (removed.pages[0].cards[0] as GridCard).items)
        assertEquals(config, config.addItems("n", listOf(link)))
    }

    @Test
    fun gridItemsCanBeRenamed() {
        val renamed = config.renameItem("g", 1, "支付宝扫码").pages[0].cards[0] as GridCard
        assertEquals(listOf("微信", "支付宝扫码", "锁屏"), renamed.items.map { it.label })
        assertEquals(grid.items[1].let { (it as LinkItem).uri }, (renamed.items[1] as LinkItem).uri)
        assertEquals(config, config.renameItem("n", 0, "x"))
    }

    @Test
    fun titlesAndLabelsCanBeHidden() {
        val hidden = config.setShowTitle("g", false).setShowLabels("g", false).setShowTitle("n", false)
        val hiddenGrid = hidden.pages[0].cards[0] as GridCard
        assertEquals(false, hiddenGrid.showTitle)
        assertEquals(false, hiddenGrid.showLabels)
        assertEquals(false, hidden.pages[0].cards[1].showTitle)
        assertEquals(hidden, parseConfig(hidden.toJson()))
        assertEquals(config, config.setShowLabels("n", false))
        // A text card only ever shows its text.
        val withText = config.addCard(1, TextCard("t", "文字"))
        assertEquals(withText, withText.setShowTitle("t", true))
        assertEquals(false, withText.pages[1].cards[0].showTitle)
    }

    @Test
    fun noteTextCanBeChanged() {
        assertEquals("bye", (config.setNoteText("n", "bye").pages[0].cards[1] as NoteCard).text)
    }

    @Test
    fun gridsSavedBeforeColumnsExistedGetFour() {
        val text = """{"pages":[{"cards":[{"type":"grid","id":"g","title":"t"}]}]}"""
        assertEquals(DEFAULT_COLUMNS, (parseConfig(text).pages[0].cards[0] as GridCard).columns)
    }

    @Test
    fun columnsStayInRange() {
        fun columnsAfter(n: Int) = (config.setColumns("g", n).pages[0].cards[0] as GridCard).columns
        assertEquals(5, columnsAfter(5))
        assertEquals(COLUMN_RANGE.last, columnsAfter(9))
        assertEquals(COLUMN_RANGE.first, columnsAfter(0))
        assertEquals(config, config.setColumns("n", 5))
    }

    @Test
    fun rowGapStaysInRange() {
        fun gapAfter(dp: Int) = (config.setRowGap("g", dp).pages[0].cards[0] as GridCard).rowGapDp
        assertEquals(DEFAULT_ROW_GAP, (config.pages[0].cards[0] as GridCard).rowGapDp)
        assertEquals(8, gapAfter(8))
        assertEquals(ROW_GAP_RANGE.last, gapAfter(99))
        assertEquals(ROW_GAP_RANGE.first, gapAfter(-4))
        assertEquals(config.setRowGap("g", 8), parseConfig(config.setRowGap("g", 8).toJson()))
        assertEquals(config, config.setRowGap("n", 8))
        val withUsage = config.addCard(1, UsageCard("u", "常用", UsageKind.FREQUENT)).setRowGap("u", 4)
        assertEquals(4, (withUsage.pages[1].cards[0] as UsageCard).rowGapDp)
    }

    @Test
    fun columnGapIsAutoUntilSetAndStaysInRange() {
        fun gapAfter(dp: Int?) = (config.setColumnGap("g", dp).pages[0].cards[0] as GridCard).columnGapDp
        assertEquals(null, (config.pages[0].cards[0] as GridCard).columnGapDp)
        assertEquals(12, gapAfter(12))
        assertEquals(COLUMN_GAP_RANGE.last, gapAfter(999))
        assertEquals(COLUMN_GAP_RANGE.first, gapAfter(-4))
        val backToAuto = config.setColumnGap("g", 12).setColumnGap("g", null)
        assertEquals(null, (backToAuto.pages[0].cards[0] as GridCard).columnGapDp)
        assertEquals(config.setColumnGap("g", 12), parseConfig(config.setColumnGap("g", 12).toJson()))
        assertEquals(config, config.setColumnGap("n", 12))
        val withUsage = config.addCard(1, UsageCard("u", "常用", UsageKind.FREQUENT)).setColumnGap("u", 20)
        assertEquals(20, (withUsage.pages[1].cards[0] as UsageCard).columnGapDp)
    }

    @Test
    fun systemCardsRoundTripAndRename() {
        val battery = SystemCard("s", "电量", SystemKind.BATTERY)
        val withSystem = config.addCard(1, battery)
        assertEquals(withSystem, parseConfig(withSystem.toJson()))
        assertEquals("电池", withSystem.renameCard("s", "电池").pages[1].cards[0].title)
    }

    @Test
    fun usageCardsRoundTripRenameAndChangeColumns() {
        val recent = UsageCard("u", "最近使用", UsageKind.RECENT)
        val withUsage = config.addCard(1, recent)
        assertEquals(withUsage, parseConfig(withUsage.toJson()))
        assertEquals("最近", withUsage.renameCard("u", "最近").pages[1].cards[0].title)
        assertEquals(5, (withUsage.setColumns("u", 5).pages[1].cards[0] as UsageCard).columns)
        assertEquals(COLUMN_RANGE.last, (withUsage.setColumns("u", 9).pages[1].cards[0] as UsageCard).columns)
    }

    @Test
    fun widgetCardsRoundTripResizeAndListTheirIds() {
        val clock = WidgetCard("w", "时钟", widgetId = 7)
        val withWidget = config.addCard(1, clock).addCard(0, WidgetCard("x", "天气", widgetId = 9, heightDp = 100))
        assertEquals(withWidget, parseConfig(withWidget.toJson()))
        assertEquals(setOf(7, 9), withWidget.widgetIds())
        assertEquals(setOf(9), withWidget.removeCard("w").widgetIds())
        fun heightAfter(dp: Int) = (withWidget.setCardHeight("w", dp).pages[1].cards[0] as WidgetCard).heightDp
        assertEquals(240, heightAfter(240))
        assertEquals(WIDGET_HEIGHT_RANGE.last, heightAfter(2000))
        assertEquals(WIDGET_HEIGHT_RANGE.first, heightAfter(0))
    }

    @Test
    fun textCardsRoundTripRenameAndChange() {
        val motto = TextCard("t", "文字", text = "今天也要加油", color = "#FFFFFF", cardBackground = true)
        val withText = config.addCard(1, motto)
        assertEquals(withText, parseConfig(withText.toJson()))
        assertEquals("标语", withText.renameCard("t", "标语").pages[1].cards[0].title)
        fun textAfter(change: (TextCard) -> TextCard) = withText.updateTextCard("t", change).pages[1].cards[0] as TextCard
        assertEquals(motto.copy(text = "早点睡", bold = true), textAfter { it.copy(text = "早点睡", bold = true) })
        assertEquals(TEXT_SIZE_RANGE.last, textAfter { it.copy(textSize = 99) }.textSize)
        assertEquals(TEXT_SIZE_RANGE.first, textAfter { it.copy(textSize = 1) }.textSize)
        assertEquals(config, config.updateTextCard("n") { it.copy(text = "x") })
    }

    @Test
    fun hitokotoCardsRoundTripRenameAndHideTheirTitle() {
        val withHitokoto = config.addCard(1, HitokotoCard("h", "一言"))
        assertEquals(withHitokoto, parseConfig(withHitokoto.toJson()))
        assertEquals("每日一句", withHitokoto.renameCard("h", "每日一句").pages[1].cards[0].title)
        assertEquals(false, withHitokoto.setShowTitle("h", false).pages[1].cards[0].showTitle)
    }

    @Test
    fun textAndNoteHeightsAreAutoOrWithinRange() {
        val withText = config.addCard(1, TextCard("t", "文字"))
        assertEquals(AUTO_HEIGHT, (withText.pages[1].cards[0] as TextCard).heightDp)
        assertEquals(120, (withText.setCardHeight("t", 120).pages[1].cards[0] as TextCard).heightDp)
        assertEquals(CARD_HEIGHT_RANGE.last, (withText.setCardHeight("t", 5000).pages[1].cards[0] as TextCard).heightDp)
        assertEquals(AUTO_HEIGHT, (withText.setCardHeight("t", -3).pages[1].cards[0] as TextCard).heightDp)
        assertEquals(200, (config.setCardHeight("n", 200).pages[0].cards[1] as NoteCard).heightDp)
        assertEquals(config, config.setCardHeight("g", 200))
    }

    @Test
    fun pageStylesRoundTripAndListTheirPictures() {
        val styled = config
            .setPageStyle(0) { it.copy(preset = ThemePreset.PAPER, darkMode = DarkMode.DARK, background = "a.jpg") }
            .setPageStyle(1) { it.copy(background = "b.png", backgroundFit = BackgroundFit.FILL_WIDTH, cardTransparency = 40) }
        assertEquals(styled, parseConfig(styled.toJson()))
        assertEquals(ThemePreset.PAPER, styled.pages[0].style.preset)
        assertEquals(PageStyle(), styled.pages[0].style.copy(preset = null, darkMode = null, background = null))
        assertEquals(setOf("a.jpg", "b.png"), styled.backgrounds())
        assertEquals(setOf("b.png"), styled.removePage(0).backgrounds())
        assertEquals(config, config.setPageStyle(5) { it.copy(cardTransparency = 10) })
    }

    @Test
    fun pagesSavedBeforeStylesExistedLookAsBefore() {
        val text = """{"pages":[{"cards":[]}]}"""
        assertEquals(PageStyle(), parseConfig(text).pages[0].style)
    }

    @Test
    fun screenTimeCardsRoundTripRenameAndHideTheirTitle() {
        val withScreenTime = config.addCard(1, ScreenTimeCard("t", "今日使用"))
        assertEquals(withScreenTime, parseConfig(withScreenTime.toJson()))
        assertEquals("屏幕时间", withScreenTime.renameCard("t", "屏幕时间").pages[1].cards[0].title)
        assertEquals(false, withScreenTime.setShowTitle("t", false).pages[1].cards[0].showTitle)
    }

    @Test
    fun scenesOpenTheirPageAndAnAppOpensOnlyOne() {
        val scenes = config.setSceneApps(1, setOf("com.tencent.mm", "tv.danmaku.bili"))
        assertEquals(scenes, parseConfig(scenes.toJson()))
        assertEquals(1, scenes.scenePage("com.tencent.mm"))
        assertEquals(0, scenes.scenePage("com.android.chrome"))
        assertEquals(0, scenes.scenePage(null))
        val moved = scenes.setSceneApps(0, setOf("com.tencent.mm"))
        assertEquals(0, moved.scenePage("com.tencent.mm"))
        assertEquals(setOf("tv.danmaku.bili"), moved.pages[1].sceneApps)
        assertEquals(0, scenes.movePage(1, -1).scenePage("com.tencent.mm"))
    }
}
