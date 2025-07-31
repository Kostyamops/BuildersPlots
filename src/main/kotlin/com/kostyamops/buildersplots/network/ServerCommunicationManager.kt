package com.kostyamops.buildersplots.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.ServerType
import com.kostyamops.buildersplots.data.Plot
import com.kostyamops.buildersplots.world.EmptyWorldGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class ServerCommunicationManager(private val plugin: BuildersPlots) {

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private val messageQueue = ConcurrentLinkedQueue<String>()
    private val gson: Gson = GsonBuilder().serializeNulls().create()
    private val executor = Executors.newSingleThreadScheduledExecutor()
    // Список чанков, которые были отсканированы для каждого плота
    private val scannedChunks = Collections.synchronizedMap(HashMap<String, MutableSet<Pair<Int, Int>>>())
    // Общее количество чанков для сканирования для каждого плота
    private val totalChunks = Collections.synchronizedMap(HashMap<String, Int>())

    // Папка для хранения миров плотов
    private val plotsWorldsFolder: File
        get() = File(plugin.dataFolder, "plots")

    private val ip: String
        get() = plugin.config.getString("communication.ip", "localhost")!!

    private val port: Int
        get() = plugin.config.getInt("communication.port", 25566)

    private val password: String
        get() = plugin.config.getString("server-password", "")!!

    private val blockChangeQueue = ConcurrentLinkedQueue<BlockChangeData>()
    private var blockProcessorTask: BukkitTask? = null
    private val maxBlocksPerTick = 5000 // Максимальное количество блоков, обрабатываемых за один тик
    private var totalProcessedBlocks = 0
    private var lastReportTime = System.currentTimeMillis()
    private val reportInterval = 5000 // Интервал отчета в миллисекундах

    suspend fun startCommunication() {
        withContext(Dispatchers.IO) {
            try {
                // Создаем папку для миров плотов, если она не существует
                if (!plotsWorldsFolder.exists()) {
                    plotsWorldsFolder.mkdirs()
                    plugin.logger.info("Создана папка для миров плотов: ${plotsWorldsFolder.absolutePath}")
                }

                when (plugin.serverType) {
                    ServerType.MAIN -> startMainServer()
                    ServerType.TEST -> connectToMainServer()
                }

                // Start message processor
                executor.scheduleAtFixedRate({ processMessageQueue() }, 1, 1, TimeUnit.SECONDS)
            } catch (e: Exception) {
                plugin.logger.severe("Failed to start communication: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    suspend fun stopCommunication() {
        withContext(Dispatchers.IO) {
            try {
                serverSocket?.close()
                clientSocket?.close()
                executor.shutdown()
            } catch (e: Exception) {
                plugin.logger.severe("Error closing connections: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun startMainServer() {
        plugin.logger.info("Starting main server communication on port $port")

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
                        plugin.logger.severe("Error in main server socket: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }.start()
        } catch (e: Exception) {
            plugin.logger.severe("Failed to start main server: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun connectToMainServer() {
        plugin.logger.info("Connecting to main server at $ip:$port")

        try {
            clientSocket = Socket(ip, port)

            // Authenticate with password
            val writer = PrintWriter(clientSocket!!.getOutputStream(), true)
            writer.println(gson.toJson(Message("AUTH", password)))

            // Start listener thread
            Thread {
                try {
                    val reader = BufferedReader(InputStreamReader(clientSocket!!.getInputStream()))
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        processMessage(line!!)
                    }
                } catch (e: Exception) {
                    plugin.logger.severe("Connection to main server lost: ${e.message}")
                    e.printStackTrace()

                    // Try to reconnect after delay
                    Thread.sleep(5000)
                    connectToMainServer()
                }
            }.start()
        } catch (e: Exception) {
            plugin.logger.severe("Failed to connect to main server: ${e.message}")
            e.printStackTrace()

            // Try to reconnect after delay
            Thread.sleep(5000)
            connectToMainServer()
        }
    }

    private fun handleClientConnection(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            // Wait for authentication
            val authLine = reader.readLine()
            val authMessage = gson.fromJson(authLine, Message::class.java)

            if (authMessage.type != "AUTH" || authMessage.data != password) {
                writer.println(gson.toJson(Message("ERROR", "Authentication failed")))
                socket.close()
                return
            }

            writer.println(gson.toJson(Message("AUTH_SUCCESS", "Authentication successful")))

            // Set as the current client socket
            clientSocket = socket

            // Listen for messages
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                processMessage(line!!)
            }
        } catch (e: Exception) {
            plugin.logger.warning("Client disconnected: ${e.message}")
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

    private fun processMessage(messageJson: String) {
        try {
            val jsonElement = JsonParser.parseString(messageJson)
            val jsonObject = jsonElement.asJsonObject
            val type = jsonObject["type"]?.asString ?: "UNKNOWN"
            plugin.logger.info("Received message of type: $type")

            val message = gson.fromJson(messageJson, Message::class.java)

            when (message.type) {
                "PLOT_CREATE" -> {
                    try {
                        val plot = gson.fromJson(message.data.toString(), Plot::class.java)
                        if (plot != null) {
                            handlePlotCreation(plot)
                        } else {
                            plugin.logger.severe("Failed to deserialize plot data")
                        }
                    } catch (e: Exception) {
                        plugin.logger.severe("Error processing PLOT_CREATE: ${e.message}")
                        e.printStackTrace()
                    }
                }
                "PLOT_DELETE" -> {
                    try {
                        handlePlotDeletion(message.data.toString())
                    } catch (e: Exception) {
                        plugin.logger.severe("Error processing PLOT_DELETE: ${e.message}")
                        e.printStackTrace()
                    }
                }
                "BLOCK_CHANGE" -> {
                    try {
                        val blockData = gson.fromJson(message.data.toString(), BlockChangeData::class.java)
                        if (blockData != null) {
                            handleBlockChange(blockData)
                        } else {
                            plugin.logger.severe("Failed to deserialize block data")
                        }
                    } catch (e: Exception) {
                        plugin.logger.severe("Error processing BLOCK_CHANGE: ${e.message}")
                        e.printStackTrace()
                    }
                }
                "REQUEST_PLOT_BLOCKS" -> {
                    try {
                        handleRequestPlotBlocks(message.data.toString())
                    } catch (e: Exception) {
                        plugin.logger.severe("Error processing REQUEST_PLOT_BLOCKS: ${e.message}")
                        e.printStackTrace()
                    }
                }
                "PLOT_BLOCKS" -> {
                    try {
                        val blocks = gson.fromJson(message.data.toString(), Array<PlotBlockData>::class.java)
                        handlePlotBlocks(blocks.toList())
                    } catch (e: Exception) {
                        plugin.logger.severe("Error processing PLOT_BLOCKS: ${e.message}")
                        e.printStackTrace()
                    }
                }
                "PLOT_SCAN_PROGRESS" -> {
                    try {
                        val progress = gson.fromJson(message.data.toString(), PlotScanProgress::class.java)
                        handlePlotScanProgress(progress)
                    } catch (e: Exception) {
                        plugin.logger.severe("Error processing PLOT_SCAN_PROGRESS: ${e.message}")
                        e.printStackTrace()
                    }
                }
                "PLOT_SCAN_COMPLETE" -> {
                    try {
                        handlePlotScanComplete(message.data.toString())
                    } catch (e: Exception) {
                        plugin.logger.severe("Error processing PLOT_SCAN_COMPLETE: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("Error processing message: ${e.message}")
            e.printStackTrace()
        }
    }


    private fun handlePlotCreation(plot: Plot) {
        // Add the plot to our manager
        plugin.plotManager.addOrUpdatePlot(plot)

        // If we're the test server, create a new world for this plot
        if (plugin.serverType == ServerType.TEST) {
            val worldName = plot.getTestWorldName()

            // Check if world already exists
            if (Bukkit.getWorld(worldName) != null) {
                plugin.logger.info("World already exists: $worldName")
                return
            }

            plugin.logger.info("Creating new world for plot: ${plot.name}")

            // На Purpur используем синхронный планировщик
            Bukkit.getScheduler().runTask(plugin, Runnable {
                try {
                    // Получаем папку для мира плота
                    val plotWorldFolder = File(plotsWorldsFolder, worldName)
                    if (!plotWorldFolder.exists()) {
                        plotWorldFolder.mkdirs()
                    }

                    // Создаем WorldCreator и указываем папку для мира
                    val creator = WorldCreator(worldName)
                        .generator(EmptyWorldGenerator())
                        .environment(World.Environment.NORMAL)
                        .generateStructures(false)

                    // Устанавливаем путь к папке мира (через рефлексию, если необходимо)
                    try {
                        val worldFolderField = WorldCreator::class.java.getDeclaredField("worldFolder")
                        worldFolderField.isAccessible = true
                        worldFolderField.set(creator, plotWorldFolder)
                    } catch (e: Exception) {
                        // Если не сработало через рефлексию, пробуем через свойства Bukkit
                        plugin.logger.warning("Не удалось установить папку мира через рефлексию: ${e.message}")
                        System.setProperty("org.bukkit.worldContainer", plotsWorldsFolder.absolutePath)
                    }

                    // Создаем мир
                    val world = creator.createWorld()

                    if (world != null) {
                        plugin.logger.info("World $worldName created successfully in custom folder!")

                        // Настраиваем мир
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            try {
                                world.difficulty = Difficulty.PEACEFUL
                                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
                                world.setGameRule(GameRule.DO_WEATHER_CYCLE, false)
                                world.setGameRule(GameRule.DO_MOB_SPAWNING, false)
                                world.setGameRule(GameRule.DO_FIRE_TICK, false)
                                world.setGameRule(GameRule.KEEP_INVENTORY, true)
                                world.time = 6000

                                // Создаем спавн-платформу
                                val spawnLocation = Location(
                                    world,
                                    plot.center.x,  // Используем оригинальные координаты центра плота
                                    plot.center.y,
                                    plot.center.z
                                )
                                world.spawnLocation = spawnLocation
                                plugin.logger.info("Set spawn location at original plot center: ${plot.center.x}, ${plot.center.y}, ${plot.center.z}")
//                                // Создаем небольшую платформу для спавна
//                                for (x in -5..5) {
//                                    for (z in -5..5) {
//                                        world.getBlockAt(x, 99, z).type = Material.STONE
//                                    }
//                                }
//
//                                // Отмечаем центр
//                                world.getBlockAt(0, 99, 0).type = Material.GOLD_BLOCK
//
//                                // Добавляем освещение
//                                for (x in -5..5 step 5) {
//                                    for (z in -5..5 step 5) {
//                                        world.getBlockAt(x, 100, z).type = Material.GLOWSTONE
//                                    }
//                                }

                                plugin.logger.info("World $worldName configured successfully.")

                                // Сразу запрашиваем копирование блоков с основного сервера
                                plugin.logger.info("Requesting blocks from main server for plot: ${plot.name}")
                                requestPlotBlocks(plot.name)

                            } catch (e: Exception) {
                                plugin.logger.severe("Failed to configure world: ${e.message}")
                                e.printStackTrace()
                            }
                        })
                    } else {
                        // Если создание мира не удалось
                        plugin.logger.severe("Failed to create world $worldName")
                    }

                } catch (e: Exception) {
                    plugin.logger.severe("Failed to create world: ${e.message}")
                    e.printStackTrace()
                }
            })
        }
    }

    private fun handlePlotDeletion(plotName: String) {
        // Remove the plot from our manager
        plugin.plotManager.deletePlot(plotName)

        // If we're the test server, unload and delete the world
        if (plugin.serverType == ServerType.TEST) {
            val plot = plugin.plotManager.getPlot(plotName)
            if (plot == null) {
                plugin.logger.warning("Plot not found for deletion: $plotName")
                return
            }

            val worldName = plot.getTestWorldName()
            val world = Bukkit.getWorld(worldName)
            if (world == null) {
                plugin.logger.warning("World not found for deletion: $worldName")
                return
            }

            // Используем синхронный планировщик для Purpur
            Bukkit.getScheduler().runTask(plugin, Runnable {
                try {
                    // Телепортируем игроков в другой мир
                    for (player in world.players) {
                        player.teleport(Bukkit.getWorlds()[0].spawnLocation)
                        player.sendMessage("§cПлот, на котором вы находились, был удален.")
                    }

                    // Выгружаем мир
                    val success = Bukkit.unloadWorld(world, false)
                    if (success) {
                        plugin.logger.info("World unloaded: $worldName")

                        // Удаляем папку мира
                        val worldFolder = File(plotsWorldsFolder, worldName)
                        if (worldFolder.exists()) {
                            if (deleteDirectory(worldFolder)) {
                                plugin.logger.info("World folder deleted: $worldName")
                            } else {
                                plugin.logger.warning("Failed to delete world folder: $worldName")
                            }
                        }
                    } else {
                        plugin.logger.warning("Failed to unload world: $worldName")
                    }
                } catch (e: Exception) {
                    plugin.logger.severe("Error unloading world: ${e.message}")
                    e.printStackTrace()
                }
            })
        }
    }

    private fun deleteDirectory(directory: File): Boolean {
        val files = directory.listFiles() ?: return false
        for (file in files) {
            if (file.isDirectory) {
                deleteDirectory(file)
            } else {
                file.delete()
            }
        }
        return directory.delete()
    }

    private fun handleBlockChange(blockData: BlockChangeData) {
        // Выполняем только на тестовом сервере
        if (plugin.serverType != ServerType.TEST) return

        // Получаем плот по имени
        val plot = plugin.plotManager.getPlot(blockData.plotName) ?: return

        // Получаем мир плота
        val worldName = plot.getTestWorldName(plugin)
        val world = Bukkit.getWorld(worldName) ?: return

        // Применяем изменения блока без лишних логов
        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                val block = world.getBlockAt(blockData.x, blockData.y, blockData.z)

                when (blockData.type) {
                    "BREAK" -> block.type = Material.AIR
                    else -> {
                        // Проверяем материал перед применением, чтобы избежать исключений
                        val material = Material.getMaterial(blockData.material) ?: return@Runnable
                        block.type = material

                        // Устанавливаем данные блока, если они доступны
                        if (blockData.blockData.isNotEmpty()) {
                            try {
                                block.blockData = Bukkit.createBlockData(blockData.blockData)
                            } catch (e: Exception) {
                                // Неверные данные блока, игнорируем
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Критическая ошибка, оставляем пустой catch для производительности
            }
        })
    }

    // Запрос блоков плота с основного сервера
    fun requestPlotBlocks(plotName: String) {
        val message = Message("REQUEST_PLOT_BLOCKS", plotName)
        messageQueue.add(gson.toJson(message))
        plugin.logger.info("Requesting blocks for plot: $plotName")

        // Отображаем сообщение для игроков на тестовом сервере
        if (plugin.serverType == ServerType.TEST) {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                Bukkit.broadcastMessage("§a[BuildersPlots] §eНачинается загрузка блоков для плота §6$plotName§e...")
            })
        }
    }

    // Обработка запроса блоков на основном сервере
    private fun handleRequestPlotBlocks(plotName: String) {
        if (plugin.serverType != ServerType.MAIN) {
            plugin.logger.warning("Received plot blocks request but not on MAIN server")
            return
        }

        plugin.logger.info("Processing block request for plot: $plotName")

        val plot = plugin.plotManager.getPlot(plotName) ?: run {
            plugin.logger.warning("Plot not found: $plotName")
            return
        }

        // Запускаем сканирование и отправку блоков
        scanAndSendPlotBlocks(plot)
    }

    private fun scanAndSendPlotBlocks(plot: Plot) {
        val minLoc = plot.getMinLocation()
        val maxLoc = plot.getMaxLocation()
        val world = Bukkit.getWorld(minLoc.world) ?: return

        // Инициализируем структуры для отслеживания прогресса
        val plotChunks = mutableListOf<Pair<Int, Int>>()
        scannedChunks[plot.name] = Collections.synchronizedSet(mutableSetOf())

        // Вычисляем все чанки в области плота и сортируем их от центра к краям
        val centerChunkX = plot.center.x.toInt() shr 4
        val centerChunkZ = plot.center.z.toInt() shr 4

        for (chunkX in (minLoc.x.toInt() shr 4)..(maxLoc.x.toInt() shr 4)) {
            for (chunkZ in (minLoc.z.toInt() shr 4)..(maxLoc.z.toInt() shr 4)) {
                plotChunks.add(Pair(chunkX, chunkZ))
            }
        }

        // Сортируем чанки по удалённости от центра
        plotChunks.sortBy { (chunkX, chunkZ) ->
            (chunkX - centerChunkX) * (chunkX - centerChunkX) +
                    (chunkZ - centerChunkZ) * (chunkZ - centerChunkZ)
        }

        totalChunks[plot.name] = plotChunks.size

        // ВАЖНО: Уменьшаем размер пакета до 100 блоков (вместо 2000)
        val batchSize = 2000

        // ВАЖНО: Задержка между отправками пакетов (10 тиков ≈ 500 мс)
        val delayBetweenBatches = 1L

        // Снижаем нагрузку - обрабатываем только 1 чанк одновременно
        val concurrentChunks = 1
        var activeChunks = 0
        val chunkLock = Object()

        // Диапазон высот для сканирования
        val minHeight = Math.max(world.minHeight, -64)
        val maxHeight = Math.min(world.maxHeight, 320)

        // Счетчик всех отправленных блоков для статистики
        var totalBlocksSent = 0

        // Итератор для чанков
        val chunkIterator = plotChunks.iterator()

        // Функция для запуска обработки следующего чанка
        fun processNextChunk() {
            synchronized(chunkLock) {
                if (chunkIterator.hasNext() && activeChunks < concurrentChunks) {
                    val (chunkX, chunkZ) = chunkIterator.next()
                    activeChunks++

                    // Запускаем обработку чанка в основном потоке
                    Bukkit.getScheduler().runTask(plugin, object : Runnable {
                        // Текущие координаты для продолжения сканирования
                        private var currentY = minHeight
                        private var currentX = Math.max(minLoc.x.toInt(), chunkX shl 4)
                        private var currentZ = Math.max(minLoc.z.toInt(), chunkZ shl 4)
                        private val xEnd = Math.min(maxLoc.x.toInt(), (chunkX shl 4) + 15)
                        private val zEnd = Math.min(maxLoc.z.toInt(), (chunkZ shl 4) + 15)
                        private val blockBatch = ArrayList<PlotBlockData>(batchSize)

                        override fun run() {
                            try {
                                // Сканируем только часть чанка за один раз
                                var blocksScanned = 0

                                // Продолжаем с того места, где остановились
                                scanLoop@ while (currentY < maxHeight) {
                                    while (currentX <= xEnd) {
                                        while (currentZ <= zEnd) {
                                            val block = world.getBlockAt(currentX, currentY, currentZ)
                                            val material = block.type

                                            // Пропускаем воздух
                                            if (material != Material.AIR &&
                                                material != Material.CAVE_AIR &&
                                                material != Material.VOID_AIR) {

                                                // Создаем данные о блоке
                                                val blockData = PlotBlockData(
                                                    plotName = plot.name,
                                                    x = currentX,
                                                    y = currentY,
                                                    z = currentZ,
                                                    material = material.name,
                                                    blockData = block.blockData.asString
                                                )

                                                blockBatch.add(blockData)
                                                blocksScanned++

                                                // Если набрали достаточно блоков, отправляем пакет
                                                if (blockBatch.size >= batchSize) {
                                                    totalBlocksSent += blockBatch.size
                                                    sendPlotBlocksAsync(ArrayList(blockBatch))
                                                    blockBatch.clear()

                                                    // Запоминаем текущую позицию и планируем продолжение с задержкой
                                                    currentZ++
                                                    Bukkit.getScheduler().runTaskLater(plugin, this, delayBetweenBatches)
                                                    return
                                                }
                                            }
                                            currentZ++
                                        }
                                        currentZ = Math.max(minLoc.z.toInt(), chunkZ shl 4)
                                        currentX++
                                    }
                                    currentX = Math.max(minLoc.x.toInt(), chunkX shl 4)
                                    currentY++
                                }

                                // Отправляем оставшиеся блоки
                                if (blockBatch.isNotEmpty()) {
                                    totalBlocksSent += blockBatch.size
                                    sendPlotBlocksAsync(ArrayList(blockBatch))
                                    blockBatch.clear()
                                }

                                // Чанк полностью обработан, обновляем статус
                                synchronized(scannedChunks) {
                                    scannedChunks[plot.name]?.add(Pair(chunkX, chunkZ))
                                    val scanned = scannedChunks[plot.name]?.size ?: 0
                                    val total = totalChunks[plot.name] ?: 1
                                    val progress = PlotScanProgress(plot.name, scanned, total)
                                    sendPlotScanProgress(progress)

                                    // Если все чанки обработаны, завершаем процесс
                                    if (scanned >= total) {
                                        sendPlotScanComplete(plot.name)
                                        scannedChunks.remove(plot.name)
                                        totalChunks.remove(plot.name)
                                    }
                                }

                                // Уменьшаем счетчик активных чанков и запускаем следующий
                                synchronized(chunkLock) {
                                    activeChunks--
                                    processNextChunk()
                                }
                            } catch (e: Exception) {
                                // При ошибке просто продолжаем со следующим чанком
                                synchronized(chunkLock) {
                                    activeChunks--
                                    processNextChunk()
                                }
                            }
                        }
                    })
                }
            }
        }

        // Запускаем обработку чанков
        processNextChunk()
    }

    /**
     * Асинхронная отправка блоков с отложенной сериализацией
     * Предотвращает блокировку основного потока сервера
     */
    private fun sendPlotBlocksAsync(blocks: List<PlotBlockData>) {
        // Выполняем сериализацию JSON в отдельном потоке
        executor.submit {
            try {
                val blockDataJson = gson.toJson(blocks)
                val message = Message("PLOT_BLOCKS", blockDataJson)
                messageQueue.add(gson.toJson(message))
            } catch (e: Exception) {
                // Игнорируем ошибки для обеспечения стабильности
            }
        }
    }

    /**
     * Сохраняем оригинальный метод для обратной совместимости,
     * но делегируем работу асинхронной версии
     */
    private fun sendPlotBlocks(blocks: List<PlotBlockData>) {
        sendPlotBlocksAsync(blocks)
    }

    // Отправка прогресса сканирования
    private fun sendPlotScanProgress(progress: PlotScanProgress) {
        val message = Message("PLOT_SCAN_PROGRESS", gson.toJson(progress))
        messageQueue.add(gson.toJson(message))
    }

    // Отправка сигнала о завершении копирования
    private fun sendPlotScanComplete(plotName: String) {
        val message = Message("PLOT_SCAN_COMPLETE", plotName)
        messageQueue.add(gson.toJson(message))
    }

    // Обработка партии блоков на тестовом сервере
    private fun handlePlotBlocks(blocks: List<PlotBlockData>) {
        if (plugin.serverType != ServerType.TEST) {
            plugin.logger.warning("Received plot blocks but not on TEST server")
            return
        }

        if (blocks.isEmpty()) return

        val plotName = blocks[0].plotName
        val plot = plugin.plotManager.getPlot(plotName) ?: run {
            plugin.logger.warning("Plot not found for blocks: $plotName")
            return
        }

        val worldName = plot.getTestWorldName()
        val world = Bukkit.getWorld(worldName) ?: run {
            plugin.logger.warning("World not found for plot: $worldName")
            return
        }

        // На Purpur используем синхронный планировщик
        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                for (blockData in blocks) {
                    try {
                        // Используем координаты блоков относительно центра плота
                        val block = world.getBlockAt(blockData.x, blockData.y, blockData.z)

                        val material = Material.valueOf(blockData.material)
                        block.type = material

                        if (blockData.blockData.isNotEmpty()) {
                            val data = Bukkit.createBlockData(blockData.blockData)
                            block.blockData = data
                        }
                    } catch (e: Exception) {
                        plugin.logger.warning("Error setting block: ${e.message}")
                    }
                }

                // plugin.logger.info("Placed ${blocks.size} blocks for plot $plotName")
            } catch (e: Exception) {
                plugin.logger.severe("Error handling block batch: ${e.message}")
                e.printStackTrace()
            }
        })
    }

    // Обработка информации о прогрессе сканирования
    private fun handlePlotScanProgress(progress: PlotScanProgress) {
        if (plugin.serverType != ServerType.TEST) return

        val percent = if (progress.totalChunks > 0) {
            (progress.scannedChunks * 100) / progress.totalChunks
        } else {
            0
        }

        plugin.logger.info("Plot scan progress: ${progress.scannedChunks}/${progress.totalChunks} chunks ($percent%) for plot ${progress.plotName}")

        // Отображаем прогресс для игроков
        Bukkit.getScheduler().runTask(plugin, Runnable {
            // Отправляем только игрокам в мире плота
            val worldName = "plot_${progress.plotName.lowercase().replace(" ", "_")}"
            val world = Bukkit.getWorld(worldName)

            if (world != null) {
                for (player in world.players) {
                    player.sendActionBar("§aКопирование плота: §e$percent%")
                }
            }

            // И администраторам
            for (player in Bukkit.getOnlinePlayers()) {
                if (player.isOp || player.hasPermission("buildersplots.admin")) {
                    player.sendMessage("§a[BuildersPlots] §eКопирование плота §6${progress.plotName}§e: §b$percent%")
                }
            }
        })
    }

    // Завершение обработки всех блоков
    private fun handlePlotScanComplete(plotName: String) {
        if (plugin.serverType != ServerType.TEST) return

        plugin.logger.info("All blocks received for plot: $plotName")

        val plot = plugin.plotManager.getPlot(plotName) ?: return
        val worldName = plot.getTestWorldName()
        val world = Bukkit.getWorld(worldName) ?: return

        // Финальная настройка мира
        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                world.time = 6000 // День

                // Уведомление для всех игроков
                Bukkit.broadcastMessage("§a[BuildersPlots] §eПлот '§6${plotName}§e' успешно скопирован и готов к использованию!")

                // Предложение телепортироваться админам и создателю
                for (player in Bukkit.getOnlinePlayers()) {
                    if (player.isOp || player.hasPermission("buildersplots.admin") ||
                        player.uniqueId.toString() == plot.creatorUUID.toString()) {  // Преобразуем оба значения в String

                        player.sendMessage("§a[BuildersPlots] §eВы можете телепортироваться на плот командой: §6/bp tp $plotName")
                    }
                }

                plugin.logger.info("Plot copying completed for: $plotName")
            } catch (e: Exception) {
                plugin.logger.severe("Error finalizing plot copying: ${e.message}")
                e.printStackTrace()
            }
        })
    }

    private fun processMessageQueue() {
        val socket = clientSocket
        if (socket == null || !socket.isConnected || socket.isClosed) {
            return
        }

        try {
            val writer = PrintWriter(socket.getOutputStream(), true)

            var message = messageQueue.poll()
            while (message != null) {
                writer.println(message)
                message = messageQueue.poll()
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error sending messages: ${e.message}")
            e.printStackTrace()
            clientSocket = null
        }
    }

    fun sendPlotCreation(plot: Plot) {
        try {
            val plotJson = gson.toJson(plot)
            plugin.logger.info("Sending plot creation: $plotJson")
            val message = Message("PLOT_CREATE", plotJson)
            messageQueue.add(gson.toJson(message))
        } catch (e: Exception) {
            plugin.logger.severe("Error serializing plot for sending: ${e.message}")
            e.printStackTrace()
        }
    }

    fun sendPlotDeletion(plotName: String) {
        val message = Message("PLOT_DELETE", plotName)
        messageQueue.add(gson.toJson(message))
    }

    fun sendBlockChange(blockData: BlockChangeData) {
        try {
            val blockDataJson = gson.toJson(blockData)
            val message = Message("BLOCK_CHANGE", blockDataJson)
            messageQueue.add(gson.toJson(message))
        } catch (e: Exception) {
            plugin.logger.severe("Error serializing block data for sending: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Телепортировать игрока в мир плота
     */
    fun teleportToPlot(player: Player, plotName: String): Boolean {
        if (plugin.serverType != ServerType.TEST) {
            player.sendMessage("§cТелепортация в плоты доступна только на тестовом сервере")
            return false
        }

        val plot = plugin.plotManager.getPlot(plotName)
        if (plot == null) {
            player.sendMessage("§cПлот с названием '$plotName' не найден")
            return false
        }

        val worldName = plot.getTestWorldName()
        val world = Bukkit.getWorld(worldName)
        if (world == null) {
            player.sendMessage("§cМир для плота '$plotName' не найден или не загружен")
            return false
        }

        player.sendMessage("§aТелепортация на плот '$plotName'...")
        player.teleport(world.spawnLocation)
        return true
    }
}

data class Message(
    val type: String,
    val data: Any
)

data class BlockChangeData(
    val type: String, // "PLACE" or "BREAK"
    val material: String,
    val blockData: String,
    val plotName: String,
    val x: Int,
    val y: Int,
    val z: Int
)

data class PlotBlockData(
    val plotName: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val material: String,
    val blockData: String
)

data class PlotScanProgress(
    val plotName: String,
    val scannedChunks: Int,
    val totalChunks: Int
)