package com.redevrx.video_trimmer.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.ui.AspectRatioFrameLayout
import com.redevrx.video_trimmer.R
import com.redevrx.video_trimmer.databinding.TrimmerViewLayoutBinding
import com.redevrx.video_trimmer.event.OnProgressVideoEvent
import com.redevrx.video_trimmer.event.OnRangeSeekBarEvent
import com.redevrx.video_trimmer.event.OnVideoEditedEvent
import com.redevrx.video_trimmer.utils.BackgroundExecutor
import com.redevrx.video_trimmer.utils.TrimVideoUtils
import com.redevrx.video_trimmer.utils.UiThreadExecutor
import java.io.File
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.UUID
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource

class VideoEditor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var player: ExoPlayer
    private lateinit var src: Uri
    private var finalPath: String? = null

    private var maxDuration: Int = -1
    private var minDuration: Int = -1
    private var listeners: ArrayList<OnProgressVideoEvent> = ArrayList()

    private var onVideoEditedListener: OnVideoEditedEvent? = null

    private lateinit var binding: TrimmerViewLayoutBinding

    private var duration: Long = 0L
    private var timeVideo = 0L
    private var startPosition = 0L

    private var endPosition = 0L
    private var resetSeekBar = false
    private val messageHandler = MessageHandler(this)
    private var originalVideoWidth: Int = 0
    private var originalVideoHeight: Int = 0
    private var videoPlayerWidth: Int = 0
    private var videoPlayerHeight: Int = 0
    private var bitRate: Int = 2
    private var isVideoPrepared = false
    private var videoPlayerCurrentPosition = 0L
    private var destinationPath: String
        get() {
            if (finalPath == null) {
                val folder = context.cacheDir
                finalPath = folder.path + File.separator
            }
            return finalPath ?: ""
        }
        set(finalPath) {
            this@VideoEditor.finalPath = finalPath
        }

    init {
        init(context)
    }

    private fun init(context: Context) {
        binding = TrimmerViewLayoutBinding.inflate(LayoutInflater.from(context), this, true)
        setUpListeners()
        setUpMargins()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setUpListeners() {
        listeners = ArrayList()
        listeners.add(object : OnProgressVideoEvent {
            override fun updateProgress(time: Float, max: Long, scale: Long) {
                updateVideoProgress(time.toLong())
            }
        })

        val gestureDetector =
            GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    onClickVideoPlayPause()
                    Toast.makeText(context, "Click", Toast.LENGTH_LONG).show()
                    return true
                }
            })

        binding.iconVideoPlay.setOnClickListener {
            onClickVideoPlayPause()
        }

        binding.layoutSurfaceView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        binding.handlerTop.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                onPlayerIndicatorSeekChanged(progress, fromUser)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                onPlayerIndicatorSeekStart()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                onPlayerIndicatorSeekStop(seekBar)
            }
        })

        binding.timeLineBar.addOnRangeSeekBarListener(object : OnRangeSeekBarEvent {
            override fun onCreate(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
            }

            override fun onSeek(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
                binding.handlerTop.visibility = View.GONE
                onSeekThumbs(index, value)
            }

            override fun onSeekStart(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
            }

            override fun onSeekStop(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
                onStopSeekThumbs()
            }
        })
    }

    private fun onPlayerIndicatorSeekChanged(progress: Int, fromUser: Boolean) {
        val duration = (duration * progress / 1000L)
        if (fromUser) {
            if (duration < startPosition) setProgressBarPosition(startPosition)
            else if (duration > endPosition) setProgressBarPosition(endPosition)
        }
    }

    private fun onPlayerIndicatorSeekStart() {
        messageHandler.removeMessages(SHOW_PROGRESS)
        player.pause()
        binding.iconVideoPlay.visibility = View.VISIBLE
        notifyProgressUpdate(false)
    }

    private fun onPlayerIndicatorSeekStop(seekBar: SeekBar) {
        messageHandler.removeMessages(SHOW_PROGRESS)
        player.pause()
        binding.iconVideoPlay.visibility = View.VISIBLE

        val duration = (duration * seekBar.progress / 1000L).toInt()
        player.seekTo(duration.toLong())
        notifyProgressUpdate(false)
    }

    private fun setProgressBarPosition(position: Long) {
        if (duration > 0) binding.handlerTop.progress = (1000L * position / duration).toInt()
    }

    private fun setUpMargins() {
        val marge = binding.timeLineBar.thumbs[0].widthBitmap
        val lp = binding.timeLineView.layoutParams as LayoutParams
        lp.setMargins(marge, 0, marge, 0)
        binding.timeLineView.layoutParams = lp
    }


    private fun onClickVideoPlayPause() {
        if (player.isPlaying) {
            binding.iconVideoPlay.visibility = View.VISIBLE
            messageHandler.removeMessages(SHOW_PROGRESS)
            player.pause()
        } else {
            binding.iconVideoPlay.visibility = View.GONE
            if (resetSeekBar) {
                resetSeekBar = false
                player.seekTo(startPosition)
            }
            resetSeekBar = false
            messageHandler.sendEmptyMessage(SHOW_PROGRESS)
            player.play()
        }
    }

    fun onCancelClicked() {
        player.stop()
    }

    private fun onVideoPrepared(mp: ExoPlayer) {
        if (isVideoPrepared) {
            return
        }
        isVideoPrepared = true
        val videoWidth = mp.videoSize.width
        val videoHeight = mp.videoSize.height
        val videoProportion = videoWidth.toFloat() / videoHeight.toFloat()
        val screenWidth = binding.layoutSurfaceView.width
        val screenHeight = binding.layoutSurfaceView.height
        val screenProportion = screenWidth.toFloat() / screenHeight.toFloat()
        val lp = binding.videoLoader.layoutParams

        if (videoProportion > screenProportion) {
            lp.width = screenWidth
            lp.height = (screenWidth.toFloat() / videoProportion).toInt()
        } else {
            lp.width = (videoProportion * screenHeight.toFloat()).toInt()
            lp.height = screenHeight
        }
        videoPlayerWidth = lp.width
        videoPlayerHeight = lp.height
        binding.videoLoader.layoutParams = lp

        binding.iconVideoPlay.visibility = View.VISIBLE
        duration = player.duration
        setSeekBarPosition()
        setTimeFrames()
    }

    private fun setSeekBarPosition() {
        when {
            duration >= maxDuration && maxDuration != -1 -> {
                startPosition = duration / 2 - maxDuration / 2
                endPosition = duration / 2 + maxDuration / 2
                binding.timeLineBar.setThumbValue(0, (startPosition * 100 / duration))
                binding.timeLineBar.setThumbValue(1, (endPosition * 100 / duration))
            }

            duration <= minDuration && minDuration != -1 -> {
                startPosition = duration / 2 - minDuration / 2
                endPosition = duration / 2 + minDuration / 2
                binding.timeLineBar.setThumbValue(0, (startPosition * 100 / duration))
                binding.timeLineBar.setThumbValue(1, (endPosition * 100 / duration))
            }

            else -> {
                startPosition = 0L
                endPosition = duration
            }
        }
        player.seekTo(startPosition)
        timeVideo = duration
        binding.timeLineBar.initMaxWidth()
    }

    private fun setTimeFrames() {
        val seconds = context.getString(R.string.short_seconds)
        binding.textTimeSelection.text = String.format(
            Locale.ENGLISH,
            "%s %s - %s %s",
            TrimVideoUtils.stringForTime(startPosition),
            seconds,
            TrimVideoUtils.stringForTime(endPosition),
            seconds
        )
    }

    private fun onSeekThumbs(index: Int, value: Float) {
        when (index) {
            Thumb.LEFT -> {
                startPosition = ((duration * value / 100L).toLong())
                player.seekTo(startPosition)
            }

            Thumb.RIGHT -> {
                endPosition = ((duration * value / 100L).toLong())
            }
        }
        setTimeFrames()
        timeVideo = endPosition - startPosition
    }

    private fun onStopSeekThumbs() {
        messageHandler.removeMessages(SHOW_PROGRESS)
        player.pause()
        binding.iconVideoPlay.visibility = View.VISIBLE
    }

    private fun notifyProgressUpdate(all: Boolean) {
        if (duration == 0L) return
        val position = player.currentPosition
        if (all) {
            for (item in listeners) {
                item.updateProgress(position.toFloat(), duration, (position * 100 / duration))
            }
        } else {
            listeners[0].updateProgress(
                position.toFloat(),
                duration,
                (position * 100 / duration)
            )
        }
    }

    private fun updateVideoProgress(time: Long) {
        if (time <= startPosition && time <= endPosition) binding.handlerTop.visibility =
            View.GONE
        else binding.handlerTop.visibility = View.VISIBLE
        if (time >= endPosition) {
            messageHandler.removeMessages(SHOW_PROGRESS)
            player.pause()
            binding.iconVideoPlay.visibility = View.VISIBLE
            resetSeekBar = true
            return
        }
        setProgressBarPosition(time)
    }

    fun setVideoBackgroundColor(@ColorInt color: Int) = with(binding) {
        container.setBackgroundColor(color)
        layout.setBackgroundColor(color)
    }

    fun setFrameColor(@ColorInt color: Int) = with(binding) {
        frameColor.setBackgroundColor(color)
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun saveVideo() {
        val txtTime = binding.textTimeSelection.text.toString()
        val pattern = "\\d{2}:\\d{2}"
        val regex = Regex(pattern)
        val matches = regex.findAll(txtTime)
        val timeList = matches.map { it.value }.toList()

        val filePath = "$destinationPath/${UUID.randomUUID()}.mp4"

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H265)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    onVideoEditedListener?.getResult(filePath.toUri())
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    exportException.localizedMessage?.let {
                        onVideoEditedListener?.onError(it)
                    }
                }
            })
            .build()

        val startMilliseconds = timeToMilliseconds(timeList[0])
        val endMilliseconds = timeToMilliseconds(timeList[1])

        val inputMediaItem = MediaItem.Builder()
            .setUri(src)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMilliseconds)
                    .setEndPositionMs(endMilliseconds)
                    .build()
            )
            .build()

        val editedMediaItem = EditedMediaItem.Builder(inputMediaItem)
            .setFrameRate(30)
            .setEffects(Effects.EMPTY)
            .build()

        transformer.start(editedMediaItem, filePath)
    }

    private fun timeToMilliseconds(time: String): Long {
        val parts = time.split(":")
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid time format: $time")
        }

        val minutes = parts[0].toInt()
        val seconds = parts[1].trim().split(" ")[0].toInt() // Extract seconds

        // Calculate total milliseconds
        return ((minutes * 60 + seconds) * 1000).toLong()
    }

    fun setBitrate(bitRate: Int): VideoEditor {
        this.bitRate = bitRate
        return this
    }

    fun setVideoInformationVisibility(visible: Boolean): VideoEditor {
        binding.timeFrame.visibility = if (visible) View.VISIBLE else View.GONE
        return this
    }

    fun setOnTrimVideoListener(onVideoEditedListener: OnVideoEditedEvent): VideoEditor {
        this@VideoEditor.onVideoEditedListener = onVideoEditedListener
        return this
    }

    fun destroy() {
        BackgroundExecutor.cancelAll("", true)
        UiThreadExecutor.cancelAll("")
    }

    fun setMaxDuration(maxDuration: Int): VideoEditor {
        this@VideoEditor.maxDuration = maxDuration * 1000
        return this
    }

    fun setMinDuration(minDuration: Int): VideoEditor {
        this@VideoEditor.minDuration = minDuration * 1000
        return this
    }

    fun setDestinationPath(path: String): VideoEditor {
        destinationPath = path
        return this
    }

    @UnstableApi
    fun setVideoURI(videoURI: Uri): VideoEditor {
        src = videoURI

        player = ExoPlayer.Builder(context)
            .build()

        val dataSourceFactory = DefaultDataSource.Factory(context)
        val mediaItem = MediaItem.fromUri(videoURI)
        val videoSource: MediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)

        player.setMediaSource(videoSource)

        player.prepare()
        player.playWhenReady = false

        binding.videoLoader.also {
            it.player = player
            it.useController = false
        }

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onVideoEditedListener?.onError("Something went wrong reason : ${error.localizedMessage}")
            }

            @OptIn(UnstableApi::class) override fun onVideoSizeChanged(videoSize: VideoSize) {
                super.onVideoSizeChanged(videoSize)
                if (player.videoSize.width > player.videoSize.height) {
                    binding.videoLoader.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                } else {
                    binding.videoLoader.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                }

                onVideoPrepared(player)
            }
        })

        binding.videoLoader.requestFocus()
        binding.timeLineView.setVideo(src)
        val mediaMetadataRetriever = MediaMetadataRetriever()
        mediaMetadataRetriever.setDataSource(context, src)
        val metaDateWidth =
            mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toInt() ?: 0
        val metaDataHeight =
            mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toInt() ?: 0

        // If the rotation is 90 or 270 the width and height will be transposed.
        when (mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toInt()) {
            90, 270 -> {
                originalVideoWidth = metaDataHeight
                originalVideoHeight = metaDateWidth
            }

            else -> {
                originalVideoWidth = metaDateWidth
                originalVideoHeight = metaDataHeight
            }
        }

        return this
    }

    fun setTextTimeSelectionTypeface(tf: Typeface?): VideoEditor {
        if (tf != null) binding.textTimeSelection.typeface = tf
        return this
    }

    fun onResume() {
        player.seekTo(videoPlayerCurrentPosition)
    }

    fun onPause() {
        videoPlayerCurrentPosition = player.currentPosition
    }

    private class MessageHandler(view: VideoEditor) : Handler(Looper.getMainLooper()) {
        private val mView: WeakReference<VideoEditor> = WeakReference(view)
        override fun handleMessage(msg: Message) {
            val view = mView.get()
            if (view == null) {
                return
            }
            view.notifyProgressUpdate(true)
            if (view.binding.videoLoader.player?.isPlaying == true) {
                sendEmptyMessageDelayed(0, 10)
            }
        }
    }

    companion object {
        private const val SHOW_PROGRESS = 2
    }
}