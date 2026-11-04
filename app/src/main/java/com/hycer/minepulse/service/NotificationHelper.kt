package com.hycer.minepulse.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hycer.minepulse.MainActivity
import com.hycer.minepulse.R

object NotificationHelper {
    const val CHANNEL_ID = "server_player_events"
    private const val CHANNEL_NAME = "玩家上下线通知"
    private const val CHANNEL_DESC = "Minecraft 服务器玩家上线或下线事件提醒"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun sendPlayerEventNotification(
        context: Context,
        serverName: String,
        playerName: String,
        isJoin: Boolean
    ) {
        createNotificationChannel(context)

        val title = if (isJoin) "🟢 玩家上线提醒" else "⚪ 玩家下线提醒"
        val message = if (isJoin) {
            "玩家【$playerName】加入了服务器 [$serverName]"
        } else {
            "玩家【$playerName】离开了服务器 [$serverName]"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                (serverName + playerName).hashCode(),
                notification
            )
        } catch (_: SecurityException) {
            // Permission denied or revoked
        }
    }
}
