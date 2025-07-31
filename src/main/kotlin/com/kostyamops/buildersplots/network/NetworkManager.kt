package com.kostyamops.buildersplots.network

import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.network.packets.BlockChangePacket
import com.kostyamops.buildersplots.network.packets.Packet
import com.kostyamops.buildersplots.network.packets.PlotManagementPacket
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import java.util.logging.Level
import java.util.UUID

class NetworkManager(private val plugin: BuildersPlots) {

    fun sendBlockChange(world: String, x: Int, y: Int, z: Int, material: Material, blockData: BlockData?) {
        if (!plugin.configManager.isMainServer) {
            return // Only main server sends block changes
        }

        val packet = BlockChangePacket(
            world = world,
            x = x,
            y = y,
            z = z,
            material = material.name,
            blockData = blockData?.asString ?: ""
        )

        plugin.serverConnection.sendPacket(packet)
        plugin.log("Sent block change at $world ($x,$y,$z): ${material.name}")
    }

    fun handleBlockChange(packet: BlockChangePacket) {
        if (plugin.configManager.isMainServer) {
            return // Only test server processes block changes
        }

        // Apply the block change to all affected plots
        applyBlockChangeToPlots(packet)
    }

    private fun applyBlockChangeToPlots(packet: BlockChangePacket) {
        // Find all plots that correspond to this original world location
        val affectedPlots = plugin.plotManager.getAllPlots().filter {
            it.originalWorld == packet.world && it.syncEnabled
        }

        if (affectedPlots.isEmpty()) return

        // Check if the block is within any of the plots' areas
        val blockX = packet.x
        val blockZ = packet.z

        val material = try {
            Material.valueOf(packet.material)
        } catch (e: IllegalArgumentException) {
            plugin.log("Unknown material: ${packet.material}", Level.WARNING)
            return
        }

        for (plot in affectedPlots) {
            // Check if the block is within this plot's original area
            val minX = plot.centerX - plot.radius
            val maxX = plot.centerX + plot.radius
            val minZ = plot.centerZ - plot.radius
            val maxZ = plot.centerZ + plot.radius

            if (blockX in minX..maxX && blockZ in minZ..maxZ) {
                // Calculate the corresponding location in the plot world
                val plotX = blockX - minX
                val plotZ = blockZ - minZ

                // Get the plot world
                val plotWorld = Bukkit.getWorld(plot.worldName) ?: continue

                // Apply the change to the plot world
                val blockData = if (packet.blockData.isNotEmpty()) {
                    try {
                        Bukkit.createBlockData(packet.blockData)
                    } catch (e: Exception) {
                        plugin.log("Invalid block data: ${packet.blockData}", Level.WARNING)
                        null
                    }
                } else null

                // Schedule the block change in the plot world using Folia's API
                // For Folia 1.21.4, we'll use the global scheduler and specify the region
                val loc = Location(plotWorld, plotX.toDouble(), packet.y.toDouble(), plotZ.toDouble())

                // Use Folia's ThreadedRegionizer API
                Bukkit.getServer().scheduler.runTask(plugin, Runnable {
                    try {
                        if (blockData != null) {
                            plotWorld.getBlockAt(plotX, packet.y, plotZ).blockData = blockData
                        } else {
                            plotWorld.getBlockAt(plotX, packet.y, plotZ).type = material
                        }
                        plugin.log("Applied block change to plot ${plot.name} at ($plotX,${packet.y},$plotZ): ${material.name}")
                    } catch (e: Exception) {
                        plugin.log("Error applying block change: ${e.message}", Level.WARNING)
                    }
                })
            }
        }
    }

    fun sendPlotManagement(action: String, plotData: Map<String, Any>) {
        val packet = PlotManagementPacket(action, plotData)
        plugin.serverConnection.sendPacket(packet)
        plugin.log("Sent plot management packet: $action")
    }

    // Метод для запроса начальной синхронизации плота
    fun requestInitialSync(plotId: UUID) {
        val plot = plugin.plotManager.getPlot(plotId)
        if (plot == null) {
            plugin.log("Ошибка: Плот с ID $plotId не найден", Level.WARNING)
            return
        }

        val minX = plot.centerX - plot.radius
        val maxX = plot.centerX + plot.radius
        val minZ = plot.centerZ - plot.radius
        val maxZ = plot.centerZ + plot.radius

        // Высота от минимума до максимума мира
        val minY = 0
        val maxY = 255

        val plotData = mapOf(
            "plotId" to plotId.toString(),
            "originalWorld" to plot.originalWorld,
            "minX" to minX,
            "maxX" to maxX,
            "minY" to minY,
            "maxY" to maxY,
            "minZ" to minZ,
            "maxZ" to maxZ
        )

        sendPlotManagement("INITIAL_SYNC", plotData)
        plugin.log("Запрошена начальная синхронизация для плота ${plot.name} (ID: $plotId)")
    }

