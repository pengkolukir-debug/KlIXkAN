package com.cowalskiiw2026.autoklix.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.cowalskiiw2026.autoklix.model.PointType

/**
 * Marker bulat bernomor urut untuk menandai lokasi titik aksi di layar.
 * Warna berbeda sesuai [PointType]. Ukuran & opacity mengikuti pengaturan pengguna.
 */
class PointMarkerView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var orderNumber: Int = 1
        set(value) { field = value; invalidate() }

    var pointType: PointType = PointType.TAP
        set(value) { field = value; invalidate() }

    var sizeScale: Float = 1.0f
        set(value) { field = value; requestLayout() }

    var opacity: Float = 0.85f
        set(value) { field = value; invalidate() }

    var highlighted: Boolean = false
        set(value) { field = value; invalidate() }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 4f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private fun colorFor(type: PointType): Int = when (type) {
        PointType.TAP -> Color.parseColor("#4F46E5")
        PointType.LONG_PRESS -> Color.parseColor("#F59E0B")
        PointType.SWIPE -> Color.parseColor("#22D3EE")
        PointType.RANDOM_TEXT -> Color.parseColor("#22C55E")
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val base = (56 * resources.displayMetrics.density * sizeScale).toInt()
        setMeasuredDimension(base, base)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = (width.coerceAtMost(height) / 2f) - 4f
        val cx = width / 2f
        val cy = height / 2f

        circlePaint.color = colorFor(pointType)
        circlePaint.alpha = (opacity.coerceIn(0.15f, 1f) * 255).toInt()
        canvas.drawCircle(cx, cy, radius, circlePaint)

        if (highlighted) {
            borderPaint.color = Color.RED
            borderPaint.strokeWidth = 6f
        } else {
            borderPaint.color = Color.WHITE
            borderPaint.strokeWidth = 4f
        }
        canvas.drawCircle(cx, cy, radius, borderPaint)

        textPaint.textSize = radius * 0.85f
        val yText = cy - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(orderNumber.toString(), cx, yText, textPaint)
    }
}
