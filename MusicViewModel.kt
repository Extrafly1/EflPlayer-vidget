package com.example.eflplayer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.MediaPlayer
import android.os.IBinder
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MusicViewModel : ViewModel() {

    var tracks = mutableStateListOf<Track>()
        private set

    var currentIndex by mutableStateOf(-1)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var progress by mutableStateOf(0f)
        private set

    var isLoading by mutableStateOf(true)
        private set

    var mediaPlayer: MediaPlayer? = null

    private var notificationService: MediaNotificationService? = null
    private var isBound = false
    private var context: Context? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MediaNotificationService.MediaNotificationBinder
            notificationService = binder.getService()
            isBound = true

            // Устанавливаем callback'и для сервиса
            notificationService?.setCallbacks(
                onPlayPause = { togglePlayPause() },
                onNext = { nextTrack() },
                onPrev = { prevTrack() },
                onClose = { stopNotificationService() }
            )

            updateNotification()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            notificationService = null
            isBound = false
        }
    }

    fun setContext(context: Context) {
        this.context = context
    }

    private fun startNotificationService() {
        val ctx = context ?: return
        try {
            val intent = Intent(ctx, MediaNotificationService::class.java)
            ContextCompat.startForegroundService(ctx, intent)
            ctx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopNotificationService() {
        val ctx = context ?: return
        try {
            if (isBound) {
                ctx.unbindService(serviceConnection)
                isBound = false
            }
            val intent = Intent(ctx, MediaNotificationService::class.java)
            ctx.stopService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateNotification() {
        if (currentIndex in tracks.indices && isBound) {
            val track = tracks[currentIndex]
            notificationService?.updateNotification(
                track = track,
                isPlaying = isPlaying
            )
        }
    }

    fun loadTracksAsync(rootDir: File) {
        isLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            val scannedTracks = scanAudioFiles(rootDir)
            tracks.clear()
            tracks.addAll(scannedTracks)
            isLoading = false
        }
    }

    fun playTrack(index: Int) {
        if (index !in tracks.indices) return

        try {
            currentIndex = index
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(tracks[index].path)
                prepare()
                start()
            }
            isPlaying = true

            // Запускаем сервис уведомлений
            startNotificationService()
            updateNotification()
            updateProgress()
        } catch (e: Exception) {
            e.printStackTrace()
            isPlaying = false
        }
    }

    fun togglePlayPause() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                } else {
                    player.start()
                }
                isPlaying = player.isPlaying
                updateNotification()
                updateProgress()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun nextTrack() {
        if (tracks.isEmpty()) return
        val nextIndex = (currentIndex + 1) % tracks.size
        playTrack(nextIndex)
    }

    fun prevTrack() {
        if (tracks.isEmpty()) return
        val prevIndex = if (currentIndex - 1 < 0) tracks.size - 1 else currentIndex - 1
        playTrack(prevIndex)
    }

    private fun updateProgress() {
        viewModelScope.launch {
            while (isPlaying) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        progress = player.currentPosition.toFloat() / (player.duration.takeIf { it > 0 } ?: 1)
                    }
                }
                delay(500)
            }
        }
    }

    private fun scanAudioFiles(dir: File): List<Track> {
        val tracks = mutableListOf<Track>()

        // список путей, которые нужно пропустить
        val excludedDirs = listOf(
            "/Android", "/DCIM/.thumbnails", "/system", "/data", "/cache",
            "/proc", "/dev", "/acct", "/vendor", "/sys"
        )

        try {
            dir.listFiles()?.forEach { file ->
                try {
                    // пропускаем скрытые и системные каталоги
                    if (file.isDirectory) {
                        val path = file.absolutePath
                        if (excludedDirs.any { path.startsWith(it) || path.contains(it) } || file.name.startsWith(".")) {
                            return@forEach
                        }
                        tracks.addAll(scanAudioFiles(file))
                    } else if (file.extension.lowercase() in listOf("mp3", "wav", "m4a", "flac")) {
                        if (file.length() > 10 * 1024) {
                            val cover = try {
                                val mmr = android.media.MediaMetadataRetriever()
                                mmr.setDataSource(file.absolutePath)
                                mmr.embeddedPicture
                            } catch (e: Exception) {
                                null
                            }
                            tracks.add(Track(file.nameWithoutExtension, file.absolutePath, cover))
                        }
                    }
                } catch (e: Exception) {
                    // Игнорируем ошибки для отдельных файлов
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return tracks
    }

    override fun onCleared() {
        super.onCleared()
        stopNotificationService()
        mediaPlayer?.release()
    }
}