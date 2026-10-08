package app.raven.browser

import app.raven.browser.engine.PullGesture
import app.raven.browser.engine.PullGesture.Step
import app.raven.browser.engine.pullAllowed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.geckoview.PanZoomController.INPUT_RESULT_HANDLED
import org.mozilla.geckoview.PanZoomController.INPUT_RESULT_HANDLED_CONTENT
import org.mozilla.geckoview.PanZoomController.INPUT_RESULT_IGNORED
import org.mozilla.geckoview.PanZoomController.INPUT_RESULT_UNHANDLED
import org.mozilla.geckoview.PanZoomController.OVERSCROLL_FLAG_NONE
import org.mozilla.geckoview.PanZoomController.OVERSCROLL_FLAG_VERTICAL
import org.mozilla.geckoview.PanZoomController.SCROLLABLE_FLAG_BOTTOM
import org.mozilla.geckoview.PanZoomController.SCROLLABLE_FLAG_NONE
import org.mozilla.geckoview.PanZoomController.SCROLLABLE_FLAG_TOP

/** Pull down to refresh: when a finger's pull is the page's, when it's Raven's, and when letting go reloads. */
class PullGestureTest {
    private fun gesture() = PullGesture(slop = 8f, trigger = 96f, longPress = 500)

    @Test fun fullPullReloads() {
        val g = gesture()
        g.down(100f, 100f, 0, blocked = false)
        g.answer(true)
        assertEquals(Step.PAGE, g.move(100f, 105f, 10, 1))  // still inside the slop
        assertEquals(Step.START, g.move(100f, 120f, 20, 1))
        assertEquals(Step.ARMED, g.move(100f, 210f, 60, 1))
        assertEquals(Step.PULL, g.move(100f, 260f, 80, 1))
        assertEquals(Step.REFRESH, g.up(cancelled = false))
    }

    @Test fun shortPullDoesNot() {
        val g = gesture()
        g.down(100f, 100f, 0, false)
        g.answer(true)
        assertEquals(Step.START, g.move(100f, 120f, 20, 1))
        g.move(100f, 150f, 40, 1)
        assertEquals(Step.END, g.up(false))
    }

    @Test fun pulledBackUpDoesNot() {
        val g = gesture()
        g.down(100f, 100f, 0, false)
        g.answer(true)
        g.move(100f, 120f, 20, 1)
        assertEquals(Step.ARMED, g.move(100f, 220f, 60, 1))
        assertEquals(Step.DISARMED, g.move(100f, 150f, 90, 1))
        assertEquals(Step.END, g.up(false))
    }

    @Test fun engineSaysNo() {
        val g = gesture()
        g.down(100f, 100f, 0, false)
        g.answer(false)
        assertEquals(Step.PAGE, g.move(100f, 120f, 20, 1))
        assertEquals(Step.PAGE, g.move(100f, 260f, 60, 1))
        assertEquals(Step.PAGE, g.up(false))
    }

    @Test fun lateAnswerStillDownwards() {
        val g = gesture()
        g.down(100f, 100f, 0, false)
        assertEquals(Step.PAGE, g.move(100f, 120f, 20, 1))
        assertEquals(Step.PAGE, g.move(100f, 140f, 30, 1))
        g.answer(true)
        assertEquals(Step.START, g.move(100f, 150f, 40, 1))
    }

    @Test fun lateAnswerPastThePointTicksAtOnce() {
        val g = gesture()
        g.down(100f, 100f, 0, false)
        g.move(100f, 150f, 20, 1)
        g.move(100f, 230f, 40, 1)
        g.answer(true)
        assertEquals(Step.START, g.move(100f, 240f, 50, 1))
        assertTrue(g.armed)
    }

    @Test fun lateAnswerAfterTurningBackUpIsAScroll() {
        val g = gesture()
        g.down(100f, 100f, 0, false)
        g.move(100f, 140f, 20, 1)
        assertEquals(Step.PAGE, g.move(100f, 120f, 30, 1))
        g.answer(true)
        assertEquals(Step.PAGE, g.move(100f, 100f, 40, 1))
        assertEquals(Step.PAGE, g.move(100f, 200f, 60, 1))
        assertEquals(Step.PAGE, g.up(false))
    }

    @Test fun sidewaysIsThePages() {
        val g = gesture()
        g.down(100f, 100f, 0, false)
        g.answer(true)
        assertEquals(Step.PAGE, g.move(160f, 110f, 20, 1))
        assertEquals(Step.PAGE, g.move(160f, 260f, 60, 1))
    }

