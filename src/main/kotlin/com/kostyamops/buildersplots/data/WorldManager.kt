package com.kostyamops.buildersplots.data

import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.ServerType
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class WorldManager(val plugin: BuildersPlots, val plotManager: PlotManager) {

    val plotsWorldsFolder: File
        get() = File(plugin.dataFolder, "plots")

    private val worldUnloadDelay = plugin.config.getLong("world-unload-delay", 5)
    private val worldUnloadTasks = ConcurrentHashMap<String, BukkitTask>()

    fun saveAllPlotWorlds() {
        if (plugin.serverType != ServerType.TEST) return

        var savedCount = 0
        for (plot in plotManager.getAllPlots()) {
            val worldName = plot.getTestWorldName(plugin)
            val world = Bukkit.getWorld(worldName)

            if (world != null) {
                try {
                    world.save()

                    val rootWorldFolder = File(Bukkit.getWorldContainer(), worldName)
                    val pluginWorldFolder = File(plotsWorldsFolder, worldName)

                    if (rootWorldFolder.exists() && rootWorldFolder.isDirectory) {
                        if (!pluginWorldFolder.exists()) {
                            FileUtils.moveWorldFolder(rootWorldFolder, pluginWorldFolder)
                        } else {
                            FileUtils.deleteDirectory(pluginWorldFolder)
                            FileUtils.copyDirectory(rootWorldFolder, pluginWorldFolder)
                        }
                        plugin.localizationManager.info("plotmanager.world.moved.to.plugin",
                            "%name%" to worldName)
                    }

                    savedCount++
                } catch (e: Exception) {
                    plugin.localizationManager.severe("plotmanager.world.save.error",
                        "%name%" to worldName, "%error%" to e.message.toString())
                    e.printStackTrace()
                }
            }
        }

        if (savedCount > 0) {
            plugin.localizationManager.info("plotmanager.worlds.saved",
                "%count%" to savedCount.toString())
        }
    }

    fun loadAllPlotWorlds() {
        if (plugin.serverType != ServerType.TEST) return

        plugin.localizationManager.info("plotmanager.worlds.loading")

        var loadedCount = 0
        for (plot in plotManager.getAllPlots()) {
            try {
                val world = loadPlotWorld(plot.name)
                if (world != null) {
                    loadedCount++
                }
            } catch (e: Exception) {
                plugin.localizationManager.severe("plotmanager.world.load.error",
                    "%name%" to plot.name, "%error%" to e.message.toString())
                e.printStackTrace()
            }
        }

        plugin.localizationManager.info("plotmanager.worlds.loaded",
            "%count%" to loadedCount.toString())
    }

    fun loadPlotWorld(plotName: String): World? {
        val plot = plotManager.getPlot(plotName) ?: return null
        val worldName = plot.getTestWorldName(plugin)

        val existingWorld = Bukkit.getWorld(worldName)
        if (existingWorld != null) {
            plugin.localizationManager.info("plotmanager.world.already.loaded",
                "%name%" to worldName)
            return existingWorld
        }

        val worldFolder = File(plotsWorldsFolder, worldName)
        if (!worldFolder.exists() || !worldFolder.isDirectory) {
            val rootWorldFolder = File(Bukkit.getWorldContainer(), worldName)
            if (rootWorldFolder.exists() && rootWorldFolder.isDirectory) {
                plugin.localizationManager.info("plotmanager.world.found.in.root",
                    "%name%" to worldName)

                try {
                    if (!plotsWorldsFolder.exists()) {
                        plotsWorldsFolder.mkdirs()
                    }

                    FileUtils.moveWorldFolder(rootWorldFolder, worldFolder)
                    plugin.localizationManager.info("plotmanager.world.moved.success",
                        "%name%" to worldName)
                } catch (e: Exception) {
                    plugin.localizationManager.severe("plotmanager.world.move.error",
                        "%name%" to worldName, "%error%" to e.message.toString())
                    e.printStackTrace()
                    return null
                }
            } else {
                plugin.localizationManager.warning("plotmanager.world.folder.not.exists",
                    "%name%" to worldName)
                return null
            }
        }

        plugin.localizationManager.info("plotmanager.world.loading",
            "%name%" to worldName, "%path%" to worldFolder.absolutePath)

        try {
            val originalWorldContainer = System.getProperty("org.bukkit.worldContainer")
            System.setProperty("org.bukkit.worldContainer", plotsWorldsFolder.absolutePath)

            val creator = WorldCreator(worldName)
                .generator(com.kostyamops.buildersplots.world.EmptyWorldGenerator())
                .environment(World.Environment.NORMAL)
                .generateStructures(false)

            val world = creator.createWorld()

            if (originalWorldContainer != null) {
                System.setProperty("org.bukkit.worldContainer", originalWorldContainer)
            } else {
                System.clearProperty("org.bukkit.worldContainer")
            }

            if (world != null) {
                plugin.localizationManager.info("plotmanager.world.loaded.success",
                    "%name%" to worldName, "%path%" to worldFolder.absolutePath)

                world.difficulty = org.bukkit.Difficulty.PEACEFUL
                world.time = 6000
                world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false)
                world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false)
                world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false)
                world.setGameRule(org.bukkit.GameRule.DO_FIRE_TICK, false)
                world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, true)

                return world
            } else {
                plugin.localizationManager.severe("plotmanager.world.create.failed",
                    "%name%" to worldName)
                return null
            }
        } catch (e: Exception) {
            plugin.localizationManager.severe("plotmanager.world.load.error",
                "%name%" to worldName, "%error%" to e.message.toString())
            e.printStackTrace()
            return null
        }
    }

    fun unloadAllPlotWorlds() {
        if (plugin.serverType != ServerType.TEST) return

        plugin.localizationManager.info("plotmanager.worlds.unloading")

        var unloadedCount = 0
        for (plot in plotManager.getAllPlots()) {
            val worldName = plot.getTestWorldName(plugin)
            val world = Bukkit.getWorld(worldName)

            if (world != null) {
                try {
                    world.save()

                    val rootWorldFolder = File(Bukkit.getWorldContainer(), worldName)
                    val pluginWorldFolder = File(plotsWorldsFolder, worldName)

                    if (rootWorldFolder.exists() && rootWorldFolder.isDirectory) {
                        if (!pluginWorldFolder.exists()) {
                            FileUtils.moveWorldFolder(rootWorldFolder, pluginWorldFolder)
                        } else {
                            FileUtils.deleteDirectory(pluginWorldFolder)
                            FileUtils.copyDirectory(rootWorldFolder, pluginWorldFolder)
                        }
                    }

                    val defaultWorld = Bukkit.getWorlds()[0]
                    world.players.forEach { player ->
                        player.teleport(defaultWorld.spawnLocation)
                        plugin.localizationManager.sendMessage(player, "messages.plotmanager.world.unloading.teleport")
                    }

                    val success = Bukkit.unloadWorld(world, true)
                    if (success) {
                        plugin.localizationManager.info("plotmanager.world.unloaded",
                            "%name%" to worldName)
                        unloadedCount++
                    } else {
                        plugin.localizationManager.warning("plotmanager.world.unload.failed",
                            "%name%" to worldName)
                    }
                } catch (e: Exception) {
                    plugin.localizationManager.severe("plotmanager.world.unload.error",
                        "%name%" to worldName, "%error%" to e.message.toString())
                    e.printStackTrace()
                }
            }
        }

        plugin.localizationManager.info("plotmanager.worlds.unloaded",
            "%count%" to unloadedCount.toString())
    }

    fun ensurePlotWorldLoaded(plotName: String): Boolean {
        val world = Bukkit.getWorld("plot_${plotName.lowercase().replace(" ", "_")}")

        if (world != null) {
            return true
        }

        return loadPlotWorld(plotName) != null
    }

    fun playerEnteredPlotWorld(worldName: String) {
        cancelUnloadTask(worldName)
    }

    fun playerLeftPlotWorld(worldName: String) {
        val world = Bukkit.getWorld(worldName) ?: return

        if (world.players.isEmpty()) {
            scheduleWorldUnload(worldName)
        }
    }

    private fun scheduleWorldUnload(worldName: String) {
        cancelUnloadTask(worldName)

        if (worldUnloadDelay <= 0) return

        val task = object : BukkitRunnable() {
            override fun run() {
                val world = Bukkit.getWorld(worldName)
                if (world != null && world.players.isEmpty()) {
                    try {
                        world.save()

                        val rootWorldFolder = File(Bukkit.getWorldContainer(), worldName)
                        val pluginWorldFolder = File(plotsWorldsFolder, worldName)

                        if (rootWorldFolder.exists() && rootWorldFolder.isDirectory) {
                            if (!pluginWorldFolder.exists()) {
                                FileUtils.moveWorldFolder(rootWorldFolder, pluginWorldFolder)
                            } else {
                                FileUtils.deleteDirectory(pluginWorldFolder)
                                FileUtils.copyDirectory(rootWorldFolder, pluginWorldFolder)
                            }
                        }

                        val success = Bukkit.unloadWorld(world, true)
                        if (success) {
                            plugin.localizationManager.info("plotmanager.world.unloaded.empty",
                                "%name%" to worldName)
                        } else {
                            plugin.localizationManager.warning("plotmanager.world.unload.empty.failed",
                                "%name%" to worldName)
                        }
                    } catch (e: Exception) {
                        plugin.localizationManager.severe("plotmanager.world.unload.error",
                            "%name%" to worldName, "%error%" to e.message.toString())
                        e.printStackTrace()
                    }
                }

                worldUnloadTasks.remove(worldName)
            }
        }.runTaskLater(plugin, worldUnloadDelay * 60 * 20) // минуты в тики

        worldUnloadTasks[worldName] = task

        plugin.localizationManager.info("plotmanager.world.unload.scheduled",
            "%name%" to worldName, "%delay%" to worldUnloadDelay.toString())
    }

    fun cancelUnloadTask(worldName: String) {
        val task = worldUnloadTasks.remove(worldName)
        task?.cancel()
    }

    fun cancelAllUnloadTasks() {
        worldUnloadTasks.values.forEach { it.cancel() }
        worldUnloadTasks.clear()
    }
}