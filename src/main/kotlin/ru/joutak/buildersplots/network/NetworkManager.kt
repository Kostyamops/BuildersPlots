package ru.joutak.buildersplots.network

import ru.joutak.buildersplots.BuildersPlots
import ru.joutak.buildersplots.model.Plot
import ru.joutak.buildersplots.util.SchematicUtil
import java.io.*
import java.net.ConnectException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.Future

class NetworkManager(private val plugin: BuildersPlots) {

    private val executorService = Executors.newCachedThreadPool()
    private var running = true
    private val serverRole = plugin.config.serverRole
    private val receiverHost = plugin.config.receiverHost
    private val receiverPort = plugin.config.receiverPort
    private val senderPort = plugin.config.senderPort
    
    private var serverSocket: ServerSocket? = null
    private var senderFuture: Future<*>? = null
    private var receiverFuture: Future<*>? = null

    // Запуск сервера-отправителя (SENDER)
    fun startSender() {
        if (serverRole != ServerRole.SENDER) {
            plugin.logger.warning("[BuildersPlots] Cannot start SENDER service on a RECEIVER server")
            return
        }
        
        senderFuture = executorService.submit {
            try {
                plugin.logger.info("[BuildersPlots] Starting SENDER service on port $senderPort")
                ServerSocket(senderPort).use { socket ->
                    serverSocket = socket
                    
                    while (running) {
                        try {
                            val clientSocket = socket.accept()
                            handleSenderConnection(clientSocket)
                        } catch (e: SocketTimeoutException) {
                            // Игнорируем таймауты
                        } catch (e: IOException) {
                            if (running) {
                                plugin.logger.warning("[BuildersPlots] Error accepting connection: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (running) {
                    plugin.logger.severe("[BuildersPlots] Failed to start SENDER service: ${e.message}")
                }
            }
        }
    }
    
    // Запуск сервера-получателя (RECEIVER)
    fun startReceiver() {
        if (serverRole != ServerRole.RECEIVER) {
            plugin.logger.warning("[BuildersPlots] Cannot start RECEIVER service on a SENDER server")
            return
        }
        
        receiverFuture = executorService.submit {
            try {
                plugin.logger.info("[BuildersPlots] Starting RECEIVER service on port $receiverPort")
                ServerSocket(receiverPort).use { socket ->
                    serverSocket = socket
                    
                    while (running) {
                        try {
                            val clientSocket = socket.accept()
                            handleReceiverConnection(clientSocket)
                        } catch (e: SocketTimeoutException) {
                            // Игнорируем таймауты
                        } catch (e: IOException) {
                            if (running) {
                                plugin.logger.warning("[BuildersPlots] Error accepting connection: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (running) {
                    plugin.logger.severe("[BuildersPlots] Failed to start RECEIVER service: ${e.message}")
                }
            }
        }
    }
    
    // Обработка подключения на стороне SENDER
    private fun handleSenderConnection(socket: Socket) {
        executorService.submit {
            try {
                socket.use { client ->
                    val input = DataInputStream(client.getInputStream())
                    val output = DataOutputStream(client.getOutputStream())
                    
                    // Читаем команду
                    val command = input.readUTF()
                    
                    when (command) {
                        "PING" -> {
                            // Отвечаем на пинг
                            output.writeUTF("PONG")
                            output.flush()
                            
                            if (plugin.config.debugMode) {
                                plugin.logger.info("[BuildersPlots] Received PING request from ${client.inetAddress.hostAddress}")
                            }
                        }
                        "REQUEST_PLOT" -> {
                            // Запрос на отправку плота
                            val plotName = input.readUTF()
                            val plot = plugin.plotManager.getPlotByName(plotName)
                            
                            if (plot == null) {
                                output.writeUTF("ERROR")
                                output.writeUTF("Plot not found")
                            } else {
                                // Отправляем информацию о плоте
                                output.writeUTF("OK")
                                
                                // Сериализуем плот
                                val byteStream = ByteArrayOutputStream()
                                ObjectOutputStream(byteStream).use { objectStream ->
                                    objectStream.writeObject(plot)
                                }
                                
                                // Отправляем данные плота
                                val plotData = byteStream.toByteArray()
                                output.writeInt(plotData.size)
                                output.write(plotData)
                                
                                // Сохраняем схематику и отправляем её
                                val schematicBytes = SchematicUtil.serializePlotToSchematic(plot)
                                
                                if (schematicBytes != null) {
                                    output.writeInt(schematicBytes.size)
                                    
                                    // Отправляем по частям с отчетом о прогрессе
                                    val chunkSize = 8192 // 8KB chunks
                                    var sentBytes = 0
                                    var lastPercent = 0
                                    
                                    while (sentBytes < schematicBytes.size) {
                                        val remaining = schematicBytes.size - sentBytes
                                        val currentChunkSize = minOf(chunkSize, remaining)
                                        
                                        output.write(schematicBytes, sentBytes, currentChunkSize)
                                        sentBytes += currentChunkSize
                                        
                                        // Отчет о прогрессе каждые 10%
                                        val percent = (sentBytes * 100 / schematicBytes.size)
                                        if (percent / 10 > lastPercent / 10) {
                                            lastPercent = percent
                                            plugin.logger.info("[BuildersPlots] Sending plot $plotName: $percent% complete")
                                        }
                                    }
                                    
                                    plugin.logger.info("[BuildersPlots] Plot $plotName sent successfully (${schematicBytes.size} bytes)")
                                } else {
                                    output.writeInt(0) // Нет данных схематики
                                    plugin.logger.warning("[BuildersPlots] Failed to create schematic for plot $plotName")
                                }
                            }
                        }
                        else -> {
                            if (plugin.config.debugMode) {
                                plugin.logger.warning("[BuildersPlots] Unknown command received: $command")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                plugin.logger.warning("[BuildersPlots] Error handling SENDER connection: ${e.message}")
            }
        }
    }
    
    // Обработка подключения на стороне RECEIVER
    private fun handleReceiverConnection(socket: Socket) {
        executorService.submit {
            try {
                socket.use { client ->
                    val input = DataInputStream(client.getInputStream())
                    val output = DataOutputStream(client.getOutputStream())
                    
                    // Читаем команду
                    val command = input.readUTF()
                    
                    when (command) {
                        "PING" -> {
                            // Отвечаем на пинг
                            output.writeUTF("PONG")
                            output.flush()
                            
                            if (plugin.config.debugMode) {
                                plugin.logger.info("[BuildersPlots] Received PING request from ${client.inetAddress.hostAddress}")
                            }
                        }
                        "SEND_PLOT" -> {
                            // Получаем данные плота
                            val dataSize = input.readInt()
                            val plotData = ByteArray(dataSize)
                            var bytesRead = 0
                            
                            while (bytesRead < dataSize) {
                                val count = input.read(plotData, bytesRead, dataSize - bytesRead)
                                if (count < 0) break
                                bytesRead += count
                            }
                            
                            // Десериализуем плот
                            val byteArrayInputStream = ByteArrayInputStream(plotData)
                            val objectInputStream = ObjectInputStream(byteArrayInputStream)
                            val plot = objectInputStream.readObject() as Plot
                            
                            // Получаем схематику
                            val schematicSize = input.readInt()
                            
                            if (schematicSize > 0) {
                                val schematicData = ByteArray(schematicSize)
                                bytesRead = 0
                                var lastPercent = 0
                                
                                while (bytesRead < schematicSize) {
                                    val count = input.read(schematicData, bytesRead, schematicSize - bytesRead)
                                    if (count < 0) break
                                    bytesRead += count
                                    
                                    // Отчет о прогрессе каждые 10%
                                    val percent = (bytesRead * 100 / schematicSize)
                                    if (percent / 10 > lastPercent / 10) {
                                        lastPercent = percent
                                        plugin.logger.info("[BuildersPlots] Receiving plot ${plot.name}: $percent% complete")
                                    }
                                }
                                
                                // Сохраняем плот и схематику
                                plugin.plotManager.getPlots().find { it.name.equals(plot.name, ignoreCase = true) }?.let {
                                    plugin.plotManager.deletePlot(it.name)
                                }
                                
                                // Сохраняем схематику во временный файл
                                val tempFile = File(plugin.dataFolder, "${plot.name}.schematic.temp")
                                FileOutputStream(tempFile).use { fos ->
                                    fos.write(schematicData)
                                }
                                
                                // Загружаем схематику в мир
                                val success = SchematicUtil.loadSchematicToWorld(tempFile, plot)
                                
                                // Сохраняем плот
                                if (success) {
                                    plugin.plotManager.createPlot(plot)
                                    output.writeUTF("OK")
                                    
                                    plugin.logger.info("[BuildersPlots] Plot ${plot.name} received and loaded successfully")
                                } else {
                                    output.writeUTF("ERROR")
                                    output.writeUTF("Failed to load schematic")
                                    
                                    plugin.logger.warning("[BuildersPlots] Failed to load schematic for plot ${plot.name}")
                                }
                                
                                // Удаляем временный файл
                                tempFile.delete()
                            } else {
                                output.writeUTF("ERROR")
                                output.writeUTF("No schematic data received")
                                
                                plugin.logger.warning("[BuildersPlots] No schematic data received for plot ${plot.name}")
                            }
                        }
                        "CREATE_PLOT" -> {
                            try {
                                // Получаем данные плота
                                val objectInputStream = ObjectInputStream(client.getInputStream())
                                val plot = objectInputStream.readObject() as Plot
                                
                                // Проверяем, существует ли плот
                                if (plugin.plotManager.plotExists(plot.name)) {
                                    output.writeUTF("ERROR")
                                    output.writeUTF("Plot already exists")
                                } else {
                                    // Сохраняем плот
                                    plugin.plotManager.createPlot(plot)
                                    output.writeUTF("OK")
                                    
                                    plugin.logger.info("[BuildersPlots] Created plot ${plot.name} from SENDER request")
                                }
                            } catch (e: Exception) {
                                output.writeUTF("ERROR")
                                output.writeUTF("Failed to read plot: ${e.message}")
                                plugin.logger.warning("[BuildersPlots] Error creating plot: ${e.message}")
                            }
                        }
                        else -> {
                            if (plugin.config.debugMode) {
                                plugin.logger.warning("[BuildersPlots] Unknown command received: $command")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                plugin.logger.warning("[BuildersPlots] Error handling RECEIVER connection: ${e.message}")
            }
        }
    }
    
    // Метод для отправки плота на RECEIVER
    fun sendPlotToReceiver(plot: Plot): Boolean {
        if (serverRole != ServerRole.SENDER) {
            throw IllegalStateException("Can only send plots from SENDER server")
        }
        
        return try {
            plugin.logger.info("[BuildersPlots] Sending plot ${plot.name} to RECEIVER server")
            
            Socket(receiverHost, receiverPort).use { socket ->
                val output = DataOutputStream(socket.getOutputStream())
                val input = DataInputStream(socket.getInputStream())
                
                // Отправляем команду
                output.writeUTF("SEND_PLOT")
                
                // Сериализуем плот
                val byteStream = ByteArrayOutputStream()
                ObjectOutputStream(byteStream).use { objectStream ->
                    objectStream.writeObject(plot)
                }
                
                // Отправляем данные плота
                val plotData = byteStream.toByteArray()
                output.writeInt(plotData.size)
                output.write(plotData)
                
                // Сохраняем схематику и отправляем её
                val schematicBytes = SchematicUtil.serializePlotToSchematic(plot)
                
                if (schematicBytes != null) {
                    output.writeInt(schematicBytes.size)
                    
                    // Отправляем по частям с отчетом о прогрессе
                    val chunkSize = 8192 // 8KB chunks
                    var sentBytes = 0
                    var lastPercent = 0
                    
                    while (sentBytes < schematicBytes.size) {
                        val remaining = schematicBytes.size - sentBytes
                        val currentChunkSize = minOf(chunkSize, remaining)
                        
                        output.write(schematicBytes, sentBytes, currentChunkSize)
                        sentBytes += currentChunkSize
                        
                        // Отчет о прогрессе каждые 10%
                        val percent = (sentBytes * 100 / schematicBytes.size)
                        if ((percent / 10) > (lastPercent / 10)) {
                            lastPercent = percent
                            plugin.logger.info("[BuildersPlots] Sending plot ${plot.name}: $percent% complete")
                        }
                    }
                    
                    // Получаем ответ
                    val response = input.readUTF()
                    
                    if (response == "OK") {
                        plugin.logger.info("[BuildersPlots] Plot ${plot.name} sent successfully")
                        return true
                    } else {
                        val errorMessage = input.readUTF()
                        plugin.logger.warning("[BuildersPlots] Failed to send plot ${plot.name}: $errorMessage")
                        return false
                    }
                } else {
                    output.writeInt(0) // Нет данных схематики
                    plugin.logger.warning("[BuildersPlots] Failed to create schematic for plot ${plot.name}")
                    return false
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("[BuildersPlots] Error sending plot to RECEIVER: ${e.message}")
            false
        }
    }
    
    // Метод для создания плота на RECEIVER из SENDER
    fun createPlotOnReceiver(plot: Plot): Boolean {
        if (serverRole != ServerRole.SENDER) {
            throw IllegalStateException("Can only send plots from SENDER server")
        }
        
        return try {
            plugin.logger.info("[BuildersPlots] Creating plot ${plot.name} on RECEIVER server")
            
            Socket(receiverHost, receiverPort).use { socket ->
                val output = DataOutputStream(socket.getOutputStream())
                val input = DataInputStream(socket.getInputStream())
                
                // Отправляем команду
                output.writeUTF("CREATE_PLOT")
                
                // Сериализуем и отправляем плот
                val oos = ObjectOutputStream(socket.getOutputStream())
                oos.writeObject(plot)
                oos.flush()
                
                // Получаем ответ
                val response = input.readUTF()
                
                if (response == "OK") {
                    plugin.logger.info("[BuildersPlots] Plot ${plot.name} created on RECEIVER successfully")
                    return true
                } else {
                    val errorMessage = input.readUTF()
                    plugin.logger.warning("[BuildersPlots] Failed to create plot ${plot.name} on RECEIVER: $errorMessage")
                    return false
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("[BuildersPlots] Error creating plot on RECEIVER: ${e.message}")
            false
        }
    }
    
    // Метод для проверки соединения (ping)
    fun pingServer(targetRole: ServerRole): Pair<Boolean, Long> {
        val host: String
        val port: Int
        
        // Определяем адрес сервера для пинга
        if (targetRole == ServerRole.RECEIVER) {
            host = receiverHost
            port = receiverPort
        } else {
            host = "localhost" // SENDER всегда на localhost для RECEIVER
            port = senderPort
        }
        
        return try {
            plugin.logger.info("[BuildersPlots] Pinging $targetRole server at $host:$port")
            
            val startTime = System.currentTimeMillis()
            Socket(host, port).use { socket ->
                val output = DataOutputStream(socket.getOutputStream())
                val input = DataInputStream(socket.getInputStream())
                
                // Устанавливаем таймаут
                socket.soTimeout = 5000
                
                // Отправляем команду PING
                output.writeUTF("PING")
                output.flush()
                
                // Получаем ответ
                val response = input.readUTF()
                val endTime = System.currentTimeMillis()
                
                if (response == "PONG") {
                    val latency = endTime - startTime
                    plugin.logger.info("[BuildersPlots] Ping successful to $targetRole. Latency: ${latency}ms")
                    return Pair(true, latency)
                } else {
                    plugin.logger.warning("[BuildersPlots] Unexpected ping response: $response")
                    return Pair(false, -1)
                }
            }
        } catch (e: ConnectException) {
            plugin.logger.warning("[BuildersPlots] Connection refused while pinging $targetRole")
            Pair(false, -1)
        } catch (e: SocketTimeoutException) {
            plugin.logger.warning("[BuildersPlots] Connection timed out while pinging $targetRole")
            Pair(false, -1)
        } catch (e: Exception) {
            plugin.logger.warning("[BuildersPlots] Error pinging $targetRole: ${e.message}")
            Pair(false, -1)
        }
    }
    
    fun shutdown() {
        running = false
        
        // Закрываем серверный сокет
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Игнорируем
        }
        
        // Отменяем задачи
        senderFuture?.cancel(true)
        receiverFuture?.cancel(true)
        
        // Останавливаем пул потоков
        executorService.shutdownNow()
        
        plugin.logger.info("[BuildersPlots] Network manager shutdown complete")
    }
}