package com.kostyamops.buildersplots.network

import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.network.packets.BlockChangePacket
import com.kostyamops.buildersplots.network.packets.Packet
import com.kostyamops.buildersplots.network.packets.PlotManagementPacket
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.logging.Level

class ServerConnection(
    private val plugin: BuildersPlots,
    private val sendPort: Int,
    private val receivePort: Int,
    private val isMainServer: Boolean
) {
    private var running = false
    private var receiverThread: Thread? = null
    private var senderThread: Thread? = null
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    
    private val packetQueue = ConcurrentLinkedQueue<Packet>()
    
    fun startConnection() {
        running = true
        
        // Start receiver thread
        receiverThread = Thread {
            runReceiver()
        }.apply {
            name = "BuildersPlots-Receiver"
            isDaemon = true
            start()
        }
        
        // Start sender thread
        senderThread = Thread {
            runSender()
        }.apply {
            name = "BuildersPlots-Sender"
            isDaemon = true
            start()
        }
        
        plugin.log("Network connection started. Listening on port $receivePort, sending to port $sendPort")
    }
    
    fun stopConnection() {
        running = false
        
        try {
            serverSocket?.close()
            clientSocket?.close()
        } catch (e: Exception) {
            plugin.log("Error closing sockets: ${e.message}", Level.WARNING)
        }
        
        receiverThread?.interrupt()
        senderThread?.interrupt()
        
        plugin.log("Network connection stopped")
    }
    
    fun sendPacket(packet: Packet) {
        packetQueue.add(packet)
    }
    
    private fun runReceiver() {
        try {
            serverSocket = ServerSocket(receivePort)
            plugin.log("Waiting for connection on port $receivePort")
            
            while (running) {
                val socket = serverSocket!!.accept()
                plugin.log("Connection accepted from ${socket.inetAddress.hostAddress}")
                
                try {
                    ObjectInputStream(socket.getInputStream()).use { input ->
                        val packet = input.readObject() as Packet
                        
                        // Process the packet on the main thread
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            processPacket(packet)
                        })
                    }
                } catch (e: Exception) {
                    if (running) {
                        plugin.log("Error processing incoming packet: ${e.message}", Level.WARNING)
                        e.printStackTrace()
                    }
                } finally {
                    try {
                        socket.close()
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        } catch (e: Exception) {
            if (running) {
                plugin.log("Receiver thread error: ${e.message}", Level.SEVERE)
                e.printStackTrace()
            }
        }
    }
    
    private fun runSender() {
        while (running) {
            if (packetQueue.isNotEmpty()) {
                val packet = packetQueue.poll()
                
                try {
                    Socket("localhost", sendPort).use { socket ->
                        ObjectOutputStream(socket.getOutputStream()).use { output ->
                            output.writeObject(packet)
                            output.flush()
                        }
                    }
                } catch (e: Exception) {
                    if (running) {
                        plugin.log("Error sending packet: ${e.message}", Level.WARNING)
                        // Put the packet back in the queue to retry
                        packetQueue.add(packet)
                        
                        // Wait a bit before retrying
                        Thread.sleep(5000)
                    }
                }
            } else {
                // Sleep a bit to reduce CPU usage
                Thread.sleep(50)
            }
        }
    }
    
    private fun processPacket(packet: Packet) {
        when (packet) {
            is BlockChangePacket -> {
                plugin.networkManager.handleBlockChange(packet)
            }
            is PlotManagementPacket -> {
                plugin.networkManager.handlePlotManagement(packet)
            }
            else -> {
                plugin.log("Unknown packet type: ${packet.javaClass.simpleName}", Level.WARNING)
            }
        }
    }
}