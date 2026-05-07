package com.santiya.localaihub.hub

import com.santiya.localaihub.distributed.DistributedProtocolIds
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LanNodeHttpServer {

    companion object {
        const val DEFAULT_PORT = 17888
    }

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var serverThread: Thread? = null

    @Volatile
    private var runningPort: Int? = null

    @Volatile
    private var pairingToken: String = ""

    @Volatile
    private var statusProvider: () -> String = { """{"ok":false,"message":"status_unavailable"}""" }

    @Volatile
    private var distributedPlanProvider: (String?) -> String = {
        """{"ok":false,"error":"plan_unavailable","message":"Distributed planner is not ready."}"""
    }

    @Volatile
    private var executeHandler: (String) -> String = {
        """{"ok":false,"status":"execute_unavailable","message":"Execution handler is not ready."}"""
    }

    private val workers: ExecutorService = Executors.newCachedThreadPool()
    private val lock = Any()

    fun start(
        port: Int = DEFAULT_PORT,
        pairingToken: String,
        statusJsonProvider: () -> String,
        distributedPlanJsonProvider: (String?) -> String,
        executeJsonHandler: (String) -> String,
    ) {
        synchronized(lock) {
            this.pairingToken = pairingToken
            this.statusProvider = statusJsonProvider
            this.distributedPlanProvider = distributedPlanJsonProvider
            this.executeHandler = executeJsonHandler

            if (serverThread?.isAlive == true && runningPort == port) {
                return
            }

            stopLocked()

            val socket = ServerSocket(port).apply {
                reuseAddress = true
            }
            serverSocket = socket
            runningPort = port
            serverThread = Thread(
                { acceptLoop(socket) },
                "lan-node-http-$port"
            ).apply {
                isDaemon = true
                start()
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            stopLocked()
        }
    }

    private fun stopLocked() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        serverThread = null
        runningPort = null
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            try {
                val client = socket.accept()
                workers.execute { handleClient(client) }
            } catch (_: SocketException) {
                return
            } catch (_: Exception) {
                continue
            }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.soTimeout = 5_000
        socket.use { client ->
            val input = BufferedInputStream(client.getInputStream())
            val output = client.getOutputStream()
            val request = readRequest(input) ?: run {
                writeJson(output, 400, """{"ok":false,"error":"bad_request"}""")
                return
            }

            val path = request.path.substringBefore("?")
            val query = parseQuery(request.path.substringAfter("?", ""))
            val method = request.method.uppercase(Locale.US)

            when {
                method == "GET" && path == "/health" -> {
                    writeJson(
                        output,
                        200,
                        """{"ok":true,"service":"SantiyaLocalAiHub LAN Node","port":${runningPort ?: DEFAULT_PORT}}"""
                    )
                }

                method == "GET" && path == DistributedProtocolIds.HTTP_STATUS -> {
                    writeJson(output, 200, statusProvider())
                }

                method == "GET" && path == DistributedProtocolIds.HTTP_DISTRIBUTED_PLAN -> {
                    if (!isAuthorized(request.headers)) {
                        writeJson(output, 401, """{"ok":false,"error":"unauthorized","message":"Valid LAN token required."}""")
                        return
                    }
                    writeJson(output, 200, distributedPlanProvider(query["modelId"]))
                }

                method == "POST" && path == DistributedProtocolIds.HTTP_EXECUTE -> {
                    if (!isAuthorized(request.headers)) {
                        writeJson(output, 401, """{"ok":false,"error":"unauthorized","message":"Valid LAN token required."}""")
                        return
                    }
                    writeJson(output, 200, executeHandler(request.body))
                }

                method == "POST" && path == DistributedProtocolIds.HTTP_HANDSHAKE -> {
                    if (!isAuthorized(request.headers)) {
                        writeJson(output, 401, """{"ok":false,"error":"unauthorized","message":"Valid LAN token required."}""")
                        return
                    }
                    writeJson(
                        output,
                        501,
                        """{"ok":false,"status":"not_implemented","message":"Handshake transport exists as a contract, but the live pipeline runtime is not wired yet."}"""
                    )
                }

                method == "POST" && path == DistributedProtocolIds.HTTP_PIPELINE_STEP -> {
                    if (!isAuthorized(request.headers)) {
                        writeJson(output, 401, """{"ok":false,"error":"unauthorized","message":"Valid LAN token required."}""")
                        return
                    }
                    writeJson(
                        output,
                        501,
                        """{"ok":false,"status":"not_implemented","message":"Pipeline worker execution is not wired yet in the Android runtime."}"""
                    )
                }

                else -> {
                    writeJson(output, 404, """{"ok":false,"error":"route_not_found"}""")
                }
            }
        }
    }

    private fun readRequest(input: InputStream): HttpRequest? {
        val headerBuffer = ByteArrayOutputStream()
        var previous = -1
        while (true) {
            val current = input.read()
            if (current == -1) break
            headerBuffer.write(current)
            if (previous == '\n'.code && current == '\n'.code) break
            previous = current
            if (headerBuffer.size() > 64 * 1024) return null
        }

        val headerText = headerBuffer.toByteArray().toString(StandardCharsets.UTF_8)
        if (headerText.isBlank()) return null

        val lines = headerText.replace("\r\n", "\n")
            .split('\n')
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        val requestLine = lines.first().trim().split(' ')
        if (requestLine.size < 2) return null

        val headers = linkedMapOf<String, String>()
        for (line in lines.drop(1)) {
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            val key = line.substring(0, separator).trim().lowercase(Locale.US)
            val value = line.substring(separator + 1).trim()
            headers[key] = value
        }

        val contentLength = headers["content-length"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val bodyBytes = if (contentLength > 0) readFully(input, contentLength) else ByteArray(0)
        return HttpRequest(
            method = requestLine[0],
            path = requestLine[1],
            headers = headers,
            body = bodyBytes.toString(StandardCharsets.UTF_8)
        )
    }

    private fun isAuthorized(headers: Map<String, String>): Boolean {
        if (pairingToken.isBlank()) return false
        val bearer = headers["authorization"]
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substringAfter(' ')
            ?.trim()
        val lanToken = headers["x-lan-token"]?.trim()
        return bearer == pairingToken || lanToken == pairingToken
    }

    private fun writeJson(output: OutputStream, status: Int, body: String) {
        val payload = body.toByteArray(StandardCharsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 ")
            append(status)
            append(' ')
            append(statusText(status))
            append("\r\nContent-Type: application/json; charset=utf-8")
            append("\r\nContent-Length: ")
            append(payload.size)
            append("\r\nConnection: close\r\n\r\n")
        }.toByteArray(StandardCharsets.UTF_8)
        output.write(headers)
        output.write(payload)
        output.flush()
    }

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split('&')
            .mapNotNull { part ->
                if (part.isBlank()) return@mapNotNull null
                val pieces = part.split('=', limit = 2)
                val key = decode(pieces[0])
                val value = if (pieces.size > 1) decode(pieces[1]) else ""
                key to value
            }
            .toMap()
    }

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8)

    private fun readFully(input: InputStream, byteCount: Int): ByteArray {
        val output = ByteArrayOutputStream(byteCount)
        val buffer = ByteArray(4 * 1024)
        var remaining = byteCount
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (read <= 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
        return output.toByteArray()
    }

    private fun statusText(status: Int): String =
        when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            501 -> "Not Implemented"
            else -> "Error"
        }

    private data class HttpRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String,
    )
}
