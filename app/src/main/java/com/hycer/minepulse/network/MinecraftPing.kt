package com.hycer.minepulse.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

data class MinecraftServerStatus(
    val version: String = "未知",
    val protocol: Int = 0,
    val onlinePlayers: Int = 0,
    val maxPlayers: Int = 0,
    val playerSample: List<String> = emptyList(),
    val rawMotd: String = "",
    val motd: String = "",
    val iconBitmap: Bitmap? = null,
    val pingMs: Long = -1L,
    val isOnline: Boolean = false,
    val errorMessage: String? = null
)

object MinecraftPing {

    suspend fun ping(host: String, port: Int = 25565, timeoutMs: Int = 3500): MinecraftServerStatus =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.soTimeout = timeoutMs
                // Measure pure TCP connect latency
                val connectStart = System.currentTimeMillis()
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val pingTime = System.currentTimeMillis() - connectStart

                val outputStream = DataOutputStream(socket.getOutputStream())
                val inputStream = DataInputStream(socket.getInputStream())

                // 1. Handshake Packet (id: 0x00)
                val handshakeBytes = ByteArrayOutputStream()
                val handshakeOut = DataOutputStream(handshakeBytes)
                writeVarInt(handshakeOut, 0x00) // Packet ID
                writeVarInt(handshakeOut, 47)   // Protocol version (47 for ping compatibility)
                writeString(handshakeOut, host) // Server address
                handshakeOut.writeShort(port)   // Server port
                writeVarInt(handshakeOut, 1)    // Next state: 1 (status)

                val handshakePacket = handshakeBytes.toByteArray()
                writeVarInt(outputStream, handshakePacket.size)
                outputStream.write(handshakePacket)

                // 2. Status Request Packet (id: 0x00)
                writeVarInt(outputStream, 1) // length
                writeVarInt(outputStream, 0x00) // Packet ID: 0x00
                outputStream.flush()

                // Read Status Response
                readVarInt(inputStream) // Packet Length
                val packetId = readVarInt(inputStream)
                if (packetId != 0x00) {
                    throw IllegalStateException("Invalid response packet ID: $packetId")
                }

                val jsonLength = readVarInt(inputStream)
                val jsonBytes = ByteArray(jsonLength)
                inputStream.readFully(jsonBytes)
                val jsonString = String(jsonBytes, Charsets.UTF_8)

                parseJson(jsonString, pingTime)
            } catch (e: Exception) {
                MinecraftServerStatus(
                    isOnline = false,
                    pingMs = -1L,
                    errorMessage = e.message ?: "连接失败"
                )
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {}
            }
        }

    private fun parseJson(jsonStr: String, pingMs: Long): MinecraftServerStatus {
        return try {
            val root = JSONObject(jsonStr)

            // Version
            val versionObj = root.optJSONObject("version")
            val versionName = versionObj?.optString("name", "Minecraft") ?: "Minecraft"
            val protocol = versionObj?.optInt("protocol", 0) ?: 0

            // Players
            val playersObj = root.optJSONObject("players")
            val online = playersObj?.optInt("online", 0) ?: 0
            val max = playersObj?.optInt("max", 0) ?: 0
            val sampleList = mutableListOf<String>()
            val sampleArray = playersObj?.optJSONArray("sample")
            if (sampleArray != null) {
                for (i in 0 until sampleArray.length()) {
                    val p = sampleArray.optJSONObject(i)
                    val pName = p?.optString("name")
                    if (!pName.isNullOrEmpty()) {
                        sampleList.add(pName)
                    }
                }
            }

            // Description / MOTD
            val descObj = root.opt("description")
            val rawMotd = extractRawMotd(descObj)
            val motd = cleanMotd(descObj)

            // Favicon
            val faviconBase64 = root.optString("favicon", "")
            var bitmap: Bitmap? = null
            if (faviconBase64.isNotEmpty() && faviconBase64.contains(",")) {
                try {
                    val pureBase64 = faviconBase64.substringAfter(",")
                    val decoded = Base64.decode(pureBase64, Base64.DEFAULT)
                    bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                } catch (_: Exception) {}
            }

            MinecraftServerStatus(
                version = versionName,
                protocol = protocol,
                onlinePlayers = online,
                maxPlayers = max,
                playerSample = sampleList,
                rawMotd = rawMotd,
                motd = motd,
                iconBitmap = bitmap,
                pingMs = pingMs,
                isOnline = true
            )
        } catch (e: Exception) {
            MinecraftServerStatus(
                isOnline = true,
                pingMs = pingMs,
                errorMessage = "解析返回数据异常: ${e.message}"
            )
        }
    }

    private fun extractRawMotd(descObj: Any?): String {
        if (descObj == null) return ""
        if (descObj is String) return descObj
        if (descObj is JSONObject) {
            val text = descObj.optString("text", "")
            val sb = java.lang.StringBuilder(text)
            val extraArray = descObj.optJSONArray("extra")
            if (extraArray != null) {
                for (i in 0 until extraArray.length()) {
                    val item = extraArray.opt(i)
                    if (item is JSONObject) {
                        sb.append(item.optString("text", ""))
                    } else if (item is String) {
                        sb.append(item)
                    }
                }
            }
            return sb.toString().trim()
        }
        return descObj.toString()
    }

    private fun cleanMotd(descObj: Any?): String {
        if (descObj == null) return ""
        if (descObj is String) return stripMcColorCodes(descObj)
        if (descObj is JSONObject) {
            val text = descObj.optString("text", "")
            val sb = java.lang.StringBuilder(stripMcColorCodes(text))
            val extraArray = descObj.optJSONArray("extra")
            if (extraArray != null) {
                for (i in 0 until extraArray.length()) {
                    val item = extraArray.opt(i)
                    if (item is JSONObject) {
                        sb.append(stripMcColorCodes(item.optString("text", "")))
                    } else if (item is String) {
                        sb.append(stripMcColorCodes(item))
                    }
                }
            }
            return sb.toString().trim()
        }
        return descObj.toString()
    }

    private fun stripMcColorCodes(input: String): String {
        return input.replace(Regex("§[0-9a-fk-orA-FK-OR]"), "")
    }

    private fun writeVarInt(out: DataOutputStream, value: Int) {
        var v = value
        while (true) {
            if ((v and 0xFFFFFF80.toInt()) == 0) {
                out.writeByte(v)
                return
            }
            out.writeByte((v and 0x7F) or 0x80)
            v = v ushr 7
        }
    }

    private fun readVarInt(inp: DataInputStream): Int {
        var numRead = 0
        var result = 0
        var read: Byte
        do {
            read = inp.readByte()
            val value = (read.toInt() and 0x7F)
            result = result or (value shl (7 * numRead))
            numRead++
            if (numRead > 5) {
                throw RuntimeException("VarInt is too big")
            }
        } while ((read.toInt() and 0x80) != 0)
        return result
    }

    private fun writeString(out: DataOutputStream, string: String) {
        val bytes = string.toByteArray(Charsets.UTF_8)
        writeVarInt(out, bytes.size)
        out.write(bytes)
    }
}
