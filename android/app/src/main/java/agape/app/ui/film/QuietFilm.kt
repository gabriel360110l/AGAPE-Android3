package agape.app.ui.film

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun QuietFilm(name: String, loop: Boolean, reduceMotion: Boolean, onEnd: () -> Unit = {}) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latestEnd by rememberUpdatedState(onEnd)

    if (reduceMotion) {
        LaunchedEffect(Unit) { latestEnd() }
        return
    }

    var videoFile by remember(name) { mutableStateOf<File?>(null) }

    LaunchedEffect(name) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                val version = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
                val dest = File(context.cacheDir, "$name-$version.mp4")
                if (!dest.exists() || dest.length() == 0L) {
                    val temp = File(context.cacheDir, "$name-$version.tmp")
                    context.assets.open("films/$name.mp4").use { input ->
                        temp.outputStream().use { input.copyTo(it) }
                    }
                    check(temp.renameTo(dest)) { "Unable to prepare video" }
                }
                dest
            }.getOrNull()
        }
        if (loaded == null) latestEnd() else videoFile = loaded
    }

    val preparedFile = videoFile ?: return

    val exoPlayer = remember(preparedFile.path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(preparedFile)))
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (loop) 0f else 0.9f
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED && !loop) latestEnd()
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    latestEnd()
                }
            })
        }
    }

    DisposableEffect(owner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> exoPlayer.play()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        onRelease = { (it as? PlayerView)?.player = null }
    )
}
