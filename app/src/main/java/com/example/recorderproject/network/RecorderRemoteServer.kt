package com.example.recorderproject.network

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.io.IOException
import java.net.InetSocketAddress
import java.util.Collections

class RecorderRemoteServer(
    private val context: Context,
    private val onCommand: (String) -> Unit,
    private val onClientConnected: () -> Unit
) {
    companion object {
        const val HTTP_PORT = 8765
        const val WS_PORT   = 8766
    }

    private val httpServer = object : NanoHTTPD(HTTP_PORT) {
        override fun serve(session: IHTTPSession): Response = try {
            val asset = context.assets.open("remote/index.html")
            newChunkedResponse(Response.Status.OK, "text/html", asset)
        } catch (_: IOException) {
            newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
    }

    private val wsClients: MutableSet<WebSocket> = Collections.synchronizedSet(mutableSetOf())

    private val wsServer = object : WebSocketServer(InetSocketAddress(WS_PORT)) {
        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
            wsClients.add(conn); onClientConnected()
        }
        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
            wsClients.remove(conn)
        }
        override fun onMessage(conn: WebSocket, message: String) { onCommand(message) }
        override fun onError(conn: WebSocket?, ex: Exception?) {}
        override fun onStart() {}
    }

    val clientCount: Int get() = wsClients.size

    fun start() {
        httpServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        wsServer.isReuseAddr = true
        wsServer.start()
    }

    fun stop() {
        try { httpServer.stop() } catch (_: Exception) {}
        try { wsServer.stop(1000) } catch (_: Exception) {}
    }

    fun broadcastState(levelJson: String) {
        wsClients.toSet().forEach { client ->
            try { if (client.isOpen) client.send(levelJson) }
            catch (_: Exception) { wsClients.remove(client) }
        }
    }
}
