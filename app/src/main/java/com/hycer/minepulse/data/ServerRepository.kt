package com.hycer.minepulse.data

import android.content.Context
import android.content.SharedPreferences
import com.hycer.minepulse.model.QuickMacro
import com.hycer.minepulse.model.ServerItem
import org.json.JSONArray
import org.json.JSONObject

class ServerRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("minepulse_prefs", Context.MODE_PRIVATE)

    fun loadServers(): List<ServerItem> {
        val jsonStr = prefs.getString("servers_list", null)
        if (jsonStr.isNullOrEmpty()) {
            return emptyList()
        }

        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<ServerItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ServerItem(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        host = obj.getString("host"),
                        pingPort = obj.getInt("pingPort"),
                        rconPort = obj.getInt("rconPort"),
                        rconPassword = obj.optString("rconPassword", ""),
                        customIconBase64 = obj.optString("customIconBase64", null)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveServers(servers: List<ServerItem>) {
        val array = JSONArray()
        for (s in servers) {
            val obj = JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("host", s.host)
                put("pingPort", s.pingPort)
                put("rconPort", s.rconPort)
                put("rconPassword", s.rconPassword)
                if (s.customIconBase64 != null) {
                    put("customIconBase64", s.customIconBase64)
                }
            }
            array.put(obj)
        }
        prefs.edit().putString("servers_list", array.toString()).apply()
    }

    fun loadHistoricalPlayers(serverId: String): Set<String> {
        val key = "historical_players_$serverId"
        val json = prefs.getString(key, null) ?: return emptySet()
        return try {
            val arr = JSONArray(json)
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                set.add(arr.getString(i))
            }
            set
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun saveHistoricalPlayers(serverId: String, players: Set<String>) {
        val key = "historical_players_$serverId"
        val arr = JSONArray()
        players.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    fun loadMacros(): List<QuickMacro> {
        val jsonStr = prefs.getString("macros_list", null)
        val defaultList = listOf(
            QuickMacro(label = "查询在线", command = "list", iconName = "people"),
            QuickMacro(label = "保存世界", command = "save-all", iconName = "save")
        )
        if (jsonStr.isNullOrEmpty()) {
            saveMacros(defaultList)
            return defaultList
        }

        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<QuickMacro>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val cmd = obj.getString("command")
                // Filter out legacy unwanted commands if they were saved in previous runs
                if (cmd.contains("tick health") || cmd.contains("time set") || cmd.contains("weather") || cmd.contains("difficulty")) {
                    continue
                }
                list.add(
                    QuickMacro(
                        id = obj.getString("id"),
                        label = obj.getString("label"),
                        command = cmd,
                        iconName = obj.optString("iconName", "bolt")
                    )
                )
            }
            if (list.isEmpty()) {
                saveMacros(defaultList)
                defaultList
            } else {
                list
            }
        } catch (_: Exception) {
            defaultList
        }
    }

    fun saveMacros(macros: List<QuickMacro>) {
        val array = JSONArray()
        for (m in macros) {
            val obj = JSONObject().apply {
                put("id", m.id)
                put("label", m.label)
                put("command", m.command)
                put("iconName", m.iconName)
            }
            array.put(obj)
        }
        prefs.edit().putString("macros_list", array.toString()).apply()
    }
}
