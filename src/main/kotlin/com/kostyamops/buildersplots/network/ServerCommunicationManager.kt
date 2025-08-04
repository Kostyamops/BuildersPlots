package com.kostyamops.buildersplots.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.ServerType
import com.kostyamops.buildersplots.data.Plot
import com.kostyamops.buildersplots.network.handlers.BlockHandler
import com.kostyamops.buildersplots.network.handlers.PlotHandler
import com.kostyamops.buildersplots.network.model.*
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Менеджер сетевой коммуникации между серверами
 */
class ServerCommunicationManager(val plugin: BuildersPlots) {

    var serverSocket: ServerSocket? = null
    var clientSocket: Socket? = null
    private val messageQueue = ConcurrentLinkedQueue<String>()
    val gson: Gson = GsonBuilder().serializeNulls().create()
    private val executor = Executors.newSingleThreadScheduledExecutor()

    // Обработчики
    private val blockHandler = BlockHandler(this, plugin)
    private val plotHandler = PlotHandler(this, plugin)

    // Список чанков, которые были отсканированы для каждого плота
    internal val scannedChunks = Collections.synchronizedMap(HashMap<String, MutableSet<Pair<Int, Int>>>())
    // Общее количество чанков для сканирования для каждого плота
    internal val totalChunks = Collections.synchronizedMap(HashMap<String, Int>())

    // Папка для хранения миров плотов
    val plotsWorldsFolder: File
        get() = File(plugin.dataFolder, "plots")

    val ip: String
        get() = plugin.config.getString("communication.ip", "localhost")!!

    val port: Int
        get() = plugin.config.getInt("communication.port", 25566)

    val password: String
        get() = plugin.config.getString("server-password", "")!!

    private val blockChangeQueue = ConcurrentLinkedQueue<BlockChangeData>()
    private var blockProcessorTask: BukkitTask? = null
    private val maxBlocksPerTick = 5000 // Максимальное количество блоков, обрабатываемых за один тик
    private var totalProcessedBlocks = 0
    private var lastReportTime = System.currentTimeMillis()
    private val reportInterval = 5000 // Интервал отчета в миллисекундах

    /**
     * Запуск менеджера коммуникации
     */
    suspend fun startCommunication() {
        withContext(Dispatchers.IO) {
            try {
                // Создаем папку для миров плотов, если она не существует
                if (!plotsWorldsFolder.exists()) {
                    plotsWorldsFolder.mkdirs()
                    plugin.localizationManager.info("logs.servercommunication.plots_folder_created",
                        "%path%" to plotsWorldsFolder.absolutePath)
                }

                when (plugin.serverType) {
                    ServerType.MAIN -> startMainServer()
                    ServerType.TEST -> connectToMainServer()
                }

                // Start message processor
                executor.scheduleAtFixedRate({ processMessageQueue() }, 1, 1, TimeUnit.SECONDS)
            } catch (e: Exception) {
                plugin.localizationManager.severe("logs.servercommunication.communication_start_failed",
                    "%error%" to e.message.toString())
                e.printStackTrace()
            }
        }
    }

    /**
     * Остановка менеджера коммуникации
     */
    suspend fun stopCommunication() {
        withContext(Dispatchers.IO) {
            try {
                serverSocket?.close()
                clientSocket?.close()
                executor.shutdown()
            } catch (e: Exception) {
                plugin.localizationManager.severe("logs.servercommunication.connections_close_error",
                    "%error%" to e.message.toString())
                e.printStackTrace()
            }
        }
    }

