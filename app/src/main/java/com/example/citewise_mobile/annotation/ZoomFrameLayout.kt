package com.example.citewise_mobile.annotation

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout

/**
 * Pinch-to-zoom + pan for its children (pager + overlay).
 * minScale = 1f (page size). Can't zoom out below page.
 * Double-tap cycles 1x -> 2x -> 4x -> 1x.
 */
//https://developer.android.com/develop/ui/views/animations/zoom
class ZoomFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null
) : FrameLayout(context, attrs) {

    var minScale = 1f
    var maxScale = 4f

    private var scale = 1f
    private var transX = 0f
    private var transY = 0f

    /** Called when zoom/pan changes (HUD, pager logic) */
    var onScaleOrPanChanged: (() -> Unit)? = null

    /** Return true if a child (overlay) wants to draw now */
    var shouldAllowChildDraw: (() -> Boolean)? = null

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val prev = scale
            scale = (scale * detector.scaleFactor).coerceIn(minScale, maxScale)
            val fx = detector.focusX; val fy = detector.focusY
            val k = scale / prev
            // zoom around focal point
            transX = (transX - fx) * k + fx
            transY = (transY - fy) * k + fy
            constrain(); apply()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            transX -= dx; transY -= dy
            constrain(); apply()
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            scale = when {
                scale < 1.5f -> 2f
                scale < 3.0f -> 4f
                else -> 1f
            }.coerceIn(minScale, maxScale)
            constrain(); apply()
            return true
        }
    })

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        gestureDetector.onTouchEvent(ev)

        val isScaling  = scaleDetector.isInProgress
        val isZoomed   = scale > minScale
        val multiTouch = ev.pointerCount > 1
        val childWantsToDraw = shouldAllowChildDraw?.invoke() == true

        // Intercept if:
        // - pinching (always), or
        // - multi-touch (pan/zoom), or
        // - zoomed AND NOT drawing (so single-finger pans),
        // but if drawing is active, let single-finger go to child for strokes.
        return isScaling || multiTouch || (isZoomed && !childWantsToDraw)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        val childWantsToDraw = shouldAllowChildDraw?.invoke() == true
        val consume = scaleDetector.isInProgress || event.pointerCount > 1 || (scale > minScale && !childWantsToDraw)

        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            constrain(); apply()
        }
        return consume
    }

    private fun constrain() {
        scale = scale.coerceIn(minScale, maxScale)

        val contentW = width * scale
        val contentH = height * scale
        val maxTx = (contentW - width) / 2f
        val maxTy = (contentH - height) / 2f

        transX = if (contentW <= width) 0f else transX.coerceIn(-maxTx, maxTx)
        transY = if (contentH <= height) 0f else transY.coerceIn(-maxTy, maxTy)
    }

    private fun apply() {
        val s = scale; val tx = transX; val ty = transY
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            v.pivotX = width / 2f
            v.pivotY = height / 2f
            v.scaleX = s
            v.scaleY = s
            v.translationX = tx
            v.translationY = ty
        }
        onScaleOrPanChanged?.invoke()
    }

    fun resetZoom() { scale = 1f; transX = 0f; transY = 0f; apply() }
    fun currentScale(): Float = scale
}
