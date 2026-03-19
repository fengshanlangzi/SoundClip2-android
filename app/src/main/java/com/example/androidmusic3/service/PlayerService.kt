package com.example.androidmusic3.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.session.MediaSession
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.androidmusic3.ui.MainActivity
import com.example.androidmusic3.MediaManager
import com.example.androidmusic3.ui.PlayerActivity
import com.example.androidmusic3.R

class PlayerService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "player_channel"
        private const val CHANNEL_NAME = "Music Player"

        const val ACTION_PLAY_PAUSE = "com.example.androidmusic3.PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.example.androidmusic3.PREVIOUS"
        const val ACTION_NEXT = "com.example.androidmusic3.NEXT"
        const val ACTION_STOP = "com.example.androidmusic3.STOP"
    }

    private val binder = LocalBinder()
        private var mediaManager: MediaManager? = null
        private lateinit var mediaSession: MediaSession
    private var notificationManager: NotificationManager? = null

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PLAY_PAUSE -> mediaManager?.togglePlayPause()
                ACTION_PREVIOUS -> mediaManager?.seekToPrevious()
                ACTION_NEXT -> mediaManager?.seekToNext()
                ACTION_STOP -> stopSelf()
            }
            updateNotification()
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): PlayerService = this@PlayerService
    }

    override fun onCreate() {
        super.onCreate()
        mediaManager = MediaManager.getInstance(this)
        mediaSession = MediaSession(this, "PlayerService")
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel()
        registerControlsReceiver()

        mediaSession.setActive(true)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music player controls"
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun registerControlsReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_PREVIOUS)
            addAction(ACTION_NEXT)
            addAction(ACTION_STOP)
        }
        registerReceiver(controlReceiver, filter)
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start foreground service immediately
        startForeground(NOTIFICATION_ID, createNotification())

        when (intent?.action) {
            ACTION_PLAY_PAUSE -> mediaManager?.togglePlayPause()
            ACTION_PREVIOUS -> mediaManager?.seekToPrevious()
            ACTION_NEXT -> mediaManager?.seekToNext()
        }
        updateNotification()
        return START_STICKY
    }

    fun startForegroundWithNotification() {
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun updateNotification() {
        notificationManager?.notify(NOTIFICATION_ID, createNotification())
    }

    private fun createNotification(): Notification {
        val currentFile = mediaManager?.currentAudioFile?.value
        val isPlaying = mediaManager?.playbackState?.value?.isPlaying ?: false

        val title = currentFile?.title ?: "No track"
        val artist = currentFile?.artist ?: "Unknown"

        // Main intent - opens the app
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingMainIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Play/Pause intent
        val playPauseIntent = Intent(ACTION_PLAY_PAUSE).apply {
            setPackage(packageName)
        }
        val pendingPlayPause = PendingIntent.getBroadcast(
            this, 1, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Previous intent
        val prevIntent = Intent(ACTION_PREVIOUS).apply {
            setPackage(packageName)
        }
        val pendingPrev = PendingIntent.getBroadcast(
            this, 2, prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Next intent
        val nextIntent = Intent(ACTION_NEXT).apply {
            setPackage(packageName)
        }
        val pendingNext = PendingIntent.getBroadcast(
            this, 3, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop intent
        val stopIntent = Intent(ACTION_STOP).apply {
            setPackage(packageName)
        }
        val pendingStop = PendingIntent.getBroadcast(
            this, 4, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseContentDescription = if (isPlaying) "Pause" else "Play"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(pendingMainIntent)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_media_previous,
                "Previous",
                pendingPrev
            )
            .addAction(
                playPauseIcon,
                playPauseContentDescription,
                pendingPlayPause
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "Next",
                pendingNext
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            notification.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return notification.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(controlReceiver)
        mediaSession.release()
        mediaManager?.release()
    }
}
