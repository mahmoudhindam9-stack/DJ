package com.example.player

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.example.MainActivity
import com.example.widget.MusicWidgetProvider
import android.appwidget.AppWidgetManager

class MusicService : Service() {
    companion object {
        const val CHANNEL_ID = "music_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_TOGGLE_PLAY = "com.example.action.TOGGLE_PLAY"
        const val ACTION_NEXT = "com.example.action.NEXT"
        const val ACTION_PREV = "com.example.action.PREV"
        const val ACTION_STOP = "com.example.action.STOP"
        const val ACTION_EQ_BASS = "com.example.action.EQ_BASS"
        const val ACTION_EQ_MID = "com.example.action.EQ_MID"
        const val ACTION_EQ_TREBLE = "com.example.action.EQ_TREBLE"
        const val ACTION_MIC_START = "com.example.action.MIC_START"
        const val ACTION_MIC_STOP = "com.example.action.MIC_STOP"
        const val MIC_NOTIFICATION_ID = 1002
        var instance: MusicService? = null
            private set
    }

    var playerController: AudioPlayerController? = null
    private lateinit var mediaSession: MediaSessionCompat
    private var micActive = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        playerController = AudioPlayerController.activeInstance ?: AudioPlayerController(applicationContext)
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "DJMusicSession").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { playerController?.togglePlayPause() }
                override fun onPause() { playerController?.pause() }
                override fun onSkipToNext() { playerController?.playNext() }
                override fun onSkipToPrevious() { playerController?.playPrevious() }
                override fun onStop() { playerController?.pause(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            })
            setActive(true)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Intent.ACTION_MEDIA_BUTTON -> { MediaButtonReceiver.handleIntent(mediaSession, intent); return START_STICKY }
            ACTION_EQ_BASS -> { EqualizerController.adjustQuickBand(this, 0); refreshWidgetFromStoredState() }
            ACTION_EQ_MID -> { EqualizerController.adjustQuickBand(this, 1); refreshWidgetFromStoredState() }
            ACTION_EQ_TREBLE -> { EqualizerController.adjustQuickBand(this, 2); refreshWidgetFromStoredState() }
            ACTION_TOGGLE_PLAY -> { ensureController(); playerController?.togglePlayPause(); syncFromController() }
            ACTION_NEXT -> { ensureController(); playerController?.playNext(); syncFromController() }
            ACTION_PREV -> { ensureController(); playerController?.playPrevious(); syncFromController() }
            ACTION_STOP -> { playerController?.pause(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            ACTION_MIC_START -> { micActive = true; if (playerController?.isPlaying != true) updateMicNotification() }
            ACTION_MIC_STOP -> { micActive = false; if (playerController?.isPlaying != true) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() } }
            null -> if (playerController?.isPlaying == true) syncFromController()
        }
        return START_STICKY
    }

    private fun ensureController() {
        if (playerController == null) playerController = AudioPlayerController.activeInstance ?: AudioPlayerController(applicationContext)
    }

    private fun syncFromController() {
        val controller = playerController ?: return
        updateNotification(controller.currentSong?.title ?: "مشغل الموسيقى", controller.currentSong?.artist ?: "موسيقى", controller.isPlaying)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateMicNotification() {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 99, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DJ Microphone")
            .setContentText("Live microphone monitor is running")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pending)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        startForeground(MIC_NOTIFICATION_ID, notification)
    }

    fun updateNotification(title: String, artist: String, isPlaying: Boolean) {
        if (micActive && !isPlaying) { updateMicNotification(); return }
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        mediaSession.isActive = true
        mediaSession.setMetadata(MediaMetadataCompat.Builder().putString(MediaMetadataCompat.METADATA_KEY_TITLE, title).putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist).build())
        mediaSession.setPlaybackState(PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_STOP)
            .setState(if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, playerController?.currentPositionMs ?: 0L, if (isPlaying) 1f else 0f).build())
        val playPauseAction = NotificationCompat.Action(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (isPlaying) "Pause" else "Play", PendingIntent.getService(this, 1, Intent(this, MusicService::class.java).setAction(ACTION_TOGGLE_PLAY), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        val prevAction = NotificationCompat.Action(android.R.drawable.ic_media_previous, "Previous", PendingIntent.getService(this, 2, Intent(this, MusicService::class.java).setAction(ACTION_PREV), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        val nextAction = NotificationCompat.Action(android.R.drawable.ic_media_next, "Next", PendingIntent.getService(this, 3, Intent(this, MusicService::class.java).setAction(ACTION_NEXT), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title).setContentText(artist).setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setOngoing(isPlaying)
            .addAction(prevAction).addAction(playPauseAction).addAction(nextAction)
            .setStyle(MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0, 1, 2)).build()
        startForeground(NOTIFICATION_ID, notification)
        updateWidget(title, artist, isPlaying)
    }

    private fun updateWidget(title: String, artist: String, isPlaying: Boolean) {
        val widgetManager = AppWidgetManager.getInstance(this)
        val widgetComponent = ComponentName(this, MusicWidgetProvider::class.java)
        widgetManager.getAppWidgetIds(widgetComponent).forEach { id -> MusicWidgetProvider.updateAppWidget(this, widgetManager, id, title, artist, isPlaying) }
    }

    private fun refreshWidgetFromStoredState() {
        val prefs = getSharedPreferences("dj_player_session", MODE_PRIVATE)
        updateWidget(prefs.getString("title", "مشغل الموسيقى") ?: "مشغل الموسيقى", prefs.getString("artist", "موسيقى") ?: "موسيقى", prefs.getBoolean("playing", false))
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Music Playback Channel", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
        instance = null
    }
}
