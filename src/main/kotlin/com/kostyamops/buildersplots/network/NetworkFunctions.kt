package com.kostyamops.buildersplots.network

import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.network.model.Message
import com.kostyamops.buildersplots.network.model.MessageType
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

/**
 * Функции для работы с сетевыми соединениями
 */
internal fun ServerCommunicationManager.startMainServer() {
    val plugin = this.plugin
    plugin.localizationManager.info("logs.servercommunication.starting_server",
        "%port%" to port.toString())

    try {
        serverSocket = ServerSocket(port)

        // Start a thread to accept connections
        Thread {
            try {
                while (!serverSocket!!.isClosed) {
                    val socket = serverSocket!!.accept()
                    handleClientConnection(socket)
                }
            } catch (e: Exception) {
                if (!serverSocket!!.isClosed) {
                    plugin.localizationManager.severe("logs.servercommunication.server_socket_error",
                        "%error%" to e.message.toString())
                    e.printStackTrace()
                }
            }
        }.start()
    } catch (e: Exception) {
        plugin.localizationManager.severe("logs.servercommunication.server_start_failed",
            "%error%" to e.message.toString())
        e.printStackTrace()
    }
}

internal fun ServerCommunicationManager.connectToMainServer() {
    val plugin = this.plugin
    plugin.localizationManager.info("logs.servercommunication.connecting",
        "%ip%" to ip,
        "%port%" to port.toString())

    try {
        clientSocket = Socket(ip, port)

        // Authenticate with password
        val writer = PrintWriter(clientSocket!!.getOutputStream(), true)
        writer.println(gson.toJson(Message(MessageType.AUTH, password)))

        // Start listener thread
        Thread {
            try {
                val reader = BufferedReader(InputStreamReader(clientSocket!!.getInputStream()))
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    processMessage(line!!)
                }
            } catch (e: Exception) {
                plugin.localizationManager.severe("logs.servercommunication.connection_lost",
                    "%error%" to e.message.toString())
                e.printStackTrace()

                // Try to reconnect after delay
                Thread.sleep(5000)
                connectToMainServer()
            }
        }.start()
    } catch (e: Exception) {
        plugin.localizationManager.severe("logs.servercommunication.connection_failed",
            "%error%" to e.message.toString())
        e.printStackTrace()

        // Try to reconnect after delay
        Thread.sleep(5000)
        connectToMainServer()
    }
}

internal fun ServerCommunicationManager.handleClientConnection(socket: Socket) {
    val plugin = this.plugin
    try {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val writer = PrintWriter(socket.getOutputStream(), true)

        // Wait for authentication
        val authLine = reader.readLine()
        val authMessage = gson.fromJson(authLine, Message::class.java)

        if (authMessage.type != MessageType.AUTH || authMessage.data != password) {
            writer.println(gson.toJson(Message(MessageType.ERROR,
                plugin.localizationManager.getLogMessage("logs.servercommunication.auth_failed"))))
            socket.close()
            return
        }

        writer.println(gson.toJson(Message(MessageType.AUTH_SUCCESS,
            plugin.localizationManager.getLogMessage("logs.servercommunication.auth_success"))))

        // Set as the current client socket
        clientSocket = socket

        // Listen for messages
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            processMessage(line!!)
        }
    } catch (e: Exception) {
        plugin.localizationManager.warning("logs.servercommunication.client_disconnected",
            "%error%" to e.message.toString())
        e.printStackTrace()
        if (socket == clientSocket) {
            clientSocket = null
        }
    } finally {
        try {
            socket.close()
        } catch (e: Exception) {
            // Ignore
        }
    }
}