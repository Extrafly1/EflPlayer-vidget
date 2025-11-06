package com.example.eflplayer

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.detectVerticalDragGestures


enum class PlaybackMode {
    Sequential,
    RepeatOne;

    fun next(): PlaybackMode = when (this) {
        Sequential -> RepeatOne
        RepeatOne -> Sequential
    }
}

enum class PlayerSize {
    Full,
    Medium,
    Mini
}

@Composable
fun TrackItem(
    track: Track,
    onClick: () -> Unit,
    onShareClick: (Track) -> Unit
) {
    // Получаем доминантный цвет из обложки
    val dominantColor = remember(track.cover) {
        if (track.cover != null) {
            val bitmap = BitmapFactory.decodeByteArray(track.cover, 0, track.cover.size)
            extractDominantColor(bitmap) ?: Color(0xFF1E1E1E)
        } else {
            Color(0xFF1E1E1E)
        }
    }

    // Определяем контрастный цвет для текста
    val textColor = if (dominantColor.isLight()) Color.Black else Color.White

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = dominantColor)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .clickable { onClick() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (track.cover != null) {
                val bitmap = BitmapFactory.decodeByteArray(track.cover, 0, track.cover.size)
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Gray, RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Gray, RoundedCornerShape(8.dp))
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    color = textColor,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Кнопка поделиться
            IconButton(
                onClick = { onShareClick(track) }
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Поделиться",
                    tint = textColor
                )
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFFFF4081))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Загрузка музыки...", color = Color.White)
        }
    }
}

