package com.programmersbox.animeworldtv

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.leanback.widget.*
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.PlaybackControlsRow.*
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.ext.leanback.LeanbackPlayerAdapter
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.video.VideoSize
import com.programmersbox.models.ChapterModel
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import java.util.*
import java.util.concurrent.TimeUnit


/** Handles video playback with media controls. */
class PlaybackVideoFragment : VideoSupportFragment() {

    private lateinit var mTransportControlGlue: VideoPlayerGlue//PlaybackTransportControlGlue<MediaPlayerAdapter>

    private val disposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*val (_, title, description, _, _, videoUrl) =
            activity?.intent?.getSerializableExtra(DetailsActivity.MOVIE) as Movie*/

        val item = try {
            activity?.intent?.getSerializableExtra(DetailsActivity.MOVIE) as ChapterModel
        } catch (e: Exception) {
            //e.printStackTrace()
            Toast.makeText(requireContext(), "Something went wrong", Toast.LENGTH_SHORT).show()
            activity?.finish()
            return
        }

        val glueHost = VideoSupportFragmentGlueHost(this@PlaybackVideoFragment)
        /*val bandwidthMeter = DefaultBandwidthMeter()
        val videoTrackSelectionFactory = AdaptiveTrackSelection.Factory(bandwidthMeter)
        val trackSelector = DefaultTrackSelector(videoTrackSelectionFactory)*/
        val exoPlayer = SimpleExoPlayer.Builder(requireContext()).build()//.newSimpleInstance(activity, trackSelector)
        val playerAdapter = LeanbackPlayerAdapter(requireActivity(), exoPlayer, 16)
        //MediaPlayerAdapter(context)
        playerAdapter.setRepeatAction(PlaybackControlsRow.RepeatAction.INDEX_NONE)

        mTransportControlGlue = VideoPlayerGlue(context, playerAdapter, null)//PlaybackTransportControlGlue(activity, playerAdapter)
        mTransportControlGlue.host = glueHost
        mTransportControlGlue.title = item.name//title
        mTransportControlGlue.subtitle = item.source.serviceName//description
        mTransportControlGlue.playWhenPrepared()

        //playerAdapter.setDataSource(Uri.parse(videoUrl))