    fun handlePlotManagement(packet: PlotManagementPacket) {
        plugin.log("Получен пакет управления плотом: ${packet.action}")
        plugin.log("Данные пакета: ${packet.data}")

        when (packet.action) {
            "CREATE" -> {
                // Handle plot creation
                val name = packet.data["name"] as? String ?: return
                val originalWorld = packet.data["originalWorld"] as? String ?: return
                val centerX = packet.data["centerX"] as? Int ?: return
                val centerZ = packet.data["centerZ"] as? Int ?: return
                val radius = packet.data["radius"] as? Int ?: return
                val creatorUuid = java.util.UUID.fromString(packet.data["creatorUuid"] as? String ?: return)

                plugin.plotManager.createPlot(name, originalWorld, centerX, centerZ, radius, creatorUuid)
                plugin.log("Created plot from network request: $name")
            }
            "DELETE" -> {
                // Handle plot deletion
                val plotId = java.util.UUID.fromString(packet.data["plotId"] as? String ?: return)
                plugin.plotManager.deletePlot(plotId)
                plugin.log("Deleted plot from network request: $plotId")
            }
            "ADD_MEMBER" -> {
                // Handle adding member
                val plotId = java.util.UUID.fromString(packet.data["plotId"] as? String ?: return)
                val playerUuid = java.util.UUID.fromString(packet.data["playerUuid"] as? String ?: return)
                plugin.plotManager.addMemberToPlot(plotId, playerUuid)
                plugin.log("Added member to plot from network request: $playerUuid to $plotId")
            }
            "REMOVE_MEMBER" -> {
                // Handle removing member
                val plotId = java.util.UUID.fromString(packet.data["plotId"] as? String ?: return)
                val playerUuid = java.util.UUID.fromString(packet.data["playerUuid"] as? String ?: return)
                plugin.plotManager.removeMemberFromPlot(plotId, playerUuid)
                plugin.log("Removed member from plot from network request: $playerUuid from $plotId")
            }
            "DISABLE_SYNC" -> {
                // Handle disabling sync
                val plotId = java.util.UUID.fromString(packet.data["plotId"] as? String ?: return)
                plugin.plotManager.disablePlotSync(plotId)
                plugin.log("Disabled sync for plot from network request: $plotId")
            }
            "INITIAL_SYNC" -> {
                try {
                    // Отладочное логирование
                    plugin.log("Обрабатываем запрос на начальную синхронизацию")
                    plugin.log("Содержимое пакета: ${packet.data}")

                    // Обработка запроса на начальную синхронизацию
                    val plotIdStr = packet.data["plotId"] as? String
                    if (plotIdStr == null) {
                        plugin.log("Ошибка: plotId отсутствует в пакете", Level.WARNING)
                        return
                    }

                    val plotId = try {
                        UUID.fromString(plotIdStr)
                    } catch (e: Exception) {
                        plugin.log("Ошибка: Неверный формат plotId: $plotIdStr", Level.WARNING)
                        return
                    }

                    val originalWorld = packet.data["originalWorld"] as? String
                    if (originalWorld == null) {
                        plugin.log("Ошибка: originalWorld отсутствует в пакете", Level.WARNING)
                        return
                    }

                    // Преобразуем числовые значения, учитывая разные возможные типы (Int, Double, Long)
                    val minX = convertToInt(packet.data["minX"]) ?: run {
                        plugin.log("Ошибка: minX отсутствует или имеет неверный формат", Level.WARNING)
                        return
                    }

                    val maxX = convertToInt(packet.data["maxX"]) ?: run {
                        plugin.log("Ошибка: maxX отсутствует или имеет неверный формат", Level.WARNING)
                        return
                    }

                    val minZ = convertToInt(packet.data["minZ"]) ?: run {
                        plugin.log("Ошибка: minZ отсутствует или имеет неверный формат", Level.WARNING)
                        return
                    }

                    val maxZ = convertToInt(packet.data["maxZ"]) ?: run {
                        plugin.log("Ошибка: maxZ отсутствует или имеет неверный формат", Level.WARNING)
                        return
                    }

                    val minY = convertToInt(packet.data["minY"]) ?: run {
                        plugin.log("Ошибка: minY отсутствует или имеет неверный формат", Level.WARNING)
                        return
                    }

                    val maxY = convertToInt(packet.data["maxY"]) ?: run {
                        plugin.log("Ошибка: maxY отсутствует или имеет неверный формат", Level.WARNING)
                        return
                    }

                    // Запускаем синхронизацию блоков
                    plugin.log("Запускаем syncAreaFromMainServer с параметрами:")
                    plugin.log("plotId=$plotId, world=$originalWorld")
                    plugin.log("minX=$minX, maxX=$maxX, minY=$minY, maxY=$maxY, minZ=$minZ, maxZ=$maxZ")

                    syncAreaFromMainServer(plotId, originalWorld, minX, maxX, minY, maxY, minZ, maxZ)
                    plugin.log("Начата начальная синхронизация для плота $plotId")
                } catch (e: Exception) {
                    plugin.log("Ошибка при обработке пакета INITIAL_SYNC: ${e.message}", Level.SEVERE)
                    e.printStackTrace()
                }
            }
            else -> plugin.log("Unknown plot management action: ${packet.action}")
        }
    }

