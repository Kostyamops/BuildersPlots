package com.kostyamops.buildersplots.network.handlers

import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.ServerType
import com.kostyamops.buildersplots.data.Plot
import com.kostyamops.buildersplots.network.ServerCommunicationManager
import com.kostyamops.buildersplots.world.EmptyWorldGenerator
import org.bukkit.*
import org.bukkit.entity.Player
import java.io.File

/**
 * Обработчик операций с плотами
 */
class PlotHandler(
    private val communicationManager: ServerCommunicationManager,
    private val plugin: BuildersPlots
) {

    /**
     * Обработка создания плота
     */
    fun handlePlotCreation(plot: Plot) {
        // Add the plot to our manager
        plugin.plotManager.addOrUpdatePlot(plot)

        // If we're the test server, create a new world for this plot
        if (plugin.serverType == ServerType.TEST) {
            val worldName = plot.getTestWorldName()

            // Check if world already exists
            if (Bukkit.getWorld(worldName) != null) {
                plugin.localizationManager.info("logs.plothandler.world_exists",
                    "%name%" to worldName)
                return
            }

            plugin.localizationManager.info("logs.plothandler.creating_world",
                "%plot%" to plot.name)

            // На Purpur используем синхронный планировщик
            Bukkit.getScheduler().runTask(plugin, Runnable {
                try {
                    // Получаем папку для мира плота
                    val plotWorldFolder = File(communicationManager.plotsWorldsFolder, worldName)
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
                        plugin.localizationManager.warning("logs.plothandler.reflection_failed",
                            "%error%" to e.message.toString())
                        System.setProperty("org.bukkit.worldContainer", communicationManager.plotsWorldsFolder.absolutePath)
                    }

                    // Создаем мир
                    val world = creator.createWorld()

                    if (world != null) {
                        plugin.localizationManager.info("logs.plothandler.world_created",
                            "%name%" to worldName)

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
                                plugin.localizationManager.info("logs.plothandler.spawn_set",
                                    "%x%" to plot.center.x.toString(),
                                    "%y%" to plot.center.y.toString(),
                                    "%z%" to plot.center.z.toString())

                                plugin.localizationManager.info("logs.plothandler.world_configured",
                                    "%name%" to worldName)

                                // Сразу запрашиваем копирование блоков с основного сервера
                                plugin.localizationManager.info("logs.plothandler.requesting_blocks",
                                    "%plot%" to plot.name)
                                communicationManager.requestPlotBlocks(plot.name)

                            } catch (e: Exception) {
                                plugin.localizationManager.severe("logs.plothandler.configure_failed",
                                    "%error%" to e.message.toString())
                                e.printStackTrace()
                            }
                        })
                    } else {
                        // Если создание мира не удалось
                        plugin.localizationManager.severe("logs.plothandler.create_world_failed",
                            "%name%" to worldName)
                    }

                } catch (e: Exception) {
                    plugin.localizationManager.severe("logs.plothandler.create_world_error",
                        "%error%" to e.message.toString())
                    e.printStackTrace()
                }
            })
        }
    }

    /**
     * Обработка удаления плота
     */
    fun handlePlotDeletion(plotName: String) {
        // Remove the plot from our manager
        plugin.plotManager.deletePlot(plotName)

        // If we're the test server, unload and delete the world
        if (plugin.serverType == ServerType.TEST) {
            val plot = plugin.plotManager.getPlot(plotName)
            if (plot == null) {
                plugin.localizationManager.warning("logs.plothandler.plot_not_found_deletion",
                    "%name%" to plotName)
                return
            }

            val worldName = plot.getTestWorldName()
            val world = Bukkit.getWorld(worldName)
            if (world == null) {
                plugin.localizationManager.warning("logs.plothandler.world_not_found_deletion",
                    "%name%" to worldName)
                return
            }

            // Используем синхронный планировщик для Purpur
            Bukkit.getScheduler().runTask(plugin, Runnable {
                try {
                    // Телепортируем игроков в другой мир
                    for (player in world.players) {
                        player.teleport(Bukkit.getWorlds()[0].spawnLocation)
                        plugin.localizationManager.sendMessage(player, "messages.plothandler.plot_deleted_teleport")
                    }

                    // Выгружаем мир
                    val success = Bukkit.unloadWorld(world, false)
                    if (success) {
                        plugin.localizationManager.info("logs.plothandler.world_unloaded",
                            "%name%" to worldName)

                        // Удаляем папку мира
                        val worldFolder = File(communicationManager.plotsWorldsFolder, worldName)
                        if (worldFolder.exists()) {
                            if (deleteDirectory(worldFolder)) {
                                plugin.localizationManager.info("logs.plothandler.world_folder_deleted",
                                    "%name%" to worldName)
                            } else {
                                plugin.localizationManager.warning("logs.plothandler.delete_folder_failed",
                                    "%name%" to worldName)
                            }
                        }
                    } else {
                        plugin.localizationManager.warning("logs.plothandler.unload_world_failed",
                            "%name%" to worldName)
                    }
                } catch (e: Exception) {
                    plugin.localizationManager.severe("logs.plothandler.world_unload_error",
                        "%error%" to e.message.toString())
                    e.printStackTrace()
                }
            })
        }
    }

    /**
     * Рекурсивное удаление директории
     */
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

    /**
     * Телепортация игрока на плот
     */
    fun teleportToPlot(player: Player, plotName: String): Boolean {
        if (plugin.serverType != ServerType.TEST) {
            plugin.localizationManager.sendMessage(player, "messages.plothandler.teleport_test_only")
            return false
        }

        val plot = plugin.plotManager.getPlot(plotName)
        if (plot == null) {
            plugin.localizationManager.sendMessage(player, "messages.plothandler.plot_not_found",
                "%name%" to plotName)
            return false
        }

        val worldName = plot.getTestWorldName()
        val world = Bukkit.getWorld(worldName)
        if (world == null) {
            plugin.localizationManager.sendMessage(player, "messages.plothandler.plot_world_not_found",
                "%name%" to plotName)
            return false
        }

        plugin.localizationManager.sendMessage(player, "messages.plothandler.teleporting",
            "%name%" to plotName)
        player.teleport(world.spawnLocation)
        return true
    }
}