@Composable
fun MusicPlayer(
    title: String,
    artist: String,
    cover: ByteArray? = null,
    isPlaying: Boolean,
    progress: Float,
    playerSize: PlayerSize,
    dominantColor: Color,
    progressColor: Color,
    onPlayerSizeChange: (PlayerSize) -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    modifier: Modifier = Modifier,
    mediaPlayer: MediaPlayer
) {
    val contentColor = if (dominantColor.isLight()) Color.Black else Color.White
    var sliderPosition by remember { mutableStateOf(progress) }
    var playbackMode by remember { mutableStateOf(PlaybackMode.Sequential) }

    LaunchedEffect(progress) { sliderPosition = progress }

    fun formatTime(ms: Int): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms.toLong())
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms.toLong()) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    // Пульсирующая анимация обложки
    val scale = remember { Animatable(1f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                scale.animateTo(1.01f, animationSpec = tween(4400, easing = EaseInOut))
                scale.animateTo(0.99f, animationSpec = tween(4400, easing = EaseInOut))
            }
        } else {
            scale.snapTo(1f)
        }
    }

    LaunchedEffect(mediaPlayer, playbackMode) {
        mediaPlayer.setOnCompletionListener {
            when (playbackMode) {
                PlaybackMode.Sequential -> onNextClick()
                PlaybackMode.RepeatOne -> {
                    mediaPlayer.seekTo(0)
                    mediaPlayer.start()
                }
            }
        }
    }

    // ---- Исправленная логика свайпов ----
    var lastDragAmount by remember { mutableStateOf(0f) }

    val swipeableModifier = Modifier.pointerInput(playerSize) {
        detectVerticalDragGestures(
            onVerticalDrag = { _, dragAmount ->
                // накапливаем общее смещение
                lastDragAmount += dragAmount
            },
            onDragEnd = {
                // анализ направления после отпускания пальца
                when {
                    lastDragAmount > 60f -> { // свайп вниз
                        when (playerSize) {
                            PlayerSize.Full -> onPlayerSizeChange(PlayerSize.Medium)
                            PlayerSize.Medium -> onPlayerSizeChange(PlayerSize.Mini)
                            PlayerSize.Mini -> {}
                        }
                    }

                    lastDragAmount < -60f -> { // свайп вверх
                        when (playerSize) {
                            PlayerSize.Mini -> onPlayerSizeChange(PlayerSize.Medium)
                            PlayerSize.Medium -> onPlayerSizeChange(PlayerSize.Full)
                            PlayerSize.Full -> {}
                        }
                    }
                }
                lastDragAmount = 0f
            },
            onDragCancel = { lastDragAmount = 0f }
        )
    }

    // ---- UI ----
    Card(
        modifier = modifier
            .then(swipeableModifier)
            .animateContentSize() // плавная анимация изменения размера
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = dominantColor)
    ) {
        when (playerSize) {
            PlayerSize.Full -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = { playbackMode = playbackMode.next() }) {
                            Icon(
                                imageVector = when (playbackMode) {
                                    PlaybackMode.Sequential -> Icons.Default.Repeat
                                    PlaybackMode.RepeatOne -> Icons.Default.RepeatOne
                                },
                                contentDescription = "Playback Mode",
                                tint = contentColor
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (cover != null) {
                            val bitmap = BitmapFactory.decodeByteArray(cover, 0, cover.size)
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Cover",
                                modifier = Modifier
                                    .size(300.dp)
                                    .scale(scale.value)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(300.dp)
                                    .background(Color.Gray, RoundedCornerShape(16.dp))
                                    .scale(scale.value)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(title, color = contentColor, style = MaterialTheme.typography.titleLarge)
                        Text(
                            artist,
                            color = contentColor.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Slider(
                            value = sliderPosition,
                            onValueChange = { sliderPosition = it },
                            onValueChangeFinished = {
                                val newPosition = (mediaPlayer.duration * sliderPosition).toInt()
                                mediaPlayer.seekTo(newPosition)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = progressColor,
                                activeTrackColor = progressColor,
                                inactiveTrackColor = Color.DarkGray.copy(alpha = 0.3f)
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                formatTime(mediaPlayer.currentPosition),
                                color = contentColor,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                formatTime(mediaPlayer.duration),
                                color = contentColor,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onPrevClick) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = contentColor)
                            }
                            IconButton(onClick = onPlayPauseClick) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = contentColor,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            IconButton(onClick = onNextClick) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = contentColor)
                            }
                        }
                    }
                }
            }

            PlayerSize.Medium -> {
                Column(
                    modifier = Modifier
                        .wrapContentHeight()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = { playbackMode = playbackMode.next() }) {
                            Icon(
                                imageVector = when (playbackMode) {
                                    PlaybackMode.Sequential -> Icons.Default.Repeat
                                    PlaybackMode.RepeatOne -> Icons.Default.RepeatOne
                                },
                                contentDescription = "Playback Mode",
                                tint = contentColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (cover != null) {
                        val bitmap = BitmapFactory.decodeByteArray(cover, 0, cover.size)
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Cover",
                            modifier = Modifier
                                .size(200.dp)
                                .scale(scale.value)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .background(Color.Gray, RoundedCornerShape(16.dp))
                                .scale(scale.value)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(title, color = contentColor, style = MaterialTheme.typography.titleLarge)
                    Text(artist, color = contentColor.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))

                    Slider(
                        value = sliderPosition,
                        onValueChange = { sliderPosition = it },
                        onValueChangeFinished = {
                            val newPosition = (mediaPlayer.duration * sliderPosition).toInt()
                            mediaPlayer.seekTo(newPosition)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = progressColor,
                            activeTrackColor = progressColor,
                            inactiveTrackColor = Color.DarkGray.copy(alpha = 0.3f)
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(mediaPlayer.currentPosition), color = contentColor, style = MaterialTheme.typography.bodySmall)
                        Text(formatTime(mediaPlayer.duration), color = contentColor, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onPrevClick) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = contentColor)
                        }
                        IconButton(onClick = onPlayPauseClick) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = contentColor,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        IconButton(onClick = onNextClick) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = contentColor)
                        }
                    }
                }
            }

            PlayerSize.Mini -> {
                Column(
                    modifier = Modifier
                        .wrapContentHeight()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = { playbackMode = playbackMode.next() }) {
                            Icon(
                                imageVector = when (playbackMode) {
                                    PlaybackMode.Sequential -> Icons.Default.Repeat
                                    PlaybackMode.RepeatOne -> Icons.Default.RepeatOne
                                },
                                contentDescription = "Playback Mode",
                                tint = contentColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Slider(
                        value = sliderPosition,
                        onValueChange = { sliderPosition = it },
                        onValueChangeFinished = {
                            val newPosition = (mediaPlayer.duration * sliderPosition).toInt()
                            mediaPlayer.seekTo(newPosition)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = progressColor,
                            activeTrackColor = progressColor,
                            inactiveTrackColor = Color.DarkGray.copy(alpha = 0.3f)
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(mediaPlayer.currentPosition), color = contentColor, style = MaterialTheme.typography.bodySmall)
                        Text(formatTime(mediaPlayer.duration), color = contentColor, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onPrevClick) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = contentColor)
                        }
                        IconButton(onClick = onPlayPauseClick) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = contentColor,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        IconButton(onClick = onNextClick) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = contentColor)
                        }
                    }
                }
            }
        }
    }
}
