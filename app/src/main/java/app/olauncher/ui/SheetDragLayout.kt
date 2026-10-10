package app.olauncher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * The home screen's root: a frame that can take a vertical drag away from whatever child it
 * started on and hand it to [callback], so the app sheet follows the finger whether the swipe
 * began on empty space, the date, the tip card, the search bar or the app list.
 *
 * Every event is seen here first. Once a gesture has moved past the touch slop, mostly
 * vertically, and [Callback.canDragSheet] accepts its direction, the children get an
 * ACTION_CANCEL (ending their taps, long presses and scrolls) and the rest of the gesture
 * drives the sheet. Anything else passes through untouched.
 */
class SheetDragLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    interface Callback {
        /** Whether a drag starting at ([x], [y]) and heading [up] (or down) should move the sheet. */
        fun canDragSheet(x: Float, y: Float, up: Boolean): Boolean

        /** The finger has moved [dy] pixels since the sheet took the gesture; negative is up. */
        fun onSheetDrag(dy: Float)

        /** The finger lifted at [velocityY] pixels per second; negative is up. */
        fun onSheetRelease(velocityY: Float)
    }

    var callback: Callback? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var claimY = 0f
    private var dragging = false
    private var undecided = false
    private var velocityTracker: VelocityTracker? = null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val callback = callback ?: return super.dispatchTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragging = false
                undecided = true
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
            }

            // A second finger makes it something other than a sheet swipe.
            MotionEvent.ACTION_POINTER_DOWN -> undecided = false

            MotionEvent.ACTION_MOVE -> if (undecided && !dragging) {
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dy) > touchSlop || abs(dx) > touchSlop) {
                    undecided = false
                    if (abs(dy) > abs(dx) && callback.canDragSheet(downX, downY, up = dy < 0)) {
                        dragging = true
                        claimY = event.y
                        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                    }
                }
            }
        }
        velocityTracker?.addMovement(event)

        if (!dragging) return super.dispatchTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> callback.onSheetDrag(event.y - claimY)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val tracker = velocityTracker
                tracker?.computeCurrentVelocity(1000)
                val velocity = if (event.actionMasked == MotionEvent.ACTION_UP) tracker?.yVelocity ?: 0f else 0f
                dragging = false
                velocityTracker?.recycle()
                velocityTracker = null
                callback.onSheetRelease(velocity)
            }
        }
        return true
    }
}
