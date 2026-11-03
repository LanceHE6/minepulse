package com.hycer.minepulse.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MinecraftRconClient(
    private val host: String,
    private val port: Int,
    private val password: String,
    private val timeoutMs: Int = 4000
) {
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var isConnected = false
    private val mutex = Mutex()

    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            internalConnect()
        }
    }

    private fun internalConnect(): Result<Unit> {
        try {
            closeSocket()
            socket = Socket()
            socket?.soTimeout = timeoutMs
            socket?.connect(InetSocketAddress(host, port), timeoutMs)
            inputStream = socket?.getInputStream()
            outputStream = socket?.getOutputStream()

            // Packet Type 3: SERVERDATA_AUTH
            val authResponse = sendPacket(reqId = 1, type = 3, payload = password)
            if (authResponse.id == -1) {
                closeSocket()
                return Result.failure(Exception("RCON 认证失败：密码错误"))
            } else {
                isConnected = true
                return Result.success(Unit)
            }
        } catch (e: Exception) {
            closeSocket()
            return Result.failure(Exception("RCON 连接异常: ${e.message}"))
        }
    }

    suspend fun executeCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isConnected || socket?.isClosed == true) {
                val reconnectResult = internalConnect()
                if (reconnectResult.isFailure) {
                    return@withLock Result.failure(reconnectResult.exceptionOrNull() ?: Exception("未连接"))
                }
            }

            try {
                // Packet Type 2: SERVERDATA_EXECCOMMAND
                val response = sendPacket(reqId = 2, type = 2, payload = command)
                Result.success(cleanFormatting(response.payload))
            } catch (e: Exception) {
                isConnected = false
                Result.failure(e)
            }
        }
    }

    fun close() {
        closeSocket()
    }

    private fun closeSocket() {
        isConnected = false
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        inputStream = null
        outputStream = null
    }

    private fun sendPacket(reqId: Int, type: Int, payload: String): RconPacket {
        val out = outputStream ?: throw IllegalStateException("OutputStream is null")
        val inp = inputStream ?: throw IllegalStateException("InputStream is null")

        val payloadBytes = payload.toByteArray(Charsets.UTF_8)
        val packetLength = 4 + 4 + payloadBytes.size + 2 // id(4) + type(4) + payload + 2 null bytes

        val buffer = ByteBuffer.allocate(4 + packetLength).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(packetLength)
        buffer.putInt(reqId)
        buffer.putInt(type)
        buffer.put(payloadBytes)
        buffer.put(0.toByte())
        buffer.put(0.toByte())

        out.write(buffer.array())
        out.flush()

        // Read Response Length
        val lengthBuffer = ByteArray(4)
        readFully(inp, lengthBuffer)
        val respLength = ByteBuffer.wrap(lengthBuffer).order(ByteOrder.LITTLE_ENDIAN).int

        if (respLength < 10) {
            return RconPacket(-1, 0, "")
        }

        val respBuffer = ByteArray(respLength)
        readFully(inp, respBuffer)

        val bb = ByteBuffer.wrap(respBuffer).order(ByteOrder.LITTLE_ENDIAN)
        val resId = bb.int
        val resType = bb.int
        val bodyLength = respLength - 10
        val bodyBytes = ByteArray(bodyLength)
        bb.get(bodyBytes)

        val resPayload = String(bodyBytes, Charsets.UTF_8)
        return RconPacket(resId, resType, resPayload)
    }

    private fun readFully(inp: InputStream, target: ByteArray) {
        var total = 0
        while (total < target.size) {
            val count = inp.read(target, total, target.size - total)
            if (count == -1) throw Exception("Stream closed unexpectedly")
            total += count
        }
    }

    private fun cleanFormatting(input: String): String {
        return input.replace(Regex("§[0-9a-fk-orA-FK-OR]"), "").trim()
    }

    data class RconPacket(val id: Int, val type: Int, val payload: String)
}
