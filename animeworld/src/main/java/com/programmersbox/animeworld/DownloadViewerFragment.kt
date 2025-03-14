package com.programmersbox.animeworld

import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.core.net.toUri
import com.google.android.material.composethemeadapter.MdcTheme
import com.programmersbox.animeworld.ytsdatabase.*
import com.programmersbox.dragswipe.*
import com.programmersbox.helpfulutils.notificationManager
import com.programmersbox.helpfulutils.sizedListOf
import com.programmersbox.uiviews.BaseMainActivity
import com.programmersbox.uiviews.utils.BaseBottomSheetDialogFragment
import com.programmersbox.uiviews.utils.BottomSheetDeleteScaffold
import com.tonyodev.fetch2.*
import com.tonyodev.fetch2core.Extras
import java.text.DecimalFormat
import kotlin.random.Random

class DownloadViewerFragment : BaseBottomSheetDialogFragment(), ActionListener {

    companion object {
        private const val UNKNOWN_REMAINING_TIME: Long = -1
        private const val UNKNOWN_DOWNLOADED_BYTES_PER_SECOND: Long = 0
    }

    private val fetch: Fetch = Fetch.getDefaultInstance()
    private val downloadSubject = mutableStateListOf<DownloadData>()

    @ExperimentalMaterialApi
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(viewLifecycleOwner))
            setContent { MdcTheme { ScaffoldUi() } }
        }
    }

    override fun onResume() {
        super.onResume()
        fetch.getDownloads { downloads ->
            val list = ArrayList(downloads)
            list.sortWith { first, second -> first.created.compareTo(second.created) }
            downloadSubject.addAll(list.map { DownloadData(it) })
        }.addListener(fetchListener)
    }

    override fun onPause() {
        super.onPause()
        fetch.removeListener(fetchListener)
    }

    private fun updateUI(
        download: Download,
        etaTime: Long = UNKNOWN_REMAINING_TIME,
        downloadPs: Long = UNKNOWN_DOWNLOADED_BYTES_PER_SECOND,
        addOrRemove: Boolean = true
    ) {
        val d = downloadSubject.withIndex().find { it.value.id == download.id }

        if (addOrRemove) {

            val item = DownloadData(download).apply {
                eta = etaTime
                downloadedBytesPerSecond = downloadPs
            }

            if (d == null) {
                downloadSubject.add(item)
            } else {
                downloadSubject[d.index] = item
            }
        } else {
            if (download.status == Status.REMOVED || download.status == Status.DELETED) {
                downloadSubject.removeAt(d!!.index)
            }
        }
    }

    private val fetchListener = object : AbstractFetchListener() {
        override fun onQueued(download: Download, waitingOnNetwork: Boolean) {
            updateUI(download)
        }

        override fun onCompleted(download: Download) {
            updateUI(download)
            context?.notificationManager?.cancel(download.id)
        }

        override fun onError(download: Download, error: Error, throwable: Throwable?) {
            super.onError(download, error, throwable)
            updateUI(download)
        }

        override fun onProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
            updateUI(download, etaInMilliSeconds, downloadedBytesPerSecond)
        }

        override fun onPaused(download: Download) {
            updateUI(download)
        }

        override fun onResumed(download: Download) {
            updateUI(download)
        }

        override fun onCancelled(download: Download) {
            updateUI(download)
            context?.notificationManager?.cancel(download.id)
        }

        override fun onRemoved(download: Download) {
            //updateUI(download)
            downloadSubject.removeAll { it.id == download.id }
        }

        override fun onDeleted(download: Download) {
            //updateUI(download)
            downloadSubject.removeAll { it.id == download.id }
            context?.notificationManager?.cancel(download.id)
        }
    }

    override fun onPauseDownload(id: Int) {
        fetch.pause(id)
    }

    override fun onResumeDownload(id: Int) {
        fetch.resume(id)
    }

    override fun onRemoveDownload(id: Int) {
        fetch.remove(id)
    }

    override fun onRetryDownload(id: Int) {
        fetch.retry(id)
    }

    @ExperimentalMaterialApi
    @Composable
    private fun ScaffoldUi() {
        BottomSheetDeleteScaffold(
            listOfItems = downloadSubject,
            multipleTitle = stringResource(id = R.string.delete),
            onRemove = { download -> context?.deleteDialog(download.download) },
            customSingleRemoveDialog = { download ->
                context?.deleteDialog(download.download)
                false
            },
            onMultipleRemove = { downloadItems -> fetch.delete(downloadItems.map { it.id }) },
            topBar = {
                TopAppBar(
                    actions = {
                        IconButton(onClick = { dismiss() }) { Icon(Icons.Default.Close, null) }
                    },
                    title = {
                        Text(
                            stringResource(id = R.string.in_progress_downloads),
                            style = MaterialTheme.typography.h5
                        )
                    }
                )
            },
            itemUi = { download ->
                Column(modifier = Modifier.padding(5.dp)) {
                    Text(download.download.url.toUri().lastPathSegment.orEmpty())
                    var prog = download.download.progress
                    if (prog == -1) { // Download progress is undermined at the moment.
                        prog = 0
                    }
                    Text(stringResource(R.string.percent_progress, prog))
                }
            }
        ) {
            if (downloadSubject.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.padding(top = 5.dp),
                    contentPadding = it,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) { items(downloadSubject) { d -> DownloadItem(d, this@DownloadViewerFragment) } }
            }
        }
    }

    @Composable
    private fun EmptyState() {
        Box(modifier = Modifier.fillMaxSize()) {

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp),
                elevation = 5.dp,
                shape = RoundedCornerShape(5.dp)
            ) {

                Column(modifier = Modifier) {

                    Text(
                        text = stringResource(id = R.string.get_started),
                        style = MaterialTheme.typography.h4,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Text(
                        text = stringResource(id = R.string.download_a_video),
                        style = MaterialTheme.typography.body1,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Button(
                        onClick = {
                            dismiss()
                            (activity as? BaseMainActivity)?.goToScreen(BaseMainActivity.Screen.RECENT)
                        },
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 5.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.go_download),
                            style = MaterialTheme.typography.button
                        )
                    }

                }

            }
        }
    }

    @ExperimentalMaterialApi
    @Composable
    private fun DownloadItem(download: DownloadData, actionListener: ActionListener) {

        val context = LocalContext.current

        val dismissState = rememberDismissState(
            confirmStateChange = {
                if (it == DismissValue.DismissedToEnd || it == DismissValue.DismissedToStart) {
                    context.deleteDialog(download.download)
                }
                false
            }
        )

        SwipeToDismiss(
            state = dismissState,
            directions = setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart),
            dismissThresholds = { FractionalThreshold(0.5f) },
            background = {
                val direction = dismissState.dismissDirection ?: return@SwipeToDismiss
                val color by animateColorAsState(
                    when (dismissState.targetValue) {
                        DismissValue.Default -> Color.Transparent
                        DismissValue.DismissedToEnd -> Color.Red
                        DismissValue.DismissedToStart -> Color.Red
                    }
                )
                val alignment = when (direction) {
                    DismissDirection.StartToEnd -> Alignment.CenterStart
                    DismissDirection.EndToStart -> Alignment.CenterEnd
                }
                val icon = when (direction) {
                    DismissDirection.StartToEnd -> Icons.Default.Delete
                    DismissDirection.EndToStart -> Icons.Default.Delete
                }
                val scale by animateFloatAsState(if (dismissState.targetValue == DismissValue.Default) 0.75f else 1f)

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(color)
                        .padding(horizontal = 20.dp),
                    contentAlignment = alignment
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.scale(scale),
                        tint = MaterialTheme.colors.onSurface
                    )
                }
            }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 5.dp)
            ) {

                ConstraintLayout {

                    val (title, progress, action, progressText, speed, remaining, status) = createRefs()

                    Text(
                        download.download.url.toUri().lastPathSegment.orEmpty(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .constrainAs(title) {
                                start.linkTo(parent.start)
                                top.linkTo(parent.top)
                            }
                            .padding(horizontal = 8.dp)
                            .padding(top = 8.dp)
                    )

                    var prog = download.download.progress
                    if (prog == -1) { // Download progress is undermined at the moment.
                        prog = 0
                    }

                    LinearProgressIndicator(
                        progress = prog.toFloat() / 100f,
                        modifier = Modifier
                            .constrainAs(progress) {
                                start.linkTo(parent.start)
                                bottom.linkTo(action.bottom)
                                end.linkTo(action.start)
                                top.linkTo(action.top)
                            }
                            .padding(8.dp)
                    )

                    OutlinedButton(
                        onClick = {
                            when (download.download.status) {
                                Status.FAILED -> actionListener.onRetryDownload(download.download.id)
                                Status.PAUSED -> actionListener.onResumeDownload(download.download.id)
                                Status.DOWNLOADING, Status.QUEUED -> actionListener.onPauseDownload(download.download.id)
                                Status.ADDED -> actionListener.onResumeDownload(download.download.id)
                                else -> {
                                }
                            }
                        },
                        modifier = Modifier
                            .constrainAs(action) {
                                end.linkTo(parent.end)
                                top.linkTo(title.bottom)
                            }
                            .padding(top = 8.dp, end = 8.dp)
                    ) {
                        Text(
                            stringResource(
                                id =
                                when (download.download.status) {
                                    Status.COMPLETED -> R.string.view
                                    Status.FAILED -> R.string.retry
                                    Status.PAUSED -> R.string.resume
                                    Status.DOWNLOADING, Status.QUEUED -> R.string.pause
                                    Status.ADDED -> R.string.download
                                    else -> R.string.error_text
                                }
                            ),
                            style = MaterialTheme.typography.button
                        )
                    }

                    Text(
                        stringResource(R.string.percent_progress, prog),
                        modifier = Modifier
                            .constrainAs(progressText) {
                                top.linkTo(progress.bottom)
                                start.linkTo(progress.start)
                            }
                            .padding(horizontal = 8.dp)
                    )

                    Text(
                        if (download.downloadedBytesPerSecond == 0L) "" else getDownloadSpeedString(download.downloadedBytesPerSecond),
                        modifier = Modifier
                            .constrainAs(speed) {
                                top.linkTo(progress.bottom)
                                end.linkTo(progress.end)
                            }
                            .padding(horizontal = 8.dp)
                            .padding(bottom = 8.dp)
                    )

                    Text(
                        if (download.eta == -1L) "" else getETAString(download.eta, true),
                        modifier = Modifier
                            .constrainAs(remaining) {
                                bottom.linkTo(parent.bottom)
                                baseline.linkTo(parent.baseline)
                                top.linkTo(progressText.bottom)
                                start.linkTo(parent.start)
                            }
                            .padding(8.dp)
                    )

                    Text(
                        stringResource(id = getStatusString(download.download.status)),
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .constrainAs(status) {
                                bottom.linkTo(parent.bottom)
                                baseline.linkTo(parent.baseline)
                                top.linkTo(remaining.top)
                                start.linkTo(remaining.end)
                                end.linkTo(parent.end)
                            }
                            .padding(8.dp)
                    )

                }

            }
        }
    }

    private fun getStatusString(status: Status): Int = when (status) {
        Status.COMPLETED -> R.string.done
        Status.DOWNLOADING -> R.string.downloading_no_dots
        Status.FAILED -> R.string.error_text
        Status.PAUSED -> R.string.paused_text
        Status.QUEUED -> R.string.waiting_in_queue
        Status.REMOVED -> R.string.removed_text
        Status.NONE -> R.string.not_queued
        else -> R.string.unknown
    }

    private fun getDownloadSpeedString(downloadedBytesPerSecond: Long): String {
        if (downloadedBytesPerSecond < 0) {
            return ""
        }
        val kb = downloadedBytesPerSecond.toDouble() / 1000.toDouble()
        val mb = kb / 1000.toDouble()
        val gb = mb / 1000
        val tb = gb / 1000
        val decimalFormat = DecimalFormat(".##")
        return when {
            tb >= 1 -> "${decimalFormat.format(tb)} tb/s"
            gb >= 1 -> "${decimalFormat.format(gb)} gb/s"
            mb >= 1 -> "${decimalFormat.format(mb)} mb/s"
            kb >= 1 -> "${decimalFormat.format(kb)} kb/s"
            else -> "$downloadedBytesPerSecond b/s"
        }
    }

    private fun getETAString(etaInMilliSeconds: Long, needLeft: Boolean = true): String {
        if (etaInMilliSeconds < 0) {
            return ""
        }
        var seconds = (etaInMilliSeconds / 1000).toInt()
        val hours = (seconds / 3600).toLong()
        seconds -= (hours * 3600).toInt()
        val minutes = (seconds / 60).toLong()
        seconds -= (minutes * 60).toInt()
        return when {
            hours > 0 -> String.format("%02d:%02d:%02d hours", hours, minutes, seconds)
            minutes > 0 -> String.format("%02d:%02d mins", minutes, seconds)
            else -> "$seconds secs"
        } + (if (needLeft) " left" else "")
    }

    @ExperimentalMaterialApi
    @Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, showSystemUi = true, device = Devices.PIXEL_2, name = "Light")
    @Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, showSystemUi = true, device = Devices.PIXEL_2, name = "Dark")
    @Composable
    fun PreviewDownloadItem() {
        MaterialTheme { ScaffoldUi() }

        val list = sizedListOf(5) {
            DownloadData(
                object : Download {
                    override val autoRetryAttempts: Int get() = 1
                    override val autoRetryMaxAttempts: Int get() = 1
                    override val created: Long get() = 1L
                    override val downloadOnEnqueue: Boolean get() = true
                    override val downloaded: Long get() = 1L
                    override val downloadedBytesPerSecond: Long get() = 60
                    override val enqueueAction: EnqueueAction get() = EnqueueAction.REPLACE_EXISTING
                    override val error: Error get() = Error.UNKNOWN
                    override val etaInMilliSeconds: Long get() = 60000
                    override val extras: Extras get() = Extras.emptyExtras
                    override val file: String get() = ""
                    override val fileUri: Uri get() = Uri.parse("")
                    override val group: Int get() = 1
                    override val headers: Map<String, String> get() = emptyMap()
                    override val id: Int get() = 1
                    override val identifier: Long get() = 0L
                    override val namespace: String get() = ""
                    override val networkType: NetworkType get() = NetworkType.ALL
                    override val priority: Priority get() = Priority.HIGH
                    override val progress: Int get() = Random.nextInt(1, 100)
                    override val request: Request get() = Request("", "")
                    override val status: Status get() = Status.DOWNLOADING
                    override val tag: String get() = "tag"
                    override val total: Long get() = 0L
                    override val url: String get() = "https://raw.githubusercontent.com/jakepurple13/OtakuWorld/master/update.json"

                    override fun copy(): Download = this

                    override fun describeContents(): Int = 0

                    override fun writeToParcel(p0: Parcel?, p1: Int) {

                    }

                }
            ).apply {
                eta = 100000L
                downloadedBytesPerSecond = 13245674L
            }
        }

        downloadSubject.addAll(list)
    }

}

interface ActionListener {
    fun onPauseDownload(id: Int)
    fun onResumeDownload(id: Int)
    fun onRemoveDownload(id: Int)
    fun onRetryDownload(id: Int)
}

class DownloadData(val download: Download, val id: Int = download.id) {
    var eta: Long = -1
    var downloadedBytesPerSecond: Long = 0
    override fun hashCode(): Int {
        return id
    }

    override fun toString(): String = download.toString()

    override fun equals(other: Any?): Boolean {
        return other === this || other is DownloadData && other.id == id
    }
}