    // Вспомогательный метод для безопасного преобразования числовых значений
    private fun convertToInt(value: Any?): Int? {
        return when (value) {
            is Int -> value
            is Double -> value.toInt()
            is Long -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private fun syncAreaFromMainServer(plotId: UUID, originalWorld: String, minX: Int, maxX: Int, minY: Int, maxY: Int, minZ: Int, maxZ: Int) {
        // Получаем плот по ID
        val plot = plugin.plotManager.getPlot(plotId)
        if (plot == null) {
            plugin.log("Ошибка синхронизации: Плот с ID $plotId не найден", Level.WARNING)
            return
        }

        // Получаем миры
        val sourceWorld = Bukkit.getWorld(originalWorld)
        if (sourceWorld == null) {
            plugin.log("Ошибка синхронизации: Мир $originalWorld не найден", Level.WARNING)
            return
        }

        val targetWorld = Bukkit.getWorld(plot.worldName)
        if (targetWorld == null) {
            plugin.log("Ошибка синхронизации: Мир плота ${plot.worldName} не найден", Level.WARNING)
            return
        }

        plugin.log("Начинаем копирование блоков из $originalWorld ($minX,$minY,$minZ - $maxX,$maxY,$maxZ) в ${plot.worldName}")

        // Создаем задачу для асинхронного копирования
        Thread {
            try {
                var blocksCopied = 0
                val totalBlocks = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1)
                val startTime = System.currentTimeMillis()

                // Для тестирования - создаем простой узор из блоков
                plugin.log("ТЕСТИРОВАНИЕ: Создаем простой узор из блоков")

                // Копируем только небольшую область для начала, чтобы увидеть результат
                val testSize = 10 // 10x10 блоков

                for (relX in 0 until testSize) {
                    for (relZ in 0 until testSize) {
                        val y = 0 // На уровне земли

                        // Создаем шахматный узор
                        val material = if ((relX + relZ) % 2 == 0) Material.STONE else Material.GOLD_BLOCK

                        plugin.server.globalRegionScheduler.execute(plugin, Runnable {
                            try {
                                val targetBlock = targetWorld.getBlockAt(relX, y, relZ)
                                targetBlock.type = material

                                blocksCopied++
                                if (blocksCopied % 10 == 0 || blocksCopied == testSize * testSize) {
                                    plugin.log("Тестовый узор: $blocksCopied блоков из ${testSize * testSize}")
                                }
                            } catch (e: Exception) {
                                plugin.log("Ошибка создания тестового узора: ${e.message}", Level.WARNING)
                            }
                        })

                        // Небольшая задержка между блоками
                        Thread.sleep(50)
                    }
                }

                plugin.log("Тестовый узор создан! Перейдите на координаты 0,0 в мире ${plot.worldName} для проверки.")

                // Реальное копирование блоков можно включить позже, когда тестовый узор будет работать
                /*
                // Проходим по всем блокам в указанной области
                for (x in minX..maxX) {
                    for (y in minY..maxY) {
                        for (z in minZ..maxZ) {
                            try {
                                // Получаем относительные координаты для плота
                                val relX = x - minX
                                val relZ = z - minZ

                                // Получаем тип блока и его данные
                                val block = sourceWorld.getBlockAt(x, y, z)
                                val material = block.type
                                val blockData = block.blockData.asString

                                // Копируем блок на тестовый сервер через глобальный планировщик
                                plugin.server.globalRegionScheduler.execute(plugin, Runnable {
                                    try {
                                        val targetBlock = targetWorld.getBlockAt(relX, y, relZ)
                                        targetBlock.blockData = Bukkit.createBlockData(blockData)

                                        blocksCopied++
                                        if (blocksCopied % 1000 == 0 || blocksCopied == totalBlocks) {
                                            val progress = (blocksCopied * 100.0) / totalBlocks
                                            val elapsed = (System.currentTimeMillis() - startTime) / 1000
                                            plugin.log("Синхронизация плота: ${progress.toInt()}% завершено ($blocksCopied/$totalBlocks блоков), прошло $elapsed сек.")
                                        }
                                    } catch (e: Exception) {
                                        plugin.log("Ошибка копирования блока на $relX,$y,$relZ: ${e.message}", Level.WARNING)
                                    }
                                })
                            } catch (e: Exception) {
                                plugin.log("Ошибка при обработке блока $x,$y,$z: ${e.message}", Level.WARNING)
                            }

                            // Небольшая задержка, чтобы не перегружать сервер
                            Thread.sleep(1)
                        }
                    }
                }
                */

                plugin.log("Операция синхронизации плота $plotId завершена.")
            } catch (e: Exception) {
                plugin.log("Критическая ошибка при синхронизации плота: ${e.message}", Level.SEVERE)
                e.printStackTrace()
            }
        }.start()
    }
}