        item.getChapterInfo()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnError {
                Toast.makeText(requireContext(), "Something went wrong", Toast.LENGTH_SHORT).show()
                activity?.finish()
            }
            .subscribeBy {
                //playerAdapter.setDataSource(Uri.parse(it.firstOrNull()?.link))
                /*val userAgent: String = com.google.android.exoplayer2.util.Util.getUserAgent(requireActivity(), "VideoPlayerGlue")
                val mediaSource: MediaSource = DefaultMediaSourceFactory(
                    Uri.parse(it.firstOrNull()?.link),
                    DefaultDataSourceFactory(requireActivity(), userAgent),
                    DefaultExtractorsFactory(),
                    null,
                    null
                )*/

                val link = it.firstOrNull()

                val dataSourceFactory = if (link?.headers?.isEmpty() == true) {
                    DefaultDataSourceFactory(
                        requireContext(),
                        com.google.android.exoplayer2.util.Util.getUserAgent(requireContext(), "AnimeWorld")
                    )
                } else {
                    DefaultHttpDataSource.Factory()
                        .setUserAgent(com.google.android.exoplayer2.util.Util.getUserAgent(requireContext(), "AnimeWorld"))
                        .setDefaultRequestProperties(hashMapOf("Referer" to link?.headers?.get("referer").orEmpty()))
                }

                /*val dataSourceFactory = DefaultDataSourceFactory(
                    requireContext(),
                    com.google.android.exoplayer2.util.Util.getUserAgent(requireContext(), "AnimeWorld")
                )*/
                val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(link?.link)))

                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.playWhenReady = true
                mTransportControlGlue.play()
            }
            .addTo(disposable)

        setupStatsForNerds(exoPlayer)
    }

    private fun setupStatsForNerds(exoPlayer: SimpleExoPlayer) {

        val bandwidthSubject = BehaviorSubject.create<Int>()
        val resolutionSubject = BehaviorSubject.create<String>()
        val bitrateSubject = BehaviorSubject.create<Double>()
        val droppedFramesSubject = BehaviorSubject.createDefault(0)

        /*
        Dropped frames of video - got it
        Current frames of video per second
        Video dimensions - got it
        Stream type
        Resolution - got it
         */

        exoPlayer.addAnalyticsListener(object : AnalyticsListener {
            override fun onBandwidthEstimate(
                eventTime: AnalyticsListener.EventTime,
                totalLoadTimeMs: Int,
                totalBytesLoaded: Long,
                bitrateEstimate: Long
            ) {
                //bandwidth
                bitrateSubject.onNext((bitrateEstimate * 8).toDouble() / (totalLoadTimeMs / 1000))
            }

            override fun onVideoSizeChanged(eventTime: AnalyticsListener.EventTime, videoSize: VideoSize) {
                super.onVideoSizeChanged(eventTime, videoSize)
                //this is resolution
                //exoPlayer.videoFormat
                //exoPlayer.audioFormat
                exoPlayer.videoFormat
                    ?.let(this@PlaybackVideoFragment::getResolution)
                    ?.let(resolutionSubject::onNext)
            }

            override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                droppedFramesSubject.onNext(droppedFramesSubject.value!! + droppedFrames)
            }

        })

        val presenterSelector = ClassPresenterSelector()
        presenterSelector.addClassPresenter(mTransportControlGlue.controlsRow::class.java, mTransportControlGlue.playbackRowPresenter)
        presenterSelector.addClassPresenter(ListRow::class.java, ListRowPresenter())

        val rowsAdapter = ArrayObjectAdapter(presenterSelector)

        rowsAdapter.add(mTransportControlGlue.controlsRow)

        val statsAdapter = ArrayObjectAdapter(GridItemPresenter())

        listOf(
            Stats("Bandwidth", bandwidthSubject),
            Stats("Bitrate", bitrateSubject),
            Stats("Total Dropped Frames", droppedFramesSubject),
            Stats("Resolution", resolutionSubject)
        )
            .forEach(statsAdapter::add)

        val header = HeaderItem("Stats for Nerds")
        val row = ListRow(header, statsAdapter)
        rowsAdapter.add(row)

        adapter = rowsAdapter
    }

    class Stats<T : Any>(val type: String, val subject: Observable<T>)

    private fun getResolution(videoSize: Format): String =
        "${videoSize.width}x${videoSize.height}${if (videoSize.frameRate > 0) "@${videoSize.frameRate.toInt()}" else ""}"

    private fun toHumanReadable(bitrate: Int): String {
        if (bitrate < 0) {
            return "none"
        }
        val mbit = bitrate.toFloat() / 1000000
        return String.format(Locale.ENGLISH, "%.2fMbit", mbit)
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.dispose()
    }

    override fun onPause() {
        super.onPause()
        mTransportControlGlue.pause()
    }

    private inner class GridItemPresenter : Presenter() {

        private val GRID_ITEM_WIDTH = 200
        private val GRID_ITEM_HEIGHT = 200

        override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
            val view = TextView(parent.context)
            view.layoutParams = ViewGroup.LayoutParams(GRID_ITEM_WIDTH, GRID_ITEM_HEIGHT)
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.setBackgroundColor(ContextCompat.getColor(context!!, R.color.default_background))
            view.setTextColor(Color.WHITE)
            view.gravity = Gravity.CENTER
            return Presenter.ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any) {
            //(viewHolder.view as TextView).text = item as String

            val textView = viewHolder.view as? TextView

            when (item) {
                is Stats<*> -> {
                    textView?.text = item.type
                    item
                        .subject
                        .subscribe { textView?.text = "${item.type}: $it" }
                        .addTo(disposable)
                }
                is String -> {
                    textView?.text = item
                }
            }

        }

        override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {}
    }
}

