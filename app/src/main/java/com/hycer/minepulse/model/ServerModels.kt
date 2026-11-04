package com.hycer.minepulse.model

import android.graphics.Bitmap
import com.hycer.minepulse.network.MinecraftServerStatus
import java.util.UUID

data class ServerItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val pingPort: Int = 20000,
    val rconPort: Int = 25574,
    val rconPassword: String = "",
    val customIconBase64: String? = null,
    val notifyPlayerEvents: Boolean = false
)

data class QuickMacro(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val command: String,
    val iconName: String = "bolt"
)

data class PlayerInfo(
    val name: String,
    val uuid: String = generateOfflineUuid(name),
    val isOnline: Boolean = true,
    val isBot: Boolean = false
)

data class TpsHistoryPoint(
    val timestampMs: Long = System.currentTimeMillis(),
    val tps: Double,
    val mspt: Double
)

data class TpsInfo(
    val isSupported: Boolean = false,
    val tps: Double = -1.0,
    val mspt: Double = -1.0,
    val rawText: String = "",
    val history: List<TpsHistoryPoint> = emptyList()
)

data class ServerRuntimeState(
    val server: ServerItem,
    val status: MinecraftServerStatus = MinecraftServerStatus(),
    val tpsInfo: TpsInfo = TpsInfo(),
    val isPinging: Boolean = false,
    val isSilentRefreshing: Boolean = false,
    val isRconConnecting: Boolean = false,
    val isRconConnected: Boolean = false,
    val logs: List<LogEntry> = emptyList(),
    val onlinePlayerList: List<PlayerInfo> = emptyList(),
    val gameTicks: Long = -1L,
    val dayTimeTicks: Long = -1L,
    val whitelist: List<String> = emptyList(),
    val banlist: List<String> = emptyList()
)

fun generateOfflineUuid(username: String): String {
    return try {
        val md5 = java.security.MessageDigest.getInstance("MD5")
        val hash = md5.digest("OfflinePlayer:$username".toByteArray(Charsets.UTF_8))
        hash[6] = ((hash[6].toInt() and 0x0f) or 0x30).toByte()
        hash[8] = ((hash[8].toInt() and 0x3f) or 0x80).toByte()
        val bb = java.nio.ByteBuffer.wrap(hash)
        val high = bb.long
        val low = bb.long
        java.util.UUID(high, low).toString()
    } catch (_: Exception) {
        java.util.UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(Charsets.UTF_8)).toString()
    }
}

data class LogEntry(
    val id: Long = System.currentTimeMillis(),
    val type: LogType,
    val text: String,
    val timestamp: String
)

enum class LogType {
    INFO, COMMAND, OUTPUT, ERROR
}
