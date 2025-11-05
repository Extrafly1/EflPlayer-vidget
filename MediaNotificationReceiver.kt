package com.example.eflplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class MediaNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        // Создаем интент для сервиса уведомлений
        val serviceIntent = Intent(context, MediaNotificationService::class.java)
        serviceIntent.action = action

        // Запускаем сервис
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}