    @Test fun slightlySidewaysStillPulls() {
        val g = gesture()
        g.down(100f, 100f, 0, false)
        g.answer(true)
        assertEquals(Step.START, g.move(106f, 109f, 20, 1))  // a thumb's wobble: within 45 degrees
        val h = gesture()
        h.down(100f, 100f, 0, false)
        h.answer(true)
        assertEquals(Step.PAGE, h.move(110f, 109f, 20, 1))   // more sideways than down
    }

    @Test fun upwardsIsThePages() {
        val g = gesture()
        g.down(100f, 100f, 0, false)
        g.answer(true)
        assertEquals(Step.PAGE, g.move(100f, 80f, 20, 1))
        assertEquals(Step.PAGE, g.move(100f, 300f, 60, 1))
    }

    @Test fun pinchBeforeIsThePages() {
        val g = gesture()
        g.down(100f, 100f, 0, false)
        g.answer(true)
        assertEquals(Step.PAGE, g.move(100f, 104f, 10, 2))
        assertEquals(Step.PAGE, g.move(100f, 220f, 40, 1))
    }

    @Test fun pinchDuringEndsIt() {
        val g = gesture()
        g.down(100f, 100f, 0, false)
        g.answer(true)
        g.move(100f, 120f, 20, 1)
        g.move(100f, 230f, 40, 1)
        assertEquals(Step.END, g.move(100f, 240f, 50, 2))
        assertEquals(Step.EAT, g.move(100f, 250f, 60, 1))
        assertEquals(Step.EAT, g.up(false))
    }

    @Test fun longPressIsThePages() {
        val g = gesture()
        g.down(100f, 100f, 0, false)
        g.answer(true)
        assertEquals(Step.PAGE, g.move(100f, 102f, 600, 1))
        assertEquals(Step.PAGE, g.move(100f, 220f, 700, 1))
        val h = gesture()
        h.down(100f, 100f, 0, false)
        h.answer(true)
        assertEquals(Step.PAGE, h.move(100f, 120f, 700, 1))
    }

    @Test fun blockedIsThePages() {
        val g = gesture()
        g.down(100f, 100f, 0, blocked = true)
        g.answer(true)
        assertEquals(Step.PAGE, g.move(100f, 220f, 40, 1))
        assertEquals(Step.PAGE, g.up(false))
    }

    @Test fun cancelledNeverReloads() {
        val g = gesture()
        g.down(100f, 100f, 0, false)
        g.answer(true)
        g.move(100f, 120f, 20, 1)
        g.move(100f, 240f, 40, 1)
        assertEquals(Step.END, g.up(cancelled = true))
    }

    @Test fun pulledFarIsSeenEvenWhenThePagesKeptIt() {
        val g = gesture()
        g.down(100f, 100f, 0, false)
        g.answer(false)
        g.move(100f, 260f, 40, 1)
        assertTrue(g.pulledFar)
    }

    /** The engine's answer, with GeckoView's own values. */
    @Test fun engineRule() {
        assertTrue(pullAllowed(INPUT_RESULT_HANDLED, SCROLLABLE_FLAG_BOTTOM, OVERSCROLL_FLAG_VERTICAL))  // at the top
        assertFalse(pullAllowed(INPUT_RESULT_HANDLED, SCROLLABLE_FLAG_TOP or SCROLLABLE_FLAG_BOTTOM, OVERSCROLL_FLAG_VERTICAL))  // scrolled down
        assertFalse(pullAllowed(INPUT_RESULT_HANDLED_CONTENT, SCROLLABLE_FLAG_NONE, OVERSCROLL_FLAG_VERTICAL))  // a map
        assertTrue(pullAllowed(INPUT_RESULT_UNHANDLED, SCROLLABLE_FLAG_NONE, OVERSCROLL_FLAG_VERTICAL))  // a short page
        assertFalse(pullAllowed(INPUT_RESULT_UNHANDLED, SCROLLABLE_FLAG_NONE, OVERSCROLL_FLAG_NONE))  // no answer yet
        assertFalse(pullAllowed(INPUT_RESULT_HANDLED, SCROLLABLE_FLAG_BOTTOM, OVERSCROLL_FLAG_NONE))  // the site says no
        assertFalse(pullAllowed(INPUT_RESULT_IGNORED, SCROLLABLE_FLAG_NONE, OVERSCROLL_FLAG_VERTICAL))
    }
}
