package com.cowalskiiw2026.autoklix.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View

/**
 * Overlay layar-penuh, tembus sentuh (non-interaktif), yang menggambar
 * garis putus-putus semu penghubung titik awal -> titik tujuan untuk
 * setiap titik bertipe SWIPE, sehingga arah & panjang gulir terlihat jelas.
 */
class SwipeLineView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    data class Line(val start: PointF, val end: PointF)

    var lines: List<Line> = emptyList()
        set(value) { field = value; invalidate() }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC22D3EE")
        strokeWidth = 5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(18f, 14f), 0f)
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC22D3EE")
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (line in lines) {
            canvas.drawLine(line.start.x, line.start.y, line.end.x, line.end.y, linePaint)
            drawArrowHead(canvas, line.start, line.end)
        }
    }

    private fun drawArrowHead(canvas: Canvas, start: PointF, end: PointF) {
        val angle = Math.atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
        val arrowLen = 22f
        val a1x = (end.x - arrowLen * Math.cos(angle - 0.5)).toFloat()
        val a1y = (end.y - arrowLen * Math.sin(angle - 0.5)).toFloat()
        val a2x = (end.x - arrowLen * Math.cos(angle + 0.5)).toFloat()
        val a2y = (end.y - arrowLen * Math.sin(angle + 0.5)).toFloat()
        val path = android.graphics.Path().apply {
            moveTo(end.x, end.y)
            lineTo(a1x, a1y)
            lineTo(a2x, a2y)
            close()
        }
        canvas.drawPath(path, arrowPaint)
    }
}
