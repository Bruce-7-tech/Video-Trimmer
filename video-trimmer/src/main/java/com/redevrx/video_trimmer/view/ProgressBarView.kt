package com.redevrx.video_trimmer.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import com.redevrx.video_trimmer.R
import com.redevrx.video_trimmer.event.OnProgressVideoEvent
import com.redevrx.video_trimmer.event.OnRangeSeekBarEvent


class ProgressBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr),
    OnRangeSeekBarEvent, OnProgressVideoEvent {

    private var progressHeight: Int = 0
    private var viewWidth: Int = 0

    private val backgroundColor = Paint()
    private val progressColor = Paint()

    private var backgroundRect: Rect? = null
    private var progressRect: Rect? = null

    init {
        init()
    }

    private fun init() {
        val lineProgress = context.getColor(R.color.progress_color)
        val lineBackground = context.getColor(R.color.background_progress_color)

        progressHeight =
            context.resources.getDimensionPixelOffset(R.dimen.progress_video_line_height)

        backgroundColor.isAntiAlias = true
        backgroundColor.color = lineBackground

        progressColor.isAntiAlias = true
        progressColor.color = lineProgress
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val minW = paddingLeft + paddingRight + suggestedMinimumWidth
        viewWidth = resolveSizeAndState(minW, widthMeasureSpec, 1)

        val minH = paddingBottom + paddingTop + progressHeight
        val viewHeight = resolveSizeAndState(minH, heightMeasureSpec, 1)

        setMeasuredDimension(viewWidth, viewHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawLineBackground(canvas)
        drawLineProgress(canvas)
    }

    private fun drawLineBackground(canvas: Canvas) {
        if (backgroundRect != null) {
            canvas.drawRect(backgroundRect!!, backgroundColor)
        }
    }

    private fun drawLineProgress(canvas: Canvas) {
        if (progressRect != null) {
            canvas.drawRect(progressRect!!, progressColor)
        }
    }

    override fun onCreate(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
        updateBackgroundRect(index, value)
    }

    override fun onSeek(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
        updateBackgroundRect(index, value)
    }

    override fun onSeekStart(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
        updateBackgroundRect(index, value)
    }

    override fun onSeekStop(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
        updateBackgroundRect(index, value)
    }

    private fun updateBackgroundRect(index: Int, value: Float) {
        if (backgroundRect == null) backgroundRect = Rect(0, 0, viewWidth, progressHeight)
        val newValue = (viewWidth * value / 100).toInt()
        backgroundRect = if (index == 0) {
            Rect(newValue, backgroundRect!!.top, backgroundRect!!.right, backgroundRect!!.bottom)
        } else {
            Rect(backgroundRect!!.left, backgroundRect!!.top, newValue, backgroundRect!!.bottom)
        }
        updateProgress(0f, 0L, (0.0).toLong())
    }

    override fun updateProgress(time: Float, max: Long, scale: Long) {
        if (backgroundRect != null) {
            progressRect = if (scale == 0L) {
                Rect(0, backgroundRect!!.top, 0, backgroundRect!!.bottom)
            } else {
                val newValue = (viewWidth * scale / 100).toInt()
                Rect(backgroundRect!!.left, backgroundRect!!.top, newValue, backgroundRect!!.bottom)
            }
        }
        invalidate()
    }
}