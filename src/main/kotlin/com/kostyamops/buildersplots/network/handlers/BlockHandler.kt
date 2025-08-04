package com.kostyamops.buildersplots.network.handlers

import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.ServerType
import com.kostyamops.buildersplots.data.Plot
import com.kostyamops.buildersplots.network.ServerCommunicationManager
import com.kostyamops.buildersplots.network.model.BlockChangeData
import com.kostyamops.buildersplots.network.model.PlotBlockData
import com.kostyamops.buildersplots.network.model.PlotScanProgress
import org.bukkit.Bukkit
import org.bukkit.Material
import java.util.ArrayList
import java.util.Collections

/**
 * Обработчик операций с блоками
 */
class BlockHandler(
    private val communicationManager: ServerCommunicationManager,
    private val plugin: BuildersPlots
) {

    /**
     * Обработка изменения блока
     */
    fun handleBlockChange(blockData: BlockChangeData) {
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

    /**
     * Обработка запроса блоков на основном сервере
     */
    fun handleRequestPlotBlocks(plotName: String) {
        if (plugin.serverType != ServerType.MAIN) {
            plugin.localizationManager.warning("logs.blockhandler.not_main_server")
            return
        }

        plugin.localizationManager.info("logs.blockhandler.processing_request",
            "%plot%" to plotName)

        val plot = plugin.plotManager.getPlot(plotName) ?: run {
            plugin.localizationManager.warning("logs.blockhandler.plot_not_found",
                "%name%" to plotName)
            return
        }

        // Запускаем сканирование и отправку блоков
        scanAndSendPlotBlocks(plot)
    }

    /**
     * Сканирование и отправка блоков плота
     */
    private fun scanAndSendPlotBlocks(plot: Plot) {
        val minLoc = plot.getMinLocation()
        val maxLoc = plot.getMaxLocation()
        val world = Bukkit.getWorld(minLoc.world) ?: return

        // Инициализируем структуры для отслеживания прогресса
        val plotChunks = mutableListOf<Pair<Int, Int>>()
        communicationManager.scannedChunks[plot.name] = Collections.synchronizedSet(mutableSetOf())

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

        communicationManager.totalChunks[plot.name] = plotChunks.size

        // Параметры сканирования
        val batchSize = 2000
        val delayBetweenBatches = 1L
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

                                                // Если набрали достаточно блоков, отправляем пакет
                                                if (blockBatch.size >= batchSize) {
                                                    totalBlocksSent += blockBatch.size
                                                    communicationManager.sendPlotBlocksAsync(ArrayList(blockBatch))
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
                                    communicationManager.sendPlotBlocksAsync(ArrayList(blockBatch))
                                    blockBatch.clear()
                                }

                                // Чанк полностью обработан, обновляем статус
                                synchronized(communicationManager.scannedChunks) {
                                    communicationManager.scannedChunks[plot.name]?.add(Pair(chunkX, chunkZ))
                                    val scanned = communicationManager.scannedChunks[plot.name]?.size ?: 0
                                    val total = communicationManager.totalChunks[plot.name] ?: 1
                                    val progress = PlotScanProgress(plot.name, scanned, total)
                                    communicationManager.sendPlotScanProgress(progress)

                                    // Если все чанки обработаны, завершаем процесс
                                    if (scanned >= total) {
                                        communicationManager.sendPlotScanComplete(plot.name)
                                        communicationManager.scannedChunks.remove(plot.name)
                                        communicationManager.totalChunks.remove(plot.name)
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
     * Обработка партии блоков на тестовом сервере
     */
    fun handlePlotBlocks(blocks: List<PlotBlockData>) {
        if (plugin.serverType != ServerType.TEST) {
            plugin.localizationManager.warning("logs.blockhandler.not_test_server")
            return
        }

        if (blocks.isEmpty()) return

        val plotName = blocks[0].plotName
        val plot = plugin.plotManager.getPlot(plotName) ?: run {
            plugin.localizationManager.warning("logs.blockhandler.plot_not_found_for_blocks",
                "%name%" to plotName)
            return
        }

        val worldName = plot.getTestWorldName()
        val world = Bukkit.getWorld(worldName) ?: run {
            plugin.localizationManager.warning("logs.blockhandler.world_not_found",
                "%name%" to worldName)
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
                        plugin.localizationManager.warning("logs.blockhandler.block_set_error",
                            "%error%" to e.message.toString())
                    }
                }
            } catch (e: Exception) {
                plugin.localizationManager.severe("logs.blockhandler.batch_error",
                    "%error%" to e.message.toString())
                e.printStackTrace()
            }
        })
    }

    /**
     * Обработка информации о прогрессе сканирования
     */
    fun handlePlotScanProgress(progress: PlotScanProgress) {
        if (plugin.serverType != ServerType.TEST) return

        val percent = progress.getPercentComplete()

        plugin.localizationManager.info("logs.blockhandler.scan_progress",
            "%scanned%" to progress.scannedChunks.toString(),
            "%total%" to progress.totalChunks.toString(),
            "%percent%" to percent.toString(),
            "%plot%" to progress.plotName)

        // Отображаем прогресс для игроков
        Bukkit.getScheduler().runTask(plugin, Runnable {
            // Отправляем только игрокам в мире плота
            val worldName = "plot_${progress.plotName.lowercase().replace(" ", "_")}"
            val world = Bukkit.getWorld(worldName)

            if (world != null) {
                for (player in world.players) {
                    player.spigot().sendMessage(
                        net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        net.md_5.bungee.api.chat.TextComponent(
                            plugin.localizationManager.getMessage("messages.blockhandler.copy_progress_actionbar",
                                "%percent%" to percent.toString())
                        )
                    )
                }
            }

            // И администраторам
            for (player in Bukkit.getOnlinePlayers()) {
                if (player.isOp || player.hasPermission("buildersplots.admin")) {
                    plugin.localizationManager.sendMessage(player, "messages.blockhandler.copy_progress_admin",
                        "%plot%" to progress.plotName, "%percent%" to percent.toString())
                }
            }
        })
    }

    /**
     * Завершение обработки всех блоков
     */
    fun handlePlotScanComplete(plotName: String) {
        if (plugin.serverType != ServerType.TEST) return

        plugin.localizationManager.info("logs.blockhandler.scan_complete",
            "%plot%" to plotName)

        val plot = plugin.plotManager.getPlot(plotName) ?: return
        val worldName = plot.getTestWorldName()
        val world = Bukkit.getWorld(worldName) ?: return

        // Финальная настройка мира
        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                world.time = 6000 // День

                // Уведомление для всех игроков
                Bukkit.broadcastMessage(plugin.localizationManager.getMessage("messages.blockhandler.copy_success",
                    "%plot%" to plotName))

                // Предложение телепортироваться админам и создателю
                for (player in Bukkit.getOnlinePlayers()) {
                    if (player.isOp || player.hasPermission("buildersplots.admin") ||
                        player.uniqueId.toString() == plot.creatorUUID.toString()) {

                        plugin.localizationManager.sendMessage(player, "messages.blockhandler.teleport_suggestion",
                            "%plot%" to plotName)
                    }
                }

                plugin.localizationManager.info("logs.blockhandler.copying_completed",
                    "%plot%" to plotName)
            } catch (e: Exception) {
                plugin.localizationManager.severe("logs.blockhandler.finalizing_error",
                    "%error%" to e.message.toString())
                e.printStackTrace()
            }
        })
    }
}