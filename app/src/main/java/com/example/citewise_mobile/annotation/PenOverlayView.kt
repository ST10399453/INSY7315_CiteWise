package com.example.citewise_mobile.annotation

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

//https://developer.android.com/reference/android/graphics/package-summary

data class PenStroke(
    val path: Path,
    val paint: Paint,
    val pageIndex: Int
)

class PenOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** set by Activity */
    var drawingEnabled: Boolean = false

    var strokeColor: Int = Color.RED
    var strokeWidthPx: Float = 6f

    var currentPageIndex: Int = 0
        set(value) { field = value; invalidate() }

    private val strokes = mutableListOf<PenStroke>()
    private val undone = ArrayDeque<PenStroke>()

    private var activePath: Path? = null
    private var activePaint: Paint? = null
    private var lastX = 0f
    private var lastY = 0f

    fun strokesForPage(page: Int): List<PenStroke> = strokes.filter { it.pageIndex == page }

    fun undo() {
        val last = strokes.lastOrNull { it.pageIndex == currentPageIndex } ?: return
        strokes.remove(last); undone.addLast(last); invalidate()
    }

    fun redo() {
        val r = undone.removeLastOrNull() ?: return
        strokes.add(r); invalidate()
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!drawingEnabled) return false                // let pager handle swipes
        if (ev.pointerCount > 1) return false            // let ZoomFrameLayout handle pinch/pan

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                activePath = Path().apply { moveTo(ev.x, ev.y) }
                activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                    color = strokeColor
                    strokeWidth = strokeWidthPx
                }
                lastX = ev.x; lastY = ev.y
                undone.clear()
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - lastX)
                val dy = abs(ev.y - lastY)
                if (dx >= 1f || dy >= 1f) {
                    activePath?.quadTo(lastX, lastY, (ev.x + lastX)/2f, (ev.y + lastY)/2f)
                    lastX = ev.x; lastY = ev.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val p = activePath; val paint = activePaint
                if (p != null && paint != null) {
                    val b = RectF()
                    p.computeBounds(b, false)
                    if (b.width() > 1f || b.height() > 1f) {
                        strokes.add(PenStroke(Path(p), Paint(paint), currentPageIndex))
                    }
                }
                activePath = null; activePaint = null
                invalidate()
                return true
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (s in strokesForPage(currentPageIndex)) {
            canvas.drawPath(s.path, s.paint)
        }
        val p = activePath; val paint = activePaint
        if (p != null && paint != null) canvas.drawPath(p, paint)
    }
}
