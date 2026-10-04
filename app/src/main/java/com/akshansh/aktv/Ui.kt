package com.akshansh.aktv

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Hide status/navigation bars and draw behind the notch (no more black strips). */
fun Activity.goImmersive() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
    }
}

/** Keep content out of the camera cutout and above the keyboard. */
fun applySafeInsets(root: View) {
    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
        val i = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or
                WindowInsetsCompat.Type.ime()
        )
        v.setPadding(i.left, i.top, i.right, i.bottom)
        insets
    }
}

/** D-pad focus: smooth zoom + lift. Touch: quick press-down feedback. */
fun View.attachCardInteractions(focusScale: Float = 1.07f) {
    val lift = 12f * resources.displayMetrics.density
    val interp = DecelerateInterpolator()
    setOnFocusChangeListener { v, focused ->
        v.animate()
            .scaleX(if (focused) focusScale else 1f)
            .scaleY(if (focused) focusScale else 1f)
            .translationZ(if (focused) lift else 0f)
            .setDuration(140)
            .setInterpolator(interp)
            .start()
    }
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.animate()
                    .scaleX(if (v.isFocused) focusScale else 1f)
                    .scaleY(if (v.isFocused) focusScale else 1f)
                    .setDuration(120).start()
        }
        false
    }
}

fun View.resetCardState() {
    animate().cancel()
    scaleX = 1f
    scaleY = 1f
    translationZ = 0f
}
