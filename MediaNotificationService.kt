package com.example.eflplayer

import android.app.*
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.content.pm.ServiceInfo

class MediaNotificationService : Service() {
    private val binder = MediaNotificationBinder()
    private lateinit var notificationManager: NotificationManager

    // Callback'и для управления воспроизведением
    private var onPlayPauseCallback: (() -> Unit)? = null
    private var onNextCallback: (() -> Unit)? = null
    private var onPrevCallback: (() -> Unit)? = null
    private var onCloseCallback: (() -> Unit)? = null

    // ID для уведомления и канала
    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "music_player_channel"
    }

    inner class MediaNotificationBinder : Binder() {
        fun getService(): MediaNotificationService = this@MediaNotificationService
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder = binder

    fun setCallbacks(
        onPlayPause: () -> Unit,
        onNext: () -> Unit,
        onPrev: () -> Unit,
        onClose: () -> Unit
    ) {
        this.onPlayPauseCallback = onPlayPause
        this.onNextCallback = onNext
        this.onPrevCallback = onPrev
        this.onCloseCallback = onClose
    }

    fun updateNotification(
        track: Track,
        isPlaying: Boolean
    ) {
        // Создаем интент для открытия приложения
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Создаем действия для кнопок с уникальными requestCode
        val prevIntent = Intent(this, MediaNotificationReceiver::class.java).apply {
            action = "PREVIOUS"
        }
        val pendingPrevIntent = PendingIntent.getBroadcast(
            this, 1, prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, MediaNotificationReceiver::class.java).apply {
            action = if (isPlaying) "PAUSE" else "PLAY"
        }
        val pendingPlayPauseIntent = PendingIntent.getBroadcast(
            this, 2, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, MediaNotificationReceiver::class.java).apply {
            action = "NEXT"
        }
        val pendingNextIntent = PendingIntent.getBroadcast(
            this, 3, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val closeIntent = Intent(this, MediaNotificationReceiver::class.java).apply {
            action = "CLOSE"
        }
        val pendingCloseIntent = PendingIntent.getBroadcast(
            this, 4, closeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Создаем билдер уведомления
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(track.title)
            .setContentText(if (isPlaying) "Воспроизведение..." else "Пауза")
            .setContentIntent(pendingOpenIntent)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setShowWhen(false)

        // Добавляем стиль медиа-уведомления
        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
            .setMediaSession(null)

        notificationBuilder.setStyle(mediaStyle)

        // Добавляем обложку если есть с обработкой ошибок
        track.cover?.let { coverBytes ->
            try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = false
                    inSampleSize = 2 // Уменьшаем размер для уведомления
                }
                val bitmap = BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size, options)
                if (bitmap != null) {
                    notificationBuilder.setLargeIcon(bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Продолжаем без обложки
            }
        }

        // Добавляем действия
        notificationBuilder
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_skip_previous,
                    "Предыдущий",
                    pendingPrevIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                    if (isPlaying) "Пауза" else "Воспроизвести",
                    pendingPlayPauseIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_skip_next,
                    "Следующий",
                    pendingNextIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_close,
                    "Закрыть",
                    pendingCloseIntent
                ).build()
            )

        // Запускаем сервис в foreground с учетом версии Android
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notificationBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notificationBuilder.build())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopNotification() {
        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Удаляем старый канал если существует
            notificationManager.deleteNotificationChannel(CHANNEL_ID)

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Воспроизведение музыки",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Уведомления для управления воспроизведением музыки"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Обрабатываем действия из уведомления
        intent?.action?.let { action ->
            when (action) {
                "PLAY", "PAUSE" -> onPlayPauseCallback?.invoke()
                "NEXT" -> onNextCallback?.invoke()
                "PREVIOUS" -> onPrevCallback?.invoke()
                "CLOSE" -> {
                    onCloseCallback?.invoke()
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }
}