    /**
     * Обработка входящего сообщения
     */
    internal fun processMessage(messageJson: String) {
        try {
            val jsonElement = JsonParser.parseString(messageJson)
            val jsonObject = jsonElement.asJsonObject
            val type = jsonObject["type"]?.asString ?: "UNKNOWN"
            plugin.localizationManager.info("logs.servercommunication.received_message",
                "%type%" to type)

            val message = gson.fromJson(messageJson, Message::class.java)

            when (message.type) {
                MessageType.PLOT_CREATE -> {
                    try {
                        val plot = gson.fromJson(message.data.toString(), Plot::class.java)
                        if (plot != null) {
                            plotHandler.handlePlotCreation(plot)
                        } else {
                            plugin.localizationManager.severe("logs.servercommunication.deserialize_plot_failed")
                        }
                    } catch (e: Exception) {
                        plugin.localizationManager.severe("logs.servercommunication.plot_create_error",
                            "%error%" to e.message.toString())
                        e.printStackTrace()
                    }
                }
                MessageType.PLOT_DELETE -> {
                    try {
                        plotHandler.handlePlotDeletion(message.data.toString())
                    } catch (e: Exception) {
                        plugin.localizationManager.severe("logs.servercommunication.plot_delete_error",
                            "%error%" to e.message.toString())
                        e.printStackTrace()
                    }
                }
                MessageType.BLOCK_CHANGE -> {
                    try {
                        val blockData = gson.fromJson(message.data.toString(), BlockChangeData::class.java)
                        if (blockData != null) {
                            blockHandler.handleBlockChange(blockData)
                        } else {
                            plugin.localizationManager.severe("logs.servercommunication.deserialize_block_failed")
                        }
                    } catch (e: Exception) {
                        plugin.localizationManager.severe("logs.servercommunication.block_change_error",
                            "%error%" to e.message.toString())
                        e.printStackTrace()
                    }
                }
                MessageType.REQUEST_PLOT_BLOCKS -> {
                    try {
                        blockHandler.handleRequestPlotBlocks(message.data.toString())
                    } catch (e: Exception) {
                        plugin.localizationManager.severe("logs.servercommunication.request_plot_blocks_error",
                            "%error%" to e.message.toString())
                        e.printStackTrace()
                    }
                }
                MessageType.PLOT_BLOCKS -> {
                    try {
                        val blocks = gson.fromJson(message.data.toString(), Array<PlotBlockData>::class.java)
                        blockHandler.handlePlotBlocks(blocks.toList())
                    } catch (e: Exception) {
                        plugin.localizationManager.severe("logs.servercommunication.plot_blocks_error",
                            "%error%" to e.message.toString())
                        e.printStackTrace()
                    }
                }
                MessageType.PLOT_SCAN_PROGRESS -> {
                    try {
                        val progress = gson.fromJson(message.data.toString(), PlotScanProgress::class.java)
                        blockHandler.handlePlotScanProgress(progress)
                    } catch (e: Exception) {
                        plugin.localizationManager.severe("logs.servercommunication.scan_progress_error",
                            "%error%" to e.message.toString())
                        e.printStackTrace()
                    }
                }
                MessageType.PLOT_SCAN_COMPLETE -> {
                    try {
                        blockHandler.handlePlotScanComplete(message.data.toString())
                    } catch (e: Exception) {
                        plugin.localizationManager.severe("logs.servercommunication.scan_complete_error",
                            "%error%" to e.message.toString())
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            plugin.localizationManager.severe("logs.servercommunication.message_processing_error",
                "%error%" to e.message.toString())
            e.printStackTrace()
        }
    }

    /**
     * Обработка очереди сообщений
     */
    private fun processMessageQueue() {
        val socket = clientSocket
        if (socket == null || !socket.isConnected || socket.isClosed) {
            return
        }

        try {
            val writer = java.io.PrintWriter(socket.getOutputStream(), true)

            var message = messageQueue.poll()
            while (message != null) {
                writer.println(message)
                message = messageQueue.poll()
            }
        } catch (e: Exception) {
            plugin.localizationManager.warning("logs.servercommunication.messages_sending_error",
                "%error%" to e.message.toString())
            e.printStackTrace()
            clientSocket = null
        }
    }

    /**
     * Отправка данных о создании плота
     */
    fun sendPlotCreation(plot: Plot) {
        try {
            val plotJson = gson.toJson(plot)
            plugin.localizationManager.info("logs.servercommunication.plot_sending",
                "%json%" to plotJson)
            val message = Message(MessageType.PLOT_CREATE, plotJson)
            messageQueue.add(gson.toJson(message))
        } catch (e: Exception) {
            plugin.localizationManager.severe("logs.servercommunication.plot_serialize_error",
                "%error%" to e.message.toString())
            e.printStackTrace()
        }
    }

    /**
     * Отправка данных об удалении плота
     */
    fun sendPlotDeletion(plotName: String) {
        val message = Message(MessageType.PLOT_DELETE, plotName)
        messageQueue.add(gson.toJson(message))
    }

    /**
     * Отправка данных об изменении блока
     */
    fun sendBlockChange(blockData: BlockChangeData) {
        try {
            val blockDataJson = gson.toJson(blockData)
            val message = Message(MessageType.BLOCK_CHANGE, blockDataJson)
            messageQueue.add(gson.toJson(message))
        } catch (e: Exception) {
            plugin.localizationManager.severe("logs.servercommunication.block_serialize_error",
                "%error%" to e.message.toString())
            e.printStackTrace()
        }
    }

    /**
     * Запрос блоков плота с основного сервера
     */
    fun requestPlotBlocks(plotName: String) {
        val message = Message(MessageType.REQUEST_PLOT_BLOCKS, plotName)
        messageQueue.add(gson.toJson(message))
        plugin.localizationManager.info("logs.servercommunication.requesting_blocks",
            "%plot%" to plotName)

        // Отображаем сообщение для игроков на тестовом сервере
        if (plugin.serverType == ServerType.TEST) {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                Bukkit.broadcastMessage(plugin.localizationManager.getMessage("messages.servercommunication.loading_blocks_start",
                    "%plot%" to plotName))
            })
        }
    }

    /**
     * Отправка прогресса сканирования
     */
    fun sendPlotScanProgress(progress: PlotScanProgress) {
        val message = Message(MessageType.PLOT_SCAN_PROGRESS, gson.toJson(progress))
        messageQueue.add(gson.toJson(message))
    }

    /**
     * Отправка сигнала о завершении копирования
     */
    fun sendPlotScanComplete(plotName: String) {
        val message = Message(MessageType.PLOT_SCAN_COMPLETE, plotName)
        messageQueue.add(gson.toJson(message))
    }

    /**
     * Асинхронная отправка блоков с отложенной сериализацией
     */
    fun sendPlotBlocksAsync(blocks: List<PlotBlockData>) {
        executor.submit {
            try {
                val blockDataJson = gson.toJson(blocks)
                val message = Message(MessageType.PLOT_BLOCKS, blockDataJson)
                messageQueue.add(gson.toJson(message))
            } catch (e: Exception) {
                // Игнорируем ошибки для обеспечения стабильности
            }
        }
    }

    /**
     * Телепортирует игрока в мир плота
     */
    fun teleportToPlot(player: Player, plotName: String): Boolean {
        return plotHandler.teleportToPlot(player, plotName)
    }
}