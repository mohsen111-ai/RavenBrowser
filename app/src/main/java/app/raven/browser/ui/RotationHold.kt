package app.raven.browser.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * With the rotation lock on, Android can leave its locked direction sideways after a fullscreen video (it happens when
 * picture-in-picture came in between). Raven then keeps the screen the way it was before the video, until the lock is
 * changed: turned off, or turned with the rotate button. Returns the direction to keep (an ActivityInfo orientation),
 * or null when there's nothing to put right.
 */
@Composable
fun rememberRotationHold(activity: Activity, fullscreen: Boolean): Int? {
    val cr = activity.contentResolver
    fun autoRotate() = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 1) == 1
    fun locked() = Settings.System.getInt(cr, Settings.System.USER_ROTATION, Surface.ROTATION_0)
    // The locked direction when the video went fullscreen (null with auto-rotate on).
    var before by remember { mutableStateOf<Int?>(null) }
    var hold by remember { mutableStateOf<Int?>(null) }
    // The locked direction Android had left when Raven started holding: any other change is yours.
    var holdFrom by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(fullscreen) {
        if (fullscreen) {
            hold = null
            before = if (autoRotate()) null else locked()
        } else {
            val was = before
            before = null
            if (was != null && !autoRotate() && locked() != was) {
                holdFrom = locked()
                hold = was
            }
        }
    }
    DisposableEffect(cr) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (hold != null && (autoRotate() || locked() != holdFrom)) hold = null
            }
        }
        cr.registerContentObserver(Settings.System.getUriFor(Settings.System.USER_ROTATION), false, observer)
        cr.registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, observer)
        onDispose { cr.unregisterContentObserver(observer) }
    }
    return when (hold) {
        null -> null
        Surface.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        Surface.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        Surface.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}
