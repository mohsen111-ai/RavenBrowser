package app.raven.browser.ui.browser

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.raven.browser.engine.PullGesture
import app.raven.browser.engine.PullGesture.Step
import app.raven.browser.engine.pullAllowed
import app.raven.browser.ui.theme.Icon
import app.raven.browser.ui.theme.Icons
import app.raven.browser.ui.theme.Raven
import app.raven.browser.ui.theme.Space
import kotlinx.coroutines.flow.collectLatest
import org.mozilla.geckoview.GeckoView

/** What a page view's pull to refresh is doing, shared by the view (which follows the finger) and its circle. */
@Stable
class PullState {
    /** How far the pull has gone: 1 is far enough to reload when let go. */
    var progress by mutableFloatStateOf(0f)
        internal set
    var dragging by mutableStateOf(false)
        internal set
    var armed by mutableStateOf(false)
        internal set
    /** Let go far enough: the page is reloading, and the circle turns until it has. */
    var refreshing by mutableStateOf(false)

    /** Whether a pull may start here now (checked as the finger comes down). */
    var allowed: () -> Boolean = { false }
    /** In full screen, touches starting this close to the top of the window bring the bars back instead. */
    var topEdgePx: () -> Float = { 0f }
    var onRefresh: () -> Unit = {}
}

/**
 * The engine's view of a page, with pull down to refresh. When a finger comes down, it asks the engine whether the
 * page is at its top and isn't using the touch itself; only then, and only once the finger clearly moves down, does
 * it take the touch from the page (which is told the touch was cancelled). Let go past the point where a light tick
 * is felt, and the page reloads, as with the Reload button.
 */
@SuppressLint("ViewConstructor")
class PullGeckoView(context: Context, private val pull: PullState) : GeckoView(context) {
    private val gesture = PullGesture(
        slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
        trigger = 96 * resources.displayMetrics.density,
        longPress = ViewConfiguration.getLongPressTimeout().toLong(),
    )
    private var downTime = 0L
    private var downClock = 0L
    private var skipped: String? = null
    private val where = IntArray(2)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (session == null) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val mine = event.downTime
                downTime = mine
                downClock = SystemClock.uptimeMillis()
                skipped = blockedReason(event)
                gesture.down(event.x, event.y, event.eventTime, skipped != null)
                if (skipped != null) return super.onTouchEvent(event)
                // The engine gets the touch through here (once), and answers whether a pull may start.
                onTouchEventForDetailResult(event).accept({ d ->
                    if (d == null || mine != downTime) return@accept
                    val ok = pullAllowed(d.handledResult(), d.scrollableDirections(), d.overscrollDirections())
                    if (!ok) skipped = "page's (result ${d.handledResult()}, scroll ${d.scrollableDirections()}, overscroll ${d.overscrollDirections()})"
                    gesture.answer(ok)
                }, { if (mine == downTime) gesture.answer(false) })
                return true
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                when (gesture.move(event.x, event.y, event.eventTime, event.pointerCount)) {
                    Step.PAGE -> return super.onTouchEvent(event)
                    Step.START -> {
                        cancelForPage(event)
                        Log.i("Raven", "pull: start (answer after ${SystemClock.uptimeMillis() - downClock} ms)")
                        if (gesture.armed) tick(true)
                        show()
                    }
                    Step.ARMED -> { tick(true); show() }
                    Step.DISARMED -> { tick(false); show() }
                    Step.PULL -> show()
                    Step.END -> release()
                    else -> Unit
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val far = gesture.pulledFar
                when (gesture.up(cancelled = event.actionMasked == MotionEvent.ACTION_CANCEL)) {
                    Step.PAGE -> {
                        // Only a pull someone meant is worth a line: why it didn't reload.
                        if (far && skipped != null) Log.d("Raven", "pull: skipped, $skipped")
                        return super.onTouchEvent(event)
                    }
                    Step.REFRESH -> {
                        Log.i("Raven", "pull: refresh")
                        pull.refreshing = true
                        release()
                        pull.onRefresh()
                    }
                    else -> { Log.i("Raven", "pull: released"); release() }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun blockedReason(e: MotionEvent): String? {
        if (e.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) return "mouse"
        if (!pull.allowed()) return "not here now"
        if (pull.refreshing) return "already reloading"
        // A page's form being filled in is never thrown away by a pull.
        if (ViewCompat.getRootWindowInsets(this)?.isVisible(WindowInsetsCompat.Type.ime()) == true) return "keyboard open"
        getLocationInWindow(where)
        if (e.y + where[1] < pull.topEdgePx()) return "top edge in full screen"
        return null
    }

    /** The page is told the touch was cancelled. Not recycled: the engine may keep the event for later. */
    private fun cancelForPage(e: MotionEvent) {
        super.onTouchEvent(MotionEvent.obtain(e).apply { action = MotionEvent.ACTION_CANCEL })
    }

    private fun show() {
        pull.dragging = true
        pull.progress = gesture.progress
        pull.armed = gesture.armed
    }

    private fun release() {
        pull.dragging = false
        pull.armed = false
        pull.progress = 0f
    }

    private fun tick(on: Boolean) {
        when {
            Build.VERSION.SDK_INT >= 34 -> performHapticFeedback(
                if (on) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE,
            )
            on -> performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}

/**
 * The circle that comes down with the finger: a ring filling as the pull goes, the reload arrow turning with it,
 * lit once letting go will reload; then turning while the page reloads (still, with Reduce motion). [clearTop]: how
 * far down it must sit to stay clear of the status bar and the camera, read as it's drawn.
 */
@Composable
internal fun PullIndicator(pull: PullState, private: Boolean, clearTop: () -> Float, modifier: Modifier = Modifier) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(pull) {
        snapshotFlow { Triple(pull.dragging, pull.refreshing, pull.progress) }.collectLatest { (drag, reloading, progress) ->
            when {
                drag -> anim.snapTo(progress)
                reloading -> anim.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
                else -> anim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            }
        }
    }
    val visible by remember { derivedStateOf { anim.value > 0.01f || pull.refreshing } }
    if (!visible) return
    val still = Raven.reduceMotion
    val accent = if (private) Space.Nebula else Raven.accent
    val spin = if (pull.refreshing && !still) {
        rememberInfiniteTransition(label = "pullSpin").animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spin")
    } else null
    val lit = pull.armed || pull.refreshing
    Box(
        modifier
            .graphicsLayer {
                val p = anim.value
                val q = p.coerceAtMost(1f)
                val rest = 20.dp.toPx()
                val hidden = -44.dp.toPx()
                // Past the point, it only gives a little more, like a rubber band.
                val extra = ((p - 1f).coerceAtLeast(0f) * 24.dp.toPx()).coerceAtMost(16.dp.toPx())
                translationY = clearTop() + hidden + (rest - hidden) * q + extra
                alpha = (p * 1.6f).coerceIn(0f, 1f)
                val s = 0.7f + 0.3f * q
                scaleX = s
                scaleY = s
            }
            .size(40.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(if (private) Space.NebulaBar else Space.Surface2)
            .border(1.dp, Space.Hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(30.dp)) {
            val w = 2.5.dp.toPx()
            drawArc(Space.Track, 0f, 360f, false, style = Stroke(w))
            if (pull.refreshing) {
                drawArc(accent, (spin?.value ?: 0f) - 90f, if (still) 360f else 100f, false, style = Stroke(w, cap = StrokeCap.Round))
            } else {
                drawArc(accent.copy(alpha = if (lit) 1f else 0.6f), -90f, 300f * anim.value.coerceAtMost(1f), false, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        Icon(
            Icons.Reload, null, size = 16.dp, tint = if (lit) accent else Space.Text2,
            modifier = Modifier.graphicsLayer { rotationZ = if (pull.refreshing) 0f else 180f * anim.value.coerceAtMost(1.2f) },
        )
    }
}