class VideoPlayerGlue(
    context: Context?,
    playerAdapter: LeanbackPlayerAdapter?,
    private val mActionListener: OnActionClickedListener?
) : PlaybackTransportControlGlue<LeanbackPlayerAdapter?>(context, playerAdapter) {
    /** Listens for when skip to next and previous actions have been dispatched.  */
    interface OnActionClickedListener {
        /** Skip to the previous item in the queue.  */
        fun onPrevious()

        /** Skip to the next item in the queue.  */
        fun onNext()
    }

    private val mMoreAction: MoreActions = MoreActions(context)
    private val mThumbsUpAction: ThumbsUpAction = ThumbsUpAction(context)
    private val mThumbsDownAction: ThumbsDownAction = ThumbsDownAction(context)
    private val mSkipPreviousAction: SkipPreviousAction = SkipPreviousAction(context)
    private val mSkipNextAction: SkipNextAction = SkipNextAction(context)
    private val mFastForwardAction: FastForwardAction = FastForwardAction(context)
    private val mRewindAction: RewindAction = RewindAction(context)
    override fun onCreatePrimaryActions(adapter: ArrayObjectAdapter) {
        // Order matters, super.onCreatePrimaryActions() will create the play / pause action.
        // Will display as follows:
        // play/pause, previous, rewind, fast forward, next
        //   > /||      |<        <<        >>         >|
        super.onCreatePrimaryActions(adapter)
        adapter.add(mSkipPreviousAction)
        adapter.add(mRewindAction)
        adapter.add(mFastForwardAction)
        adapter.add(mSkipNextAction)
    }

    override fun onCreateSecondaryActions(adapter: ArrayObjectAdapter) {
        super.onCreateSecondaryActions(adapter)
        adapter.add(mThumbsDownAction)
        adapter.add(mThumbsUpAction)
        adapter.add(mMoreAction)
        //add stats for nerds here
    }

    override fun onActionClicked(action: Action) {
        if (shouldDispatchAction(action)) {
            dispatchAction(action)
            return
        }
        // Super class handles play/pause and delegates to abstract methods next()/previous().
        super.onActionClicked(action)
    }

    // Should dispatch actions that the super class does not supply callbacks for.
    private fun shouldDispatchAction(action: Action): Boolean {
        return action === mRewindAction || action === mFastForwardAction ||
                action === mSkipNextAction ||
                action === mThumbsDownAction || action === mThumbsUpAction || action === mMoreAction
    }

    private fun dispatchAction(action: Action) {
        // Primary actions are handled manually.
        when {
            action === mRewindAction -> {
                rewind()
            }
            action === mFastForwardAction -> {
                fastForward()
            }
            action === mSkipNextAction -> {
                skipOpening()
            }
            action === mMoreAction -> {

            }
            action is MultiAction -> {
                val multiAction = action as MultiAction
                multiAction.nextIndex()
                // Notify adapter of action changes to handle secondary actions, such as, thumbs up/down
                // and repeat.
                notifyActionChanged(
                    multiAction,
                    controlsRow.secondaryActionsAdapter as ArrayObjectAdapter
                )
            }
        }
    }

    private fun notifyActionChanged(
        action: MultiAction, adapter: ArrayObjectAdapter?
    ) {
        if (adapter != null) {
            val index = adapter.indexOf(action)
            if (index >= 0) {
                adapter.notifyArrayItemRangeChanged(index, 1)
            }
        }
    }

    override fun next() {
        mActionListener?.onNext()
    }

    override fun previous() {
        mActionListener?.onPrevious()
    }

    /** Skips backwards 10 seconds.  */
    fun rewind() {
        var newPosition = currentPosition - TEN_SECONDS
        newPosition = if (newPosition < 0) 0 else newPosition
        playerAdapter!!.seekTo(newPosition)
    }

    /** Skips forward 10 seconds.  */
    fun fastForward() {
        if (duration > -1) {
            var newPosition = currentPosition + TEN_SECONDS
            newPosition = if (newPosition > duration) duration else newPosition
            playerAdapter!!.seekTo(newPosition)
        }
    }

    fun skipOpening() {
        if (duration > -1) {
            var newPosition = currentPosition + TimeUnit.SECONDS.toMillis(90)
            newPosition = if (newPosition > duration) duration else newPosition
            playerAdapter!!.seekTo(newPosition)
        }
    }

    companion object {
        private val TEN_SECONDS: Long = TimeUnit.SECONDS.toMillis(10)
    }

    init {
        mThumbsUpAction.index = ThumbsUpAction.INDEX_OUTLINE
        mThumbsDownAction.index = ThumbsDownAction.INDEX_OUTLINE
    }
}
