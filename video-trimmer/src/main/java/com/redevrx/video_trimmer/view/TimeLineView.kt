package com.redevrx.video_trimmer.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.util.LongSparseArray
import android.view.View
import com.redevrx.video_trimmer.R
import com.redevrx.video_trimmer.utils.BackgroundExecutor
import com.redevrx.video_trimmer.utils.UiThreadExecutor
import kotlin.math.ceil
import androidx.core.graphics.scale
import androidx.core.util.size

class TimeLineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var videoUri: Uri? = null
    private var heightView: Int = 0
    private var bitmapList: LongSparseArray<Bitmap>? = null

    init {
        init()
    }

    private fun init() {
        heightView = context.resources.getDimensionPixelOffset(R.dimen.frames_video_height)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minW = paddingLeft + paddingRight + suggestedMinimumWidth
        val w = resolveSizeAndState(minW, widthMeasureSpec, 1)
        val minH = paddingBottom + paddingTop + heightView
        val h = resolveSizeAndState(minH, heightMeasureSpec, 1)
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        if (w != oldW) getBitmap(w)
    }

    private fun getBitmap(viewWidth: Int) {
        BackgroundExecutor.execute(object : BackgroundExecutor.Task("", 0L, "") {
            override fun execute() {
                try {
                    val threshold = 10
                    val thumbnailList = LongSparseArray<Bitmap>()
                    val mediaMetadataRetriever = MediaMetadataRetriever()
                    mediaMetadataRetriever.setDataSource(context, videoUri)
                    val videoLengthInMs = (Integer.parseInt(
                        "${mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)}"
                    ) * 1000).toLong()
                    val frameHeight = heightView
                    val initialBitmap = mediaMetadataRetriever.getFrameAtTime(
                        0,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                    val frameWidth =
                        ((initialBitmap?.width?.toFloat()!! / initialBitmap.height.toFloat()) * frameHeight.toFloat()).toInt()
                    var numThumbs = ceil((viewWidth.toFloat() / frameWidth)).toInt()
                    if (numThumbs < threshold) numThumbs = threshold
                    val cropWidth = viewWidth / threshold
                    val interval = videoLengthInMs / numThumbs
                    for (i in 0 until numThumbs) {
                        var bitmap = mediaMetadataRetriever.getFrameAtTime(
                            i * interval,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )
                        if (bitmap != null) {
                            try {
                                bitmap = bitmap.scale(frameWidth, frameHeight, false)
                                bitmap = Bitmap.createBitmap(bitmap, 0, 0, cropWidth, bitmap.height)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            thumbnailList.put(i.toLong(), bitmap)
                        }
                    }
                    mediaMetadataRetriever.release()
                    returnBitmaps(thumbnailList)
                } catch (e: Throwable) {
                    Thread.getDefaultUncaughtExceptionHandler()
                        ?.uncaughtException(Thread.currentThread(), e)
                }
            }
        })
    }

    private fun returnBitmaps(thumbnailList: LongSparseArray<Bitmap>) {
        Log.d("TimeLineView", "returnBitmaps() thumbnailList.size: ${thumbnailList.size}")
        UiThreadExecutor.runTask("", {
            bitmapList = thumbnailList
            invalidate()
        }, 0L)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bitmapList != null) {
            canvas.save()
            var x = 0
            for (i in 0 until (bitmapList?.size ?: 0)) {
                val bitmap = bitmapList?.get(i.toLong())
                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, x.toFloat(), 0f, null)
                    x += bitmap.width
                }
            }
        }
    }

    fun setVideo(data: Uri) {
        videoUri = data
    }
}