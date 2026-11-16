package com.floatplay.overlay

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent

object MediaToggle {
    private const val TAG = "FloatPlay"

    fun listenerComponent(context: Context): ComponentName {
        return ComponentName(context, MediaNotificationListener::class.java)
    }

    fun sessionManager(context: Context): MediaSessionManager? {
        return context.getSystemService(MediaSessionManager::class.java)
    }

    fun activeSessions(context: Context): List<MediaController> {
        if (!context.listenerEnabled()) return emptyList()
        val manager = sessionManager(context) ?: return emptyList()
        return try {
            manager.getActiveSessions(listenerComponent(context))
        } catch (error: SecurityException) {
            Log.w(TAG, "Media sessions unavailable until notification access is allowed", error)
            emptyList()
        }
    }

    fun isPlaying(context: Context): Boolean {
        val sessions = activeSessions(context)
        if (sessions.isNotEmpty()) return sessions.any { it.isActivePlayback() }
        // Media-key control also works without session access. Reflect actual music
        // output instead of leaving the button permanently on the play icon.
        return context.getSystemService(AudioManager::class.java)?.isMusicActive == true
    }

    /**
     * Pauses the session that is playing, or resumes the highest-priority session.
     * A media-key fallback covers players that do not publish a session.
     */
    fun toggle(context: Context): Boolean {
        val sessions = activeSessions(context)
        val playing = sessions.firstOrNull { it.canPause() }
        if (playing != null) {
            Log.i(TAG, "Pause ${playing.packageName}")
            playing.transportControls.pause()
            return true
        }
        val resumable = sessions.firstOrNull { it.canResume() }
        if (resumable != null) {
            Log.i(TAG, "Play ${resumable.packageName}")
            resumable.transportControls.play()
            return true
        }
        Log.i(TAG, "No media session; sending play/pause key")
        return dispatchPlayPauseKey(context)
    }

    private fun dispatchPlayPauseKey(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        val now = SystemClock.uptimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0)
        val up = KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0)
        return try {
            audio.dispatchMediaKeyEvent(down)
            audio.dispatchMediaKeyEvent(up)
            true
        } catch (error: RuntimeException) {
            Log.w(TAG, "Media key was not delivered", error)
            false
        }
    }
}

private fun MediaController.isActivePlayback(): Boolean = canPause()

private fun MediaController.canPause(): Boolean {
    val state = playbackState?.state ?: return false
    return state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
}

private fun MediaController.canResume(): Boolean {
    val state = playbackState?.state ?: return false
    return state == PlaybackState.STATE_PAUSED || state == PlaybackState.STATE_STOPPED
}
