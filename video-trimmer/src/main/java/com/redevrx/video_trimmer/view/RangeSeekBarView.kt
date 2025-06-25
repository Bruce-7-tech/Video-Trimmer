package com.redevrx.video_trimmer.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.redevrx.video_trimmer.R
import com.redevrx.video_trimmer.event.OnRangeSeekBarEvent


class RangeSeekBarView @JvmOverloads constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {

    private var heightTimeLine = 0
    lateinit var thumbs: List<Thumb>
    private var listeners: MutableList<OnRangeSeekBarEvent>? = null
    private var maxWidth = 0f
    private var thumbWidth = 0f
    private var thumbHeight = 0f
    private var viewWidth = 0
    private var pixelRangeMin = 0f
    private var pixelRangeMax = 0f
    private var scaleRangeMax = 0f
    private var firstRun = false

    private val shadow = Paint()
    private val line = Paint()

    private var currentThumb = 0

    init {
        init()
    }

    private fun init() {
        thumbs = Thumb.initThumbs(resources)
        thumbWidth = Thumb.getWidthBitmap(thumbs).toFloat()
        thumbHeight = Thumb.getHeightBitmap(thumbs).toFloat()

        scaleRangeMax = 100f
        heightTimeLine = context.resources.getDimensionPixelOffset(R.dimen.frames_video_height)

        isFocusable = true
        isFocusableInTouchMode = true

        firstRun = true

        val shadowColor = context.getColor( R.color.shadow_color)
        shadow.isAntiAlias = true
        shadow.color = shadowColor
        shadow.alpha = 177

        val lineColor = context.getColor( R.color.line_color)
        line.isAntiAlias = true
        line.color = lineColor
        line.alpha = 200
    }

    fun initMaxWidth() {
        maxWidth = thumbs[1].pos - thumbs[0].pos
        onSeekStop(this, 0, thumbs[0].value)
        onSeekStop(this, 1, thumbs[1].value)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val minW = paddingLeft + paddingRight + suggestedMinimumWidth
        viewWidth = resolveSizeAndState(minW, widthMeasureSpec, 1)

        val minH = paddingBottom + paddingTop + thumbHeight.toInt() + heightTimeLine
        val viewHeight = resolveSizeAndState(minH, heightMeasureSpec, 1)

        setMeasuredDimension(viewWidth, viewHeight)

        pixelRangeMin = 0f
        pixelRangeMax = viewWidth - thumbWidth

        if (firstRun) {
            for (i in thumbs.indices) {
                val th = thumbs[i]
                th.value = scaleRangeMax * i
                th.pos = pixelRangeMax * i
            }
            onCreate(this, currentThumb, getThumbValue(currentThumb))
            firstRun = false
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawShadow(canvas)
        drawThumbs(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val mThumb: Thumb
        val mThumb2: Thumb
        val coordinate = ev.x
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                currentThumb = getClosestThumb(coordinate)
                if (currentThumb == -1) return false
                mThumb = thumbs[currentThumb]
                mThumb.lastTouchX = coordinate
                onSeekStart(this, currentThumb, mThumb.value)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (currentThumb == -1) return false
                mThumb = thumbs[currentThumb]
                onSeekStop(this, currentThumb, mThumb.value)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                mThumb = thumbs[currentThumb]
                mThumb2 = thumbs[if (currentThumb == 0) 1 else 0]
                // Calculate the distance moved
                val dx = coordinate - mThumb.lastTouchX
                val newX = mThumb.pos + dx
                if (currentThumb == 0) {
                    when {
                        newX + mThumb.widthBitmap >= mThumb2.pos -> mThumb.pos = mThumb2.pos - mThumb.widthBitmap
                        newX <= pixelRangeMin -> mThumb.pos = pixelRangeMin
                        else -> {
                            checkPositionThumb(mThumb, mThumb2, dx, true)
                            mThumb.pos = mThumb.pos + dx
                            mThumb.lastTouchX = coordinate
                        }
                    }

                } else {
                    when {
                        newX <= mThumb2.pos + mThumb2.widthBitmap -> mThumb.pos = mThumb2.pos + mThumb.widthBitmap
                        newX >= pixelRangeMax -> mThumb.pos = pixelRangeMax
                        else -> {
                            checkPositionThumb(mThumb2, mThumb, dx, false)
                            mThumb.pos = mThumb.pos + dx
                            mThumb.lastTouchX = coordinate
                        }
                    }
                }

                setThumbPos(currentThumb, mThumb.pos)
                invalidate()
                return true
            }
        }
        return false
    }

    private fun checkPositionThumb(mThumbLeft: Thumb, mThumbRight: Thumb, dx: Float, isLeftMove: Boolean) {
        if (isLeftMove && dx < 0) {
            if (mThumbRight.pos + dx - mThumbLeft.pos > maxWidth) {
                mThumbRight.pos = mThumbLeft.pos + dx + maxWidth
                setThumbPos(1, mThumbRight.pos)
            }
        } else if (!isLeftMove && dx > 0) {
            if (mThumbRight.pos + dx - mThumbLeft.pos > maxWidth) {
                mThumbLeft.pos = mThumbRight.pos + dx - maxWidth
                setThumbPos(0, mThumbLeft.pos)
            }
        }
    }

    private fun getUnstuckFrom(index: Int): Int {
        val unstuck = 0
        val lastVal = thumbs[index].value
        for (i in index - 1 downTo 0) {
            val th = thumbs[i]
            if (th.value != lastVal)
                return i + 1
        }
        return unstuck
    }

    private fun pixelToScale(index: Int, pixelValue: Float): Float {
        val scale = pixelValue * 100 / pixelRangeMax
        return if (index == 0) {
            val pxThumb = scale * thumbWidth / 100
            scale + pxThumb * 100 / pixelRangeMax
        } else {
            val pxThumb = (100 - scale) * thumbWidth / 100
            scale - pxThumb * 100 / pixelRangeMax
        }
    }

    private fun scaleToPixel(index: Int, scaleValue: Float): Float {
        val px = scaleValue * pixelRangeMax / 100
        return if (index == 0) {
            val pxThumb = scaleValue * thumbWidth / 100
            px - pxThumb
        } else {
            val pxThumb = (100 - scaleValue) * thumbWidth / 100
            px + pxThumb
        }
    }

    private fun calculateThumbValue(index: Int) {
        if (index < thumbs.size && thumbs.isNotEmpty()) {
            val th = thumbs[index]
            th.value = pixelToScale(index, th.pos)
            onSeek(this, index, th.value)
        }
    }

    private fun calculateThumbPos(index: Int) {
        if (index < thumbs.size && thumbs.isNotEmpty()) {
            val th = thumbs[index]
            th.pos = scaleToPixel(index, th.value)
        }
    }

    private fun getThumbValue(index: Int): Float = thumbs[index].value

    fun setThumbValue(index: Int, value: Long) {
        thumbs[index].value = value.toFloat()
        calculateThumbPos(index)
        invalidate()
    }

    private fun setThumbPos(index: Int, pos: Float) {
        thumbs[index].pos = pos
        calculateThumbValue(index)
        invalidate()
    }

    private fun getClosestThumb(coordinate: Float): Int {
        var closest = -1
        if (thumbs.isNotEmpty()) {
            for (i in thumbs.indices) {
                val tcoordinate = thumbs[i].pos + thumbWidth
                if (coordinate >= thumbs[i].pos && coordinate <= tcoordinate) {
                    closest = thumbs[i].index
                }
            }
        }
        return closest
    }

    private fun drawShadow(canvas: Canvas) {
        if (thumbs.isNotEmpty()) {
            for (th in thumbs) {
                if (th.index == 0) {
                    val x = th.pos + paddingLeft
                    if (x > pixelRangeMin) {
                        val mRect = Rect(thumbWidth.toInt(), 0, (x + thumbWidth).toInt(), heightTimeLine)
                        canvas.drawRect(mRect, shadow)
                    }
                } else {
                    val x = th.pos - paddingRight
                    if (x < pixelRangeMax) {
                        val mRect = Rect(x.toInt(), 0, (viewWidth - thumbWidth).toInt(), heightTimeLine)
                        canvas.drawRect(mRect, shadow)
                    }
                }
            }
        }
    }

    private fun drawThumbs(canvas: Canvas) {
        if (thumbs.isNotEmpty()) {
            for (th in thumbs) {
                if (th.index == 0) {
                    if (th.bitmap != null) canvas.drawBitmap(th.bitmap!!, th.pos + paddingLeft, 0f, null)
                } else {
                    if (th.bitmap != null) canvas.drawBitmap(th.bitmap!!, th.pos - paddingRight, 0f, null)
                }
            }
        }
    }

    fun addOnRangeSeekBarListener(listener: OnRangeSeekBarEvent) {
        if (listeners == null) listeners = ArrayList()
        listeners?.add(listener)
    }

    private fun onCreate(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
        if (listeners == null) return
        else {
            for (item in listeners!!) {
                item.onCreate(rangeSeekBarView, index, value)
            }
        }
    }

    private fun onSeek(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
        if (listeners == null) return
        else {
            for (item in listeners!!) {
                item.onSeek(rangeSeekBarView, index, value)
            }
        }
    }

    private fun onSeekStart(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
        if (listeners == null) return
        else {
            for (item in listeners!!) {
                item.onSeekStart(rangeSeekBarView, index, value)
            }
        }
    }

    private fun onSeekStop(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
        if (listeners == null) return
        else {
            for (item in listeners!!) {
                item.onSeekStop(rangeSeekBarView, index, value)
            }
        }
    }
}