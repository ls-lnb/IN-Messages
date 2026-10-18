package org.fossify.messages.extensions

import android.animation.ObjectAnimator
import android.view.View
import android.widget.TextView
import androidx.core.animation.doOnStart
import androidx.core.view.isVisible

fun View.showWithAnimation(duration: Long = 250L) {
    if (!isVisible) {
        ObjectAnimator.ofFloat(
            this, "alpha", 0f, 1f
        ).apply {
            this.duration = duration
            doOnStart { visibility = View.VISIBLE }
        }.start()
    }
}

/**
 * True when the touch at [x], [y] landed on the characters starting at [start] with the given
 * [length]. Used to react to a long press on a highlighted part of a message body.
 */
fun TextView.isTouchOnTextRange(x: Float, y: Float, start: Int, length: Int): Boolean {
    if (layout == null) {
        return false
    }

    val offset = getOffsetForPosition(x, y)
    return offset in start..(start + length)
}

