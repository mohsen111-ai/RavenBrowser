package app.raven.browser.engine

import org.mozilla.geckoview.PanZoomController
import kotlin.math.abs

/**
 * Pull down to refresh is allowed for this touch, by the engine's own answer when the finger came down: the page is at
 * its top, it isn't using the touch itself (a map, a game, a drawer), and it allows the pull (a site can say no with
 * CSS overscroll-behavior, for example one with its own pull to refresh). The same rule as Firefox for Android.
 */
fun pullAllowed(handled: Int, scrollable: Int, overscroll: Int): Boolean =
    handled != PanZoomController.INPUT_RESULT_HANDLED_CONTENT && handled != PanZoomController.INPUT_RESULT_IGNORED &&
        scrollable and PanZoomController.SCROLLABLE_FLAG_TOP == 0 &&
        overscroll and PanZoomController.OVERSCROLL_FLAG_VERTICAL != 0

/**
 * One finger's pull, from touching down to letting go. It decides, move by move, whether the page keeps the touch or
 * Raven takes it for a pull, and whether letting go reloads. Kept free of Android views so it can be tested alone.
 *
 * [slop]: how far a finger moves before it counts as moving. [trigger]: how far a pull goes before letting go reloads.
 * [longPress]: a finger held this long before moving is a long press (a link's menu, selecting text), not a pull.
 */
class PullGesture(private val slop: Float, private val trigger: Float, private val longPress: Long) {
    enum class Phase { IDLE, WATCHING, PULLING, OUT, DONE }

    /** What the view does with this event. */
    enum class Step {
        /** The page gets it. */
        PAGE,
        /** Raven takes the touch from here on (the page is told the touch was cancelled). */
        START,
        PULL,
        /** Pulled far enough: letting go now reloads. */
        ARMED,
        /** Pulled back up again: letting go now doesn't. */
        DISARMED,
        /** The pull ends without a reload. */
        END,
        REFRESH,
        /** The rest of a pull that ended (a second finger came down): nobody gets it. */
        EAT,
    }

    var phase = Phase.IDLE
        private set
    var distance = 0f
        private set
    val progress: Float get() = distance / trigger
    val armed: Boolean get() = distance >= trigger
    /** The finger went down past the trigger in this touch, whoever had it (for the log). */
    val pulledFar: Boolean get() = lowest - downY > trigger + slop

    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var lowest = 0f
    private var answer: Boolean? = null
    private var candidate = false

    /** A finger touches down. [blocked]: no pull here at all (another reason, such as the keyboard being open). */
    fun down(x: Float, y: Float, time: Long, blocked: Boolean) {
        phase = if (blocked) Phase.OUT else Phase.WATCHING
        downX = x
        downY = y
        downAt = time
        lowest = y
        answer = null
        candidate = false
        distance = 0f
    }

    /** The engine's answer for this touch ([pullAllowed]); it can come a few moves late. */
    fun answer(allowed: Boolean) {
        if (phase != Phase.WATCHING || answer != null) return
        answer = allowed
        if (!allowed) phase = Phase.OUT
    }

    fun move(x: Float, y: Float, time: Long, pointers: Int): Step {
        if (y > lowest) lowest = y
        return when (phase) {
            Phase.IDLE, Phase.OUT -> Step.PAGE
            Phase.DONE -> Step.EAT
            Phase.WATCHING -> watch(x, y, time, pointers)
            Phase.PULLING -> pull(y, pointers)
        }
    }

    private fun watch(x: Float, y: Float, time: Long, pointers: Int): Step {
        // Two fingers: zooming, the page's.
        if (pointers > 1) { phase = Phase.OUT; return Step.PAGE }
        val dx = x - downX
        val dy = y - downY
        if (!candidate) {
            if (abs(dx) < slop && abs(dy) < slop) {
                if (time - downAt > longPress) phase = Phase.OUT
                return Step.PAGE
            }
            // Its first real move decides: upward or more sideways than down (past 45°), or after a long press, it's
            // the page's.
            if (dy <= 0f || abs(dx) > dy || time - downAt > longPress) { phase = Phase.OUT; return Step.PAGE }
            candidate = true
        }
        // Waiting for a late answer, the finger came back up: that's a scroll, and stays the page's.
        if (dy < slop || lowest - y > slop) { phase = Phase.OUT; return Step.PAGE }
        if (answer != true) return Step.PAGE
        phase = Phase.PULLING
        distance = (dy - slop).coerceAtLeast(0f)
        return Step.START
    }

    private fun pull(y: Float, pointers: Int): Step {
        if (pointers > 1) { phase = Phase.DONE; distance = 0f; return Step.END }
        val was = armed
        distance = (y - downY - slop).coerceAtLeast(0f)
        return when {
            armed && !was -> Step.ARMED
            !armed && was -> Step.DISARMED
            else -> Step.PULL
        }
    }

    /** The finger lifts ([cancelled]: Android took the touch away; never a reload then). */
    fun up(cancelled: Boolean): Step {
        val step = when (phase) {
            Phase.PULLING -> if (armed && !cancelled) Step.REFRESH else Step.END
            Phase.DONE -> Step.EAT
            else -> Step.PAGE
        }
        phase = Phase.IDLE
        distance = 0f
        return step
    }
}
