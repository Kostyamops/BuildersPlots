package ru.joutak.buildersplots.network

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.data.BlockData
import ru.joutak.buildersplots.BuildersPlots
import ru.joutak.buildersplots.model.BlockChange
import ru.joutak.buildersplots.model.Plot
import ru.joutak.buildersplots.model.PlotCreationRequest
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class NetworkManager(private val plugin: BuildersPlots) {

    private var serverSocket: ServerSocket? = null
    private val clientConnections = ConcurrentHashMap<String, Socket>()
    private val networkExecutor = Executors.newCachedThreadPool()

    init {
        // Start server socket based on role
        if (plugin.config.serverRole == ServerRole.RECEIVER) {
            startReceiver()
        }
    }

    private fun startReceiver() {
        networkExecutor.submit {
            try {
                serverSocket = ServerSocket(plugin.config.receiverPort)
                plugin.logger.info("Started receiver server on port ${plugin.config.receiverPort}")

                while (!Thread.currentThread().isInterrupted) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        handleClientConnection(clientSocket)
                    } catch (e: IOException) {
                        if (!Thread.currentThread().isInterrupted) {
                            plugin.logger.warning("Error accepting client connection: ${e.message}")
                        }
                    }
                }
            } catch (e: IOException) {
                plugin.logger.severe("Failed to start receiver server: ${e.message}")
            }
        }
    }

    private fun handleClientConnection(socket: Socket) {
        networkExecutor.submit {
            try {
                val clientId = "${socket.inetAddress.hostAddress}:${socket.port}"
                clientConnections[clientId] = socket
                plugin.logger.info("Client connected: $clientId")

                val input = ObjectInputStream(socket.getInputStream())

                while (!socket.isClosed) {
                    try {
                        when (val message = input.readObject()) {
                            is BlockChange -> handleBlockChange(message)
                            is PlotCreationRequest -> handlePlotCreationRequest(message)
                            else -> plugin.logger.warning("Received unknown message type: ${message.javaClass.name}")
                        }
                    } catch (e: EOFException) {
                        break
                    } catch (e: Exception) {
                        plugin.logger.warning("Error processing message: ${e.message}")
                        break
                    }
                }

                // Clean up
                clientConnections.remove(clientId)
                socket.close()
                plugin.logger.info("Client disconnected: $clientId")

            } catch (e: IOException) {
                plugin.logger.warning("Error handling client connection: ${e.message}")
            }
        }
    }

    private fun handleBlockChange(change: BlockChange) {
        if (plugin.config.serverRole == ServerRole.RECEIVER) {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                plugin.plotManager.applyBlockChange(change)
            })
        }
    }

    private fun handlePlotCreationRequest(request: PlotCreationRequest) {
        if (plugin.config.serverRole == ServerRole.RECEIVER) {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                plugin.plotManager.createPlot(request)
            })
        }
    }

    fun sendBlockChange(change: BlockChange): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()

        if (plugin.config.serverRole != ServerRole.SENDER) {
            future.complete(false)
            return future
        }

        networkExecutor.submit {
            try {
                val socket = getOrCreateSenderSocket()
                val output = ObjectOutputStream(socket.getOutputStream())

                output.writeObject(change)
                output.flush()

                future.complete(true)
            } catch (e: Exception) {
                plugin.logger.warning("Failed to send block change: ${e.message}")
                future.complete(false)
            }
        }

        return future
    }

    fun sendPlotCreationRequest(request: PlotCreationRequest): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()

        if (plugin.config.serverRole != ServerRole.SENDER) {
            future.complete(false)
            return future
        }

        networkExecutor.submit {
            try {
                val socket = getOrCreateSenderSocket()
                val output = ObjectOutputStream(socket.getOutputStream())

                output.writeObject(request)
                output.flush()

                future.complete(true)
            } catch (e: Exception) {
                plugin.logger.warning("Failed to send plot creation request: ${e.message}")
                future.complete(false)
            }
        }

        return future
    }

    private fun getOrCreateSenderSocket(): Socket {
        val existingSocket = clientConnections["receiver"]
        if (existingSocket != null && !existingSocket.isClosed) {
            return existingSocket
        }

        val socket = Socket(plugin.config.receiverHost, plugin.config.receiverPort)
        clientConnections["receiver"] = socket
        return socket
    }

    fun shutdown() {
        networkExecutor.shutdownNow()

        // Close all connections
        clientConnections.values.forEach { socket ->
            try {
                socket.close()
            } catch (e: IOException) {
                plugin.logger.warning("Error closing socket: ${e.message}")
            }
        }
        clientConnections.clear()

        // Close server socket
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            plugin.logger.warning("Error closing server socket: ${e.message}")
        }
    }
}