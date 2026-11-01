package io.github.alstarknight.slidex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeGeometryTest {

    @Test
    fun rightHandleOpensOnLeftwardSwipe() {
        assertTrue(isOpenSwipe(Side.RIGHT, dx = -60f, dy = 10f, thresholdPx = 48f))
    }

    @Test
    fun leftHandleOpensOnRightwardSwipe() {
        assertTrue(isOpenSwipe(Side.LEFT, dx = 60f, dy = -10f, thresholdPx = 48f))
    }

    @Test
    fun swipeTowardTheEdgeDoesNotOpen() {
        assertFalse(isOpenSwipe(Side.RIGHT, dx = 60f, dy = 0f, thresholdPx = 48f))
        assertFalse(isOpenSwipe(Side.LEFT, dx = -60f, dy = 0f, thresholdPx = 48f))
    }

    @Test
    fun shortSwipeDoesNotOpen() {
        assertFalse(isOpenSwipe(Side.LEFT, dx = 30f, dy = 0f, thresholdPx = 48f))
    }

    @Test
    fun mostlyVerticalDragDoesNotOpen() {
        assertFalse(isOpenSwipe(Side.LEFT, dx = 60f, dy = 90f, thresholdPx = 48f))
    }

    @Test
    fun handleIsCenteredOnPosition() {
        assertEquals(400, handleTop(screenHeight = 2000, handleHeight = 200, positionPercent = 25))
    }

    @Test
    fun handleStaysOnScreen() {
        assertEquals(0, handleTop(screenHeight = 2000, handleHeight = 200, positionPercent = 0))
        assertEquals(1800, handleTop(screenHeight = 2000, handleHeight = 200, positionPercent = 100))
    }

    @Test
    fun handleTallerThanScreenSticksToTop() {
        assertEquals(0, handleTop(screenHeight = 100, handleHeight = 200, positionPercent = 50))
    }

    @Test
    fun handlePositionIsTheInverseOfHandleTop() {
        assertEquals(25, handlePositionPercent(screenHeight = 2000, handleHeight = 200, top = 400))
        assertEquals(100, handlePositionPercent(screenHeight = 2000, handleHeight = 200, top = 2000))
    }

    @Test
    fun panelFollowsTheFingerFromEitherEdge() {
        assertEquals(0.5f, panelFraction(Side.LEFT, x = 400f, screenWidth = 1000, panelWidth = 800f), 0.0001f)
        assertEquals(0.25f, panelFraction(Side.RIGHT, x = 800f, screenWidth = 1000, panelWidth = 800f), 0.0001f)
        assertEquals(1f, panelFraction(Side.LEFT, x = 950f, screenWidth = 1000, panelWidth = 800f), 0.0001f)
        assertEquals(0f, panelFraction(Side.RIGHT, x = 1010f, screenWidth = 1000, panelWidth = 800f), 0.0001f)
        assertEquals(1f, panelFraction(Side.LEFT, x = 0f, screenWidth = 1000, panelWidth = 0f), 0.0001f)
    }

    @Test
    fun aFlickDecidesOtherwiseThePanelOpensWhenHalfOut() {
        assertTrue(settlesOpen(fraction = 0.2f, velocity = 500f, flingVelocity = 400f))
        assertFalse(settlesOpen(fraction = 0.9f, velocity = -500f, flingVelocity = 400f))
        assertTrue(settlesOpen(fraction = 0.5f, velocity = -100f, flingVelocity = 400f))
        assertFalse(settlesOpen(fraction = 0.4f, velocity = 100f, flingVelocity = 400f))
    }

    @Test
    fun nearestSideSplitsTheScreenInHalf() {
        assertEquals(Side.LEFT, nearestSide(x = 400f, screenWidth = 1000))
        assertEquals(Side.RIGHT, nearestSide(x = 600f, screenWidth = 1000))
    }

    @Test
    fun ballPositionRoundTripsAndStaysOnScreen() {
        assertEquals(950, centeredOffset(length = 2000, size = 100, fraction = 0.5f))
        assertEquals(0.5f, centerFraction(length = 2000, size = 100, offset = 950), 0.0001f)
        assertEquals(0, centeredOffset(length = 2000, size = 100, fraction = 0f))
        assertEquals(1900, centeredOffset(length = 2000, size = 100, fraction = 1f))
    }

    @Test
    fun iconMovesAboveTheKeyboardOnlyWhenItWouldOverlap() {
        assertEquals(1000, aboveKeyboard(top = 1000, size = 100, keyboardTop = null, gap = 20))
        assertEquals(500, aboveKeyboard(top = 500, size = 100, keyboardTop = 1500, gap = 20))
        assertEquals(1380, aboveKeyboard(top = 1600, size = 100, keyboardTop = 1500, gap = 20))
        assertEquals(0, aboveKeyboard(top = 100, size = 100, keyboardTop = 50, gap = 20))
    }

    @Test
    fun menuPicksTheWayTheFingerMovedMost() {
        assertNull(menuDirection(dx = 10f, dy = -10f, thresholdPx = 30f))
        assertEquals(Direction.UP, menuDirection(dx = 10f, dy = -40f, thresholdPx = 30f))
        assertEquals(Direction.DOWN, menuDirection(dx = -10f, dy = 40f, thresholdPx = 30f))
        assertEquals(Direction.LEFT, menuDirection(dx = -40f, dy = 20f, thresholdPx = 30f))
        assertEquals(Direction.RIGHT, menuDirection(dx = 40f, dy = 40f, thresholdPx = 30f))
    }

    @Test
    fun menuStaysOnScreenNextToAnEdge() {
        assertEquals(500f, menuCenter(position = 500f, reach = 200f, length = 1000f), 0f)
        assertEquals(800f, menuCenter(position = 980f, reach = 200f, length = 1000f), 0f)
        assertEquals(200f, menuCenter(position = 0f, reach = 200f, length = 1000f), 0f)
        assertEquals(150f, menuCenter(position = 20f, reach = 200f, length = 300f), 0f)
    }
}
