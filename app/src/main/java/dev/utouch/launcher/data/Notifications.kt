package dev.utouch.launcher.data

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A media session the launcher can show on the home screen. */
data class NowPlaying(
    val packageName: String,
    val title: String,
    val artist: String?,
    val isPlaying: Boolean,
)

/**
 * Bridge between the notification listener (which lives outside Compose) and the ViewModel.
 * Everything here is best effort: a missing permission simply leaves the flows empty.
 */
object NotificationCenter {

    private val _badges = MutableStateFlow<Map<String, Int>>(emptyMap())
    val badges: StateFlow<Map<String, Int>> = _badges.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    @Volatile
    private var sessionToken: MediaSession.Token? = null

    internal fun publishBadges(counts: Map<String, Int>) {
        if (_badges.value != counts) _badges.value = counts
    }

    internal fun publishNowPlaying(state: NowPlaying?) {
        _nowPlaying.value = state
    }

    internal fun publishToken(token: MediaSession.Token?) {
        sessionToken = token
    }

    /** Forgets everything, e.g. when the feature flag is turned off. */
    fun clear() {
        _badges.value = emptyMap()
        _nowPlaying.value = null
        sessionToken = null
    }

    /** Play/pause the current media session, if the system still lets us. */
    fun togglePlayPause(context: Context, currentlyPlaying: Boolean) {
        val token = sessionToken ?: return
        runCatching {
            val controller = MediaController(context, token)
            if (currentlyPlaying) controller.transportControls.pause()
            else controller.transportControls.play()
        }
    }
}

/**
 * Reads notifications and the media session. The user grants access manually in the system
 * settings; without it [onBind] is never called and nothing happens.
 */
class LauncherNotificationService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private val refresh = Runnable { publish() }

    override fun onListenerConnected() {
        super.onListenerConnected()
        publish()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        scheduleRefresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        scheduleRefresh()
    }

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        super.onDestroy()
    }

    /** Notifications can arrive in bursts; one recompute per 250 ms is plenty. */
    private fun scheduleRefresh() {
        handler.removeCallbacks(refresh)
        handler.postDelayed(refresh, 250)
    }

    private fun publish() {
        val active = runCatching { activeNotifications }.getOrNull().orEmpty()

        val counts = HashMap<String, Int>()
        active.forEach { sbn ->
            if (!isUserVisible(sbn)) return@forEach
            counts[sbn.packageName] = (counts[sbn.packageName] ?: 0) + 1
        }
        NotificationCenter.publishBadges(counts)
        NotificationCenter.publishNowPlaying(readNowPlaying())
    }

    private fun isUserVisible(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == packageName) return false
        val notification = sbn.notification ?: return false
        if (!sbn.isClearable) return false
        return when (notification.category) {
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_SYSTEM,
            -> false

            else -> true
        }
    }

    private fun readNowPlaying(): NowPlaying? = runCatching {
        val manager = getSystemService(MediaSessionManager::class.java) ?: return null
        val listener = ComponentName(this, LauncherNotificationService::class.java)
        val sessions = manager.getActiveSessions(listener)
        if (sessions.isEmpty()) {
            NotificationCenter.publishToken(null)
            return null
        }

        val playing = sessions.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        }
        val paused = sessions.firstOrNull {
            val state = it.playbackState?.state
            state == PlaybackState.STATE_PAUSED || state == PlaybackState.STATE_BUFFERING
        }
        val session = playing ?: paused ?: return null

        val metadata = session.metadata
        val title = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(android.media.MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: return null
        val artist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST)

        NotificationCenter.publishToken(session.sessionToken)

        NowPlaying(
            packageName = session.packageName,
            title = title,
            artist = artist?.takeIf { it.isNotBlank() },
            isPlaying = session.playbackState?.state == PlaybackState.STATE_PLAYING,
        )
    }.getOrNull()
}
