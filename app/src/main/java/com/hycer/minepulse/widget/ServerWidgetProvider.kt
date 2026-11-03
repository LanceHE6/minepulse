package com.hycer.minepulse.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.hycer.minepulse.MainActivity
import com.hycer.minepulse.R
import com.hycer.minepulse.data.ServerRepository
import com.hycer.minepulse.model.ServerItem
import com.hycer.minepulse.network.MinecraftPing
import com.hycer.minepulse.network.MinecraftRconClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == AppWidgetManager.ACTION_APPWIDGET_UPDATE || action == Intent.ACTION_BOOT_COMPLETED) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, ServerWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (appWidgetIds.isNotEmpty()) {
                onUpdate(context, appWidgetManager, appWidgetIds)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val repo = ServerRepository(context)
        val servers = repo.loadServers()
        if (servers.isEmpty()) return

        val prefs = context.getSharedPreferences("minepulse_widget_prefs", Context.MODE_PRIVATE)
        val pinnedId = prefs.getString("pinned_server_id", null)
        val targetServer = servers.find { it.id == pinnedId } ?: servers.first()

        CoroutineScope(Dispatchers.IO).launch {
            updateWidgetContent(context, appWidgetManager, appWidgetIds, targetServer)
        }
    }

    companion object {
        fun hasShortcutPermission(context: Context): Boolean {
            // On HyperOS / MIUI, check AppOps for OP_INSTALL_SHORTCUT (op code 10017 or name)
            try {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
                if (appOps != null) {
                    val method = appOps.javaClass.getMethod(
                        "checkOpNoThrow",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        String::class.java
                    )
                    // 10017 is standard Xiaomi/HyperOS op for install shortcut
                    val result = method.invoke(appOps, 10017, android.os.Process.myUid(), context.packageName) as? Int
                    if (result != null) {
                        return result == android.app.AppOpsManager.MODE_ALLOWED
                    }
                }
            } catch (_: Exception) {}
            return true
        }

        fun pinWidget(context: Context, server: ServerItem, onPermissionDenied: () -> Unit) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val myProvider = ComponentName(context, ServerWidgetProvider::class.java)

            // Save the server ID so the widget knows which server to bind to
            val prefs = context.getSharedPreferences("minepulse_widget_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("pinned_server_id", server.id).apply()

            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                try {
                    val success = appWidgetManager.requestPinAppWidget(myProvider, null, null)
                    if (success) {
                        android.widget.Toast.makeText(context, "已发起添加请求，请在桌面弹窗中确认", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        onPermissionDenied()
                    }
                } catch (e: Exception) {
                    onPermissionDenied()
                }
            } else {
                onPermissionDenied()
            }
        }

        suspend fun updateWidgetContent(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            server: ServerItem
        ) = withContext(Dispatchers.IO) {
            val status = MinecraftPing.ping(server.host, server.pingPort)

            var timeDisplay = "--"
            var tpsDisplay = "--"
            var msptDisplay = "--"

            if (server.rconPassword.isNotBlank()) {
                val rcon = MinecraftRconClient(server.host, server.rconPort, server.rconPassword)
                val gametimeRes = rcon.executeCommand("time query gametime")
                gametimeRes.onSuccess { output ->
                    val match = Regex("([0-9]+)").find(output)
                    if (match != null) {
                        val ticks = match.groupValues[1].toLongOrNull() ?: -1L
                        if (ticks >= 0) {
                            val days = ticks / 24000
                            timeDisplay = "第${days}天"
                        }
                    }
                }

                val tpsRes = rcon.executeCommand("tick query")
                tpsRes.onSuccess { output ->
                    val tpsRegex = Regex("(?i)(?:Target tick rate:|TPS:?)\\s*([0-9.]+)")
                    val msptRegex = Regex("(?i)(?:Average time per tick:|MSPT:?)\\s*([0-9.]+)ms?")
                    val tMatch = tpsRegex.find(output)
                    val mMatch = msptRegex.find(output)
                    if (tMatch != null) {
                        tpsDisplay = tMatch.groupValues[1]
                    }
                    if (mMatch != null) {
                        msptDisplay = "${mMatch.groupValues[1]}ms"
                    }
                }
                rcon.close()
            }

            for (widgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_server_status)

                views.setTextViewText(R.id.widget_server_name, server.name)
                views.setTextViewText(R.id.widget_server_version, "版本: ${if (status.isOnline) status.version else "未知"}")

                // Server Favicon
                if (status.iconBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_icon, status.iconBitmap)
                } else {
                    views.setImageViewResource(R.id.widget_icon, R.mipmap.ic_launcher)
                }

                if (status.isOnline) {
                    views.setTextViewText(R.id.widget_status_text, "在线")
                    views.setTextColor(R.id.widget_status_text, 0xFF4CAF50.toInt())
                    views.setTextViewText(R.id.widget_ping_text, "${status.pingMs} ms")
                    views.setTextViewText(R.id.widget_players_val, "${status.onlinePlayers} / ${status.maxPlayers}")
                    views.setTextViewText(R.id.widget_tps_val, tpsDisplay)
                    views.setTextViewText(R.id.widget_mspt_val, msptDisplay)
                } else {
                    views.setTextViewText(R.id.widget_status_text, "离线")
                    views.setTextColor(R.id.widget_status_text, 0xFFEF5350.toInt())
                    views.setTextViewText(R.id.widget_ping_text, "-- ms")
                    views.setTextViewText(R.id.widget_players_val, "-- / --")
                    views.setTextViewText(R.id.widget_tps_val, "--")
                    views.setTextViewText(R.id.widget_mspt_val, "--")
                }

                views.setTextViewText(R.id.widget_time_val, timeDisplay)

                val intent = Intent(context, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}
