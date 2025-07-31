package com.kostyamops.buildersplots.network

import com.kostyamops.buildersplots.BuildersPlots
import org.bukkit.Bukkit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// Менеджер синхронизации чанков
class ChunkSyncManager(private val plugin: BuildersPlots) {

    private val syncTasks = ConcurrentHashMap<UUID, Int>()

    fun startChunkBasedSync(
        plotId: UUID,
        originalWorld: String,
        minX: Int, maxX: Int,
        minY: Int, maxY: Int,
        minZ: Int, maxZ: Int
    ) {
        val plot = plugin.plotManager.getPlot(plotId) ?: return

        // Рассчитываем количество чанков (16x16x16 блоков)
        val chunkSize = 16
        val xChunks = (maxX - minX + 1 + chunkSize - 1) / chunkSize
        val yChunks = (maxY - minY + 1 + chunkSize - 1) / chunkSize
        val zChunks = (maxZ - minZ + 1 + chunkSize - 1) / chunkSize

        val totalChunks = xChunks * yChunks * zChunks
        var processedChunks = 0

        plugin.log("Начата синхронизация $totalChunks чанков для плота ${plot.name}")

        // Запускаем задачу с периодическим выполнением (каждые 5 тиков)
        val taskId = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (processedChunks >= totalChunks) {
                // Все чанки обработаны, отменяем задачу
                syncTasks[plotId]?.let { Bukkit.getScheduler().cancelTask(it) }
                syncTasks.remove(plotId)
                plugin.log("Синхронизация плота ${plot.name} завершена")
                return@Runnable
            }

            // Вычисляем текущие координаты чанка
            val chunkIndex = processedChunks
            val xChunk = (chunkIndex % xChunks)
            val yChunk = ((chunkIndex / xChunks) % yChunks)
            val zChunk = (chunkIndex / (xChunks * yChunks))

            // Координаты блоков в чанке
            val xStart = minX + xChunk * chunkSize
            val yStart = minY + yChunk * chunkSize
            val zStart = minZ + zChunk * chunkSize

            val xEnd = Math.min(xStart + chunkSize - 1, maxX)
            val yEnd = Math.min(yStart + chunkSize - 1, maxY)
            val zEnd = Math.min(zStart + chunkSize - 1, maxZ)

            // Синхронизируем чанк
            syncChunk(plotId, originalWorld, plot.worldName,
                xStart, xEnd, yStart, yEnd, zStart, zEnd,
                minX, minZ)

            processedChunks++

            if (processedChunks % 10 == 0 || processedChunks == totalChunks) {
                val progress = (processedChunks * 100.0) / totalChunks
                plugin.log("Синхронизация плота ${plot.name}: $progress% завершено ($processedChunks/$totalChunks чанков)")
            }
        }, 0L, 5L).taskId

        syncTasks[plotId] = taskId
    }

    private fun syncChunk(
        plotId: UUID,
        sourceWorldName: String,
        targetWorldName: String,
        xStart: Int, xEnd: Int,
        yStart: Int, yEnd: Int,
        zStart: Int, zEnd: Int,
        originX: Int, originZ: Int
    ) {
        val sourceWorld = Bukkit.getWorld(sourceWorldName) ?: return
        val targetWorld = Bukkit.getWorld(targetWorldName) ?: return

        for (x in xStart..xEnd) {
            for (y in yStart..yEnd) {
                for (z in zStart..zEnd) {
                    try {
                        // Рассчитываем относительные координаты
                        val relX = x - originX
                        val relZ = z - originZ

                        // Копируем блок
                        val sourceBlock = sourceWorld.getBlockAt(x, y, z)
                        val targetBlock = targetWorld.getBlockAt(relX, y, relZ)

                        // Применяем изменения (в основном потоке)
                        targetBlock.blockData = sourceBlock.blockData
                    } catch (e: Exception) {
                        plugin.log("Ошибка при синхронизации блока: ${e.message}", java.util.logging.Level.WARNING)
                    }
                }
            }
        }
    }

    fun cancelSync(plotId: UUID) {
        syncTasks[plotId]?.let {
            Bukkit.getScheduler().cancelTask(it)
            plugin.log("Синхронизация плота $plotId отменена")
        }
        syncTasks.remove(plotId)
    }
}