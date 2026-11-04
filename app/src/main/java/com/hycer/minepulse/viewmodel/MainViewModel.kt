package com.hycer.minepulse.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hycer.minepulse.data.ServerRepository
import com.hycer.minepulse.model.*
import com.hycer.minepulse.network.MinecraftPing
import com.hycer.minepulse.network.MinecraftRconClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ServerRepository(application)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _serverList = MutableStateFlow<List<ServerItem>>(emptyList())
    val serverList: StateFlow<List<ServerItem>> = _serverList.asStateFlow()

    private val _serverStatusMap = MutableStateFlow<Map<String, ServerRuntimeState>>(emptyMap())
    val serverStatusMap: StateFlow<Map<String, ServerRuntimeState>> = _serverStatusMap.asStateFlow()

    private val _activeServerId = MutableStateFlow<String?>(null)
    val activeServerId: StateFlow<String?> = _activeServerId.asStateFlow()

    private val _macroList = MutableStateFlow<List<QuickMacro>>(emptyList())
    val macroList: StateFlow<List<QuickMacro>> = _macroList.asStateFlow()

    private var activeRconClient: MinecraftRconClient? = null
    private val previousOnlinePlayerMap = java.util.concurrent.ConcurrentHashMap<String, Set<String>>()

    init {
        loadData()
    }

    private fun loadData() {
        val servers = repository.loadServers()
        _serverList.value = servers
        _macroList.value = repository.loadMacros()

        val map = mutableMapOf<String, ServerRuntimeState>()
        for (s in servers) {
            map[s.id] = ServerRuntimeState(server = s)
        }
        _serverStatusMap.value = map

        refreshAllServers()
        startAutoRefreshLoop()
    }

    private var autoRefreshJob: kotlinx.coroutines.Job? = null
    private fun startAutoRefreshLoop() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5000) // Auto-refresh every 5s
                if (_activeServerId.value != null) {
                    silentRefreshActiveServer()
                } else {
                    silentRefreshAllServers()
                }
            }
        }
    }

    private fun silentRefreshAllServers() {
        for (server in _serverList.value) {
            viewModelScope.launch {
                val status = MinecraftPing.ping(server.host, server.pingPort)
                updateServerState(server.id) { current ->
                    current.copy(status = status)
                }
            }
        }
    }

    private fun silentRefreshActiveServer() {
        val id = _activeServerId.value ?: return
        val current = _serverStatusMap.value[id] ?: return
        viewModelScope.launch {
            val status = MinecraftPing.ping(current.server.host, current.server.pingPort)
            updateServerState(id) { it.copy(status = status) }
            refreshTps()
            refreshGameTime()
            executeRconCommand("list", silent = true)
        }
    }

    fun selectServer(server: ServerItem) {
        _activeServerId.value = server.id
        activeRconClient?.close()
        val client = MinecraftRconClient(
            host = server.host,
            port = server.rconPort,
            password = server.rconPassword
        )
        activeRconClient = client
        refreshActiveServerStatus()

        // Actively connect RCON and load initial data with full screen loading indicator
        updateServerState(server.id) { it.copy(isRconConnecting = true) }
        viewModelScope.launch {
            val res = client.connect()
            if (res.isSuccess) {
                updateServerState(server.id) { it.copy(isRconConnecting = false, isRconConnected = true) }
                refreshTps()
                refreshGameTime()
                executeRconCommand("list", silent = true)
            } else {
                updateServerState(server.id) { it.copy(isRconConnecting = false, isRconConnected = false) }
                appendLog(server.id, LogType.ERROR, "RCON 连接失败: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun clearActiveServer() {
        _activeServerId.value = null
        activeRconClient?.close()
        activeRconClient = null
    }

    fun addServer(name: String, host: String, pingPort: Int, rconPort: Int, rconPass: String) {
        val newServer = ServerItem(
            name = name,
            host = host,
            pingPort = pingPort,
            rconPort = rconPort,
            rconPassword = rconPass
        )
        val updated = _serverList.value + newServer
        _serverList.value = updated
        repository.saveServers(updated)

        val map = _serverStatusMap.value.toMutableMap()
        map[newServer.id] = ServerRuntimeState(server = newServer)
        _serverStatusMap.value = map

        pingServer(newServer)
    }

    fun updateServer(updatedServer: ServerItem) {
        val updated = _serverList.value.map { if (it.id == updatedServer.id) updatedServer else it }
        _serverList.value = updated
        repository.saveServers(updated)

        val map = _serverStatusMap.value.toMutableMap()
        val prev = map[updatedServer.id]
        map[updatedServer.id] = (prev ?: ServerRuntimeState(server = updatedServer)).copy(server = updatedServer)
        _serverStatusMap.value = map

        if (_activeServerId.value == updatedServer.id) {
            selectServer(updatedServer)
        }
    }

    fun deleteServer(serverId: String) {
        val updated = _serverList.value.filter { it.id != serverId }
        _serverList.value = updated
        repository.saveServers(updated)

        val map = _serverStatusMap.value.toMutableMap()
        map.remove(serverId)
        _serverStatusMap.value = map

        if (_activeServerId.value == serverId) {
            clearActiveServer()
        }
    }

    fun refreshAllServers() {
        for (server in _serverList.value) {
            pingServer(server)
        }
    }

    fun pingServer(server: ServerItem) {
        viewModelScope.launch {
            updateServerState(server.id) { it.copy(isPinging = true) }
            val status = MinecraftPing.ping(server.host, server.pingPort)
            updateServerState(server.id) { current ->
                val names = if (current.onlinePlayerList.isNotEmpty()) {
                    current.onlinePlayerList
                } else if (status.isOnline) {
                    status.playerSample
                        .filter { !it.contains("Anonymous", ignoreCase = true) }
                        .map { PlayerInfo(name = it) }
                } else {
                    emptyList()
                }
                current.copy(
                    isPinging = false,
                    status = status,
                    onlinePlayerList = names
                )
            }
        }
    }

    fun refreshActiveServerStatus() {
        val id = _activeServerId.value ?: return
        val current = _serverStatusMap.value[id] ?: return
        pingServer(current.server)
        refreshTps()
        refreshGameTime()
        executeRconCommand("list", silent = true)
    }

    fun refreshGameTime() {
        val id = _activeServerId.value ?: return
        val client = activeRconClient ?: return

        viewModelScope.launch {
            // time query gametime gives the total game ticks (e.g. 568624260 ticks)
            val gametimeRes = client.executeCommand("time query gametime")
            gametimeRes.onSuccess { output ->
                val regex = Regex("([0-9]+)")
                val match = regex.find(output)
                if (match != null) {
                    val ticks = match.groupValues[1].toLongOrNull() ?: -1L
                    updateServerState(id) { it.copy(gameTicks = ticks) }
                }
            }

            // time query day gives the timeline ticks within the day (0~24000)
            val dayRes = client.executeCommand("time query day")
            dayRes.onSuccess { output ->
                val regex = Regex("([0-9]+)")
                val match = regex.find(output)
                if (match != null) {
                    val ticks = match.groupValues[1].toLongOrNull() ?: -1L
                    updateServerState(id) { it.copy(dayTimeTicks = ticks) }
                }
            }
        }
    }

    suspend fun executeCommandWithResult(command: String, silent: Boolean = false): Result<String> {
        val id = _activeServerId.value ?: return Result.failure(Exception("未选择服务器"))
        val client = activeRconClient ?: return Result.failure(Exception("RCON 未连接"))

        if (!silent) {
            appendLog(id, LogType.COMMAND, "> $command")
        }

        val result = client.executeCommand(command)
        result.onSuccess { output ->
            updateServerState(id) { it.copy(isRconConnected = true) }
            if (!silent) {
                if (output.isNotEmpty()) {
                    appendLog(id, LogType.OUTPUT, output)
                } else {
                    appendLog(id, LogType.INFO, "指令已发送（无回显）")
                }
            }

            if (command.trim().equals("list", ignoreCase = true)) {
                parseOnlinePlayersFromList(id, output)
            }
        }.onFailure { error ->
            updateServerState(id) { it.copy(isRconConnected = false) }
            if (!silent) {
                appendLog(id, LogType.ERROR, "执行失败: ${error.message}")
            }
        }
        return result
    }

    fun executeRconCommand(command: String, silent: Boolean = false) {
        viewModelScope.launch {
            executeCommandWithResult(command, silent)
        }
    }

    fun refreshTps() {
        val id = _activeServerId.value ?: return
        val client = activeRconClient ?: return

        viewModelScope.launch {
            val result = client.executeCommand("tick query")
            result.onSuccess { output ->
                parseCarpetTps(id, output)
            }.onFailure {
                val fallback = client.executeCommand("tps")
                fallback.onSuccess { output2 ->
                    parseFallbackTps(id, output2)
                }.onFailure {
                    updateServerState(id) {
                        it.copy(tpsInfo = TpsInfo(isSupported = false, rawText = "未检测到支持的 TPS 监控指令"))
                    }
                }
            }
        }
    }

    private fun parseCarpetTps(serverId: String, output: String) {
        if (output.contains("Unknown") || output.contains("未知的") || output.contains("command not found")) {
            updateServerState(serverId) {
                it.copy(tpsInfo = TpsInfo(isSupported = false, rawText = output))
            }
            return
        }

        // Target tick rate: 20.0 per second. Average time per tick: 12.6ms
        val tpsRegex = Regex("(?i)(?:Target tick rate:|TPS:?)\\s*([0-9.]+)")
        val msptRegex = Regex("(?i)(?:Average time per tick:|MSPT:?)\\s*([0-9.]+)ms?")

        val tpsMatch = tpsRegex.find(output)
        val msptMatch = msptRegex.find(output)

        if (tpsMatch != null || msptMatch != null) {
            val tpsVal = tpsMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 20.0
            val msptVal = msptMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: -1.0
            updateServerState(serverId) { current ->
                val prevHistory = current.tpsInfo.history
                val newPoint = TpsHistoryPoint(tps = tpsVal, mspt = if (msptVal > 0) msptVal else 0.0)
                val updatedHistory = (prevHistory + newPoint).takeLast(30) // keep last 30 data points
                current.copy(
                    tpsInfo = TpsInfo(
                        isSupported = true,
                        tps = tpsVal,
                        mspt = msptVal,
                        rawText = output,
                        history = updatedHistory
                    )
                )
            }
        } else {
            updateServerState(serverId) {
                it.copy(tpsInfo = TpsInfo(isSupported = false, rawText = output))
            }
        }
    }

    private fun parseFallbackTps(serverId: String, output: String) {
        if (output.contains("Unknown") || output.contains("未知的")) {
            updateServerState(serverId) {
                it.copy(tpsInfo = TpsInfo(isSupported = false, rawText = output))
            }
            return
        }
        val tpsRegex = Regex("([0-9.]+)")
        val match = tpsRegex.find(output)
        if (match != null) {
            val tpsVal = match.groupValues[1].toDoubleOrNull() ?: -1.0
            updateServerState(serverId) {
                it.copy(tpsInfo = TpsInfo(isSupported = true, tps = tpsVal, mspt = -1.0, rawText = output))
            }
        } else {
            updateServerState(serverId) {
                it.copy(tpsInfo = TpsInfo(isSupported = false, rawText = output))
            }
        }
    }

    private fun parseOnlinePlayersFromList(serverId: String, output: String) {
        if (!output.contains("online", ignoreCase = true) && !output.contains("players", ignoreCase = true)) {
            return
        }
        val colonIndex = output.lastIndexOf(":")
        val activeNames = mutableSetOf<String>()
        if (colonIndex != -1 && colonIndex < output.length - 1) {
            val listPart = output.substring(colonIndex + 1).trim()
            if (listPart.isNotEmpty()) {
                activeNames.addAll(
                    listPart.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                )
            }
        }

        // Check online/offline diffs and fire notifications
        val previous = previousOnlinePlayerMap[serverId]
        if (previous != null) {
            val joined = activeNames - previous
            val left = previous - activeNames

            val serverName = _serverStatusMap.value[serverId]?.server?.name ?: "服务器"
            val appCtx = getApplication<Application>().applicationContext

            for (p in joined) {
                com.hycer.minepulse.service.NotificationHelper.sendPlayerEventNotification(
                    context = appCtx,
                    serverName = serverName,
                    playerName = p,
                    isJoin = true
                )
            }

            for (p in left) {
                com.hycer.minepulse.service.NotificationHelper.sendPlayerEventNotification(
                    context = appCtx,
                    serverName = serverName,
                    playerName = p,
                    isJoin = false
                )
            }
        }
        previousOnlinePlayerMap[serverId] = activeNames

        // Merge with repository's historical players
        val history = repository.loadHistoricalPlayers(serverId).toMutableSet()
        history.addAll(activeNames)
        repository.saveHistoricalPlayers(serverId, history)

        // Construct sorted PlayerInfo list: online players first, offline players greyed out
        val sortedList = history.map { name ->
            PlayerInfo(
                name = name,
                isOnline = activeNames.contains(name)
            )
        }.sortedWith(compareByDescending<PlayerInfo> { it.isOnline }.thenBy { it.name.lowercase() })

        updateServerState(serverId) { it.copy(onlinePlayerList = sortedList) }
    }

    private fun appendLog(serverId: String, type: LogType, text: String) {
        val entry = LogEntry(
            type = type,
            text = text,
            timestamp = timeFormat.format(Date())
        )
        updateServerState(serverId) {
            val currentLogs = it.logs.takeLast(199)
            it.copy(logs = currentLogs + entry)
        }
    }

    fun addMacro(label: String, command: String) {
        val newMacro = QuickMacro(label = label, command = command)
        val updated = _macroList.value + newMacro
        _macroList.value = updated
        repository.saveMacros(updated)
    }

    fun deleteMacro(macroId: String) {
        val updated = _macroList.value.filter { it.id != macroId }
        _macroList.value = updated
        repository.saveMacros(updated)
    }

    private fun updateServerState(serverId: String, block: (ServerRuntimeState) -> ServerRuntimeState) {
        val map = _serverStatusMap.value.toMutableMap()
        val curr = map[serverId]
        if (curr != null) {
            map[serverId] = block(curr)
            _serverStatusMap.value = map
        }
    }
}
