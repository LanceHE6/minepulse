package com.hycer.minepulse.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.hycer.minepulse.data.ServerRepository
import com.hycer.minepulse.network.MinecraftRconClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BackgroundMonitorReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                pollAllServers(context)
            } finally {
                // Schedule next alarm in 60 seconds (1 minute interval)
                scheduleNextPoll(context, 60_000L)
                pendingResult.finish()
            }
        }
    }

    private suspend fun pollAllServers(context: Context) {
        val repo = ServerRepository(context)
        val servers = repo.loadServers()
        if (servers.isEmpty()) return

        val prefs = context.getSharedPreferences("minepulse_bg_monitor_prefs", Context.MODE_PRIVATE)

        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
        val thisWidget = android.content.ComponentName(context, com.hycer.minepulse.widget.ServerWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        val widgetPinnedId = prefs.getString("pinned_server_id", null)

        for (server in servers) {
            // If this server is pinned to widget, update widget content
            if (appWidgetIds.isNotEmpty() && (server.id == widgetPinnedId || (widgetPinnedId == null && server == servers.first()))) {
                try {
                    com.hycer.minepulse.widget.ServerWidgetProvider.updateWidgetContent(
                        context,
                        appWidgetManager,
                        appWidgetIds,
                        server
                    )
                } catch (_: Exception) {}
            }

            if (!server.notifyPlayerEvents || server.rconPassword.isBlank()) continue

            try {
                val rcon = MinecraftRconClient(server.host, server.rconPort, server.rconPassword)
                val listResult = rcon.executeCommand("list")
                rcon.close()

                listResult.onSuccess { output ->
                    val colonIndex = output.indexOf(":")
                    val activeNames = mutableSetOf<String>()
                    if (colonIndex != -1) {
                        val listPart = output.substring(colonIndex + 1).trim()
                        if (listPart.isNotEmpty()) {
                            activeNames.addAll(
                                listPart.split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                            )
                        }
                    }

                    val key = "last_online_players_${server.id}"
                    val previous = prefs.getStringSet(key, null)

                    if (previous != null) {
                        val joined = activeNames - previous
                        val left = previous - activeNames

                        for (p in joined) {
                            NotificationHelper.sendPlayerEventNotification(
                                context = context,
                                serverName = server.name,
                                playerName = p,
                                isJoin = true
                            )
                        }

                        for (p in left) {
                            NotificationHelper.sendPlayerEventNotification(
                                context = context,
                                serverName = server.name,
                                playerName = p,
                                isJoin = false
                            )
                        }
                    }

                    prefs.edit().putStringSet(key, activeNames).apply()
                }
            } catch (_: Exception) {
                // Network error or server offline
            }
        }
    }

    companion object {
        private const val REQUEST_CODE = 8888

        fun startMonitoring(context: Context) {
            scheduleNextPoll(context, 5_000L)
        }

        fun scheduleNextPoll(context: Context, delayMillis: Long) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, BackgroundMonitorReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAtMillis = SystemClock.elapsedRealtime() + delayMillis

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } catch (_: SecurityException) {
                // If exact alarm permission is missing on Android 12+, fallback to windowed alarm
                alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        }
    }
}
