package com.kostyamops.buildersplots.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.ServerType
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlotManager(private val plugin: BuildersPlots) {

    private val plots = ConcurrentHashMap<String, Plot>()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val plotsFile = File(plugin.dataFolder, "plots.json")

    // Поля для управления автосохранением
    private var autoSaveTask: BukkitTask? = null
    private val plotsWorldsFolder: File
        get() = File(plugin.dataFolder, "plots")

    init {
        loadPlots()

        if (!plotsWorldsFolder.exists()) {
            plotsWorldsFolder.mkdirs()
            plugin.localizationManager.info("plotmanager.directory.created",
                "%path%" to plotsWorldsFolder.absolutePath)
        }
    }

    fun createPlot(name: String, radius: Int, player: Player): Plot? {
        if (plots.containsKey(name)) {
            return null
        }

        val maxRadius = plugin.config.getInt("plots.max-radius", 100)
        if (radius <= 0 || radius > maxRadius) {
            return null
        }

        val plotLocation = PlotLocation(player.location)
        val plot = Plot(name, radius, plotLocation, player.uniqueId)

        plots[name] = plot
        savePlots()

        if (plugin.serverType == ServerType.MAIN) {
            plugin.communicationManager.sendPlotCreation(plot)
        }

        plugin.localizationManager.info("plotmanager.plot.created",
            "%name%" to name, "%owner%" to player.name, "%radius%" to radius.toString())
        return plot
    }

    fun deletePlot(name: String): Boolean {
        val plot = plots[name] ?: return false

        if (plugin.serverType == ServerType.TEST) {
            val worldName = plot.getTestWorldName(plugin)

            if (Bukkit.isPrimaryThread()) {
                deleteWorldSafely(worldName, plot)
            } else {
                try {
                    val future = java.util.concurrent.CompletableFuture<Boolean>()

                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        try {
                            val result = deleteWorldSafely(worldName, plot)
                            future.complete(result)
                        } catch (e: Exception) {
                            plugin.localizationManager.severe("plotmanager.world.delete.error",
                                "%name%" to worldName, "%error%" to e.message.toString())
                            e.printStackTrace()
                            future.complete(false)
                        }
                    })

                    try {
                        future.get(5, java.util.concurrent.TimeUnit.SECONDS)
                    } catch (e: Exception) {
                        plugin.localizationManager.severe("plotmanager.world.delete.timeout",
                            "%name%" to worldName, "%error%" to e.message.toString())
                    }
                } catch (e: Exception) {
                    plugin.localizationManager.severe("plotmanager.world.delete.schedule.error",
                        "%name%" to worldName, "%error%" to e.message.toString())
                }
            }
        }

        plots.remove(name)
        savePlots()

        plugin.communicationManager.sendPlotDeletion(name)
        plugin.localizationManager.info("plotmanager.plot.deleted", "%name%" to name)

        return true
    }

    private fun deleteWorldSafely(worldName: String, plot: Plot): Boolean {
        if (!Bukkit.isPrimaryThread()) {
            plugin.localizationManager.severe("plotmanager.world.delete.thread.error")
            return false
        }

        cancelUnloadTask(worldName)

        var success = true
        val world = Bukkit.getWorld(worldName)

        if (world != null) {
            val defaultWorld = Bukkit.getWorlds()[0]
            for (player in world.players) {
                player.teleport(defaultWorld.spawnLocation)
                plugin.localizationManager.sendMessage(player, "messages.plotmanager.plot.deleted.teleport")
            }

            try {
                success = Bukkit.unloadWorld(world, false) // не сохраняем при выгрузке
                if (!success) {
                    plugin.localizationManager.warning("plotmanager.world.unload.failed", "%name%" to worldName)
                }
            } catch (e: Exception) {
                plugin.localizationManager.severe("plotmanager.world.unload.error",
                    "%name%" to worldName, "%error%" to e.message.toString())
                success = false
            }
        }

        try {
            val pluginWorldFolder = File(plotsWorldsFolder, worldName)
            if (pluginWorldFolder.exists()) {
                deleteDirectoryCompletely(pluginWorldFolder)
            }

            val rootWorldFolder = File(Bukkit.getWorldContainer(), worldName)
            if (rootWorldFolder.exists()) {
                deleteDirectoryCompletely(rootWorldFolder)
            }
        } catch (e: Exception) {
            plugin.localizationManager.severe("plotmanager.world.files.delete.error",
                "%error%" to e.message.toString())
            success = false
        }

        System.gc()

        return success
    }

    private fun deleteDirectoryCompletely(directory: File) {
        if (!directory.exists()) return

        try {
            val regionFolders = arrayOf(
                File(directory, "region"),
                File(directory, "DIM1/region"),
                File(directory, "DIM-1/region")
            )

            for (regionFolder in regionFolders) {
                if (regionFolder.exists() && regionFolder.isDirectory) {
                    regionFolder.listFiles()?.forEach { file ->
                        if (file.name.endsWith(".mca")) {
                            if (!file.delete()) {
                                file.deleteOnExit()
                            }
                        }
                    }
                }
            }
            Files.walkFileTree(directory.toPath(), object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    try {
                        Files.delete(file)
                    } catch (e: Exception) {
                        file.toFile().deleteOnExit()
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    try {
                        Files.delete(dir)
                    } catch (e: Exception) {
                        dir.toFile().deleteOnExit()
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (e: Exception) {
            plugin.localizationManager.severe("plotmanager.directory.delete.error",
                "%error%" to e.message.toString())
        }
    }

    fun getPlot(name: String): Plot? {
        return plots[name]
    }

    fun getAllPlots(): List<Plot> {
        return plots.values.toList()
    }

    fun getPlotAtLocation(location: Location): Plot? {
        val plotLocation = PlotLocation(location)
        return plots.values.find { it.contains(plotLocation) }
    }

    fun savePlots() {
        try {
            if (!plotsFile.exists()) {
                plotsFile.createNewFile()
            }

            FileWriter(plotsFile).use { writer ->
                gson.toJson(plots.values.toList(), writer)
            }

            plugin.localizationManager.info("plotmanager.plots.saved",
                "%count%" to plots.size.toString())
        } catch (e: Exception) {
            plugin.localizationManager.severe("plotmanager.plots.save.error",
                "%error%" to e.message.toString())
        }
    }

    private fun loadPlots() {
        try {
            if (plotsFile.exists()) {
                FileReader(plotsFile).use { reader ->
                    val loadedPlots = gson.fromJson(reader, Array<Plot>::class.java)
                    plots.clear()
                    loadedPlots.forEach { plot ->
                        plots[plot.name] = plot
                    }
                }
                plugin.localizationManager.info("plotmanager.plots.loaded",
                    "%count%" to plots.size.toString())
            }
        } catch (e: Exception) {
            plugin.localizationManager.severe("plotmanager.plots.load.error",
                "%error%" to e.message.toString())
        }
    }

    fun addOrUpdatePlot(plot: Plot) {
        plots[plot.name] = plot
        savePlots()
        plugin.localizationManager.info("plotmanager.plot.updated", "%name%" to plot.name)
    }

    /**
     * Запускает задачу автосохранения миров
     */
    fun startAutoSave() {
        autoSaveTask?.cancel()

        val autoSaveInterval = plugin.config.getLong("world-autosave-interval", 10)

        if (autoSaveInterval <= 0) {
            plugin.localizationManager.info("plotmanager.autosave.disabled")
            return
        }

        if (plugin.serverType == ServerType.TEST) {
            val intervalTicks = autoSaveInterval * 60 * 20
            autoSaveTask = object : BukkitRunnable() {
                override fun run() {
                    plugin.localizationManager.info("plotmanager.autosave.start")
                    saveAllPlotWorlds()
                    plugin.localizationManager.info("plotmanager.autosave.complete")
                }
            }.runTaskTimer(plugin, intervalTicks, intervalTicks)

            plugin.localizationManager.info("plotmanager.autosave.enabled",
                "%interval%" to autoSaveInterval.toString())
        }
    }

    fun stopAutoSave() {
        autoSaveTask?.cancel()
        autoSaveTask = null
        plugin.localizationManager.info("plotmanager.autosave.stopped")
    }

    fun saveAllPlotWorlds() {
        if (plugin.serverType != ServerType.TEST) return

        var savedCount = 0
        for (plot in plots.values) {
            val worldName = plot.getTestWorldName(plugin)
            val world = Bukkit.getWorld(worldName)

            if (world != null) {
                try {
                    world.save()

                    val rootWorldFolder = File(Bukkit.getWorldContainer(), worldName)
                    val pluginWorldFolder = File(plotsWorldsFolder, worldName)

                    if (rootWorldFolder.exists() && rootWorldFolder.isDirectory) {
                        if (!pluginWorldFolder.exists()) {
                            moveWorldFolder(rootWorldFolder, pluginWorldFolder)
                        } else {
                            deleteDirectory(pluginWorldFolder)
                            copyDirectory(rootWorldFolder, pluginWorldFolder)
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
        for (plot in plots.values) {
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
        val plot = getPlot(plotName) ?: return null
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

                    moveWorldFolder(rootWorldFolder, worldFolder)
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
        for (plot in plots.values) {
            val worldName = plot.getTestWorldName(plugin)
            val world = Bukkit.getWorld(worldName)

            if (world != null) {
                try {
                    world.save()

                    val rootWorldFolder = File(Bukkit.getWorldContainer(), worldName)
                    val pluginWorldFolder = File(plotsWorldsFolder, worldName)

                    if (rootWorldFolder.exists() && rootWorldFolder.isDirectory) {
                        if (!pluginWorldFolder.exists()) {
                            moveWorldFolder(rootWorldFolder, pluginWorldFolder)
                        } else {
                            deleteDirectory(pluginWorldFolder)
                            copyDirectory(rootWorldFolder, pluginWorldFolder)
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

    private fun moveWorldFolder(source: File, destination: File) {
        if (destination.exists()) {
            deleteDirectory(destination)
        }

        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            copyDirectory(source, destination)
            deleteDirectory(source)
        }
    }

    private fun copyDirectory(source: File, destination: File) {
        if (!destination.exists()) {
            destination.mkdirs()
        }

        val sourcePath = source.toPath()
        val destinationPath = destination.toPath()

        Files.walkFileTree(sourcePath, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val targetDir = destinationPath.resolve(sourcePath.relativize(dir))
                try {
                    Files.createDirectories(targetDir)
                } catch (e: Exception) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val targetFile = destinationPath.resolve(sourcePath.relativize(file))
                try {
                    Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING)
                } catch (e: Exception) {
                }
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun deleteDirectory(directory: File) {
        try {
            Files.walkFileTree(directory.toPath(), object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (e: Exception) {
        }
    }

    private val worldUnloadDelay = plugin.config.getLong("world-unload-delay", 5)

    private val worldUnloadTasks = ConcurrentHashMap<String, BukkitTask>()

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
                                moveWorldFolder(rootWorldFolder, pluginWorldFolder)
                            } else {
                                deleteDirectory(pluginWorldFolder)
                                copyDirectory(rootWorldFolder, pluginWorldFolder)
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

    private fun cancelUnloadTask(worldName: String) {
        val task = worldUnloadTasks.remove(worldName)
        task?.cancel()
    }

    private fun cancelAllUnloadTasks() {
        worldUnloadTasks.values.forEach { it.cancel() }
        worldUnloadTasks.clear()
    }

    fun shutdown() {
        stopAutoSave()
        cancelAllUnloadTasks()
        saveAllPlotWorlds()
        unloadAllPlotWorlds()
        plugin.localizationManager.info("plotmanager.shutdown.complete")
    }

    private fun deleteWorldCompletely(worldName: String, plot: Plot): Boolean {
        cancelUnloadTask(worldName)

        val world = Bukkit.getWorld(worldName)
        if (world != null) {
            val defaultWorld = Bukkit.getWorlds()[0]
            for (player in world.players) {
                player.teleport(defaultWorld.spawnLocation)
                plugin.localizationManager.sendMessage(player, "messages.plotmanager.plot.deleted.teleport")
            }

            try {
                if (!Bukkit.unloadWorld(world, false)) { // false = не сохраняем мир при выгрузке
                    plugin.localizationManager.warning("plotmanager.world.unload.failed",
                        "%name%" to worldName)
                }
            } catch (e: Exception) {
                plugin.localizationManager.severe("plotmanager.world.unload.error",
                    "%name%" to worldName, "%error%" to e.message.toString())
            }
        }

        try {
            clearChunkCache()
        } catch (e: Exception) {
            plugin.localizationManager.warning("plotmanager.chunk.cache.clear.failed",
                "%error%" to e.message.toString())
        }
        var success = true

        val pluginWorldFolder = File(plotsWorldsFolder, worldName)
        if (pluginWorldFolder.exists()) {
            if (!deleteWorldDirectoryComplete(pluginWorldFolder)) {
                plugin.localizationManager.warning("plotmanager.world.plugin.delete.failed",
                    "%name%" to worldName)
                success = false
            }
        }

        val rootWorldFolder = File(Bukkit.getWorldContainer(), worldName)
        if (rootWorldFolder.exists()) {
            if (!deleteWorldDirectoryComplete(rootWorldFolder)) {
                plugin.localizationManager.warning("plotmanager.world.root.delete.failed",
                    "%name%" to worldName)
                success = false
            }
        }

        try {
            val sessionFolder = File(Bukkit.getWorldContainer(), "session.lock")
            if (sessionFolder.exists()) {
                sessionFolder.delete()
            }

            val uidFile = File(rootWorldFolder, "uid.dat")
            if (uidFile.exists()) {
                uidFile.delete()
            }
        } catch (e: Exception) {
            plugin.localizationManager.warning("plotmanager.world.service.files.delete.error",
                "%error%" to e.message.toString())
        }

        System.gc()

        return success
    }

    private fun deleteWorldDirectoryComplete(directory: File): Boolean {
        if (!directory.exists()) {
            return true
        }

        var success = true

        try {
            // region, DIM1/region, DIM-1/region
            val regionFolders = listOf(
                File(directory, "region"),
                File(directory, "DIM1/region"),
                File(directory, "DIM-1/region")
            )

            for (regionFolder in regionFolders) {
                if (regionFolder.exists() && regionFolder.isDirectory) {
                    val regionFiles = regionFolder.listFiles { file ->
                        file.name.startsWith("r.") && file.name.endsWith(".mca")
                    }

                    regionFiles?.forEach { file ->
                        if (!file.delete()) {
                            forceDeleteFile(file)
                            success = false
                        }
                    }
                }
            }

            Files.walkFileTree(directory.toPath(), object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    try {
                        Files.delete(file)
                    } catch (e: Exception) {
                        success = false
                        plugin.localizationManager.warning("plotmanager.file.delete.failed",
                            "%path%" to file.fileName.toString())
                        forceDeleteFile(file.toFile())
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    try {
                        Files.delete(dir)
                    } catch (e: Exception) {
                        success = false
                        plugin.localizationManager.warning("plotmanager.directory.delete.failed",
                            "%path%" to dir.fileName.toString())
                    }
                    return FileVisitResult.CONTINUE
                }
            })

            if (directory.exists()) {
                plugin.localizationManager.warning("plotmanager.directory.still.exists",
                    "%path%" to directory.absolutePath)
                success = false
            }
        } catch (e: Exception) {
            plugin.localizationManager.severe("plotmanager.world.directory.delete.error",
                "%error%" to e.message.toString())
            e.printStackTrace()
            success = false
        }

        return success
    }

    private fun forceDeleteFile(file: File) {
        try {
            val tempFile = File(file.parentFile, "temp_delete_${System.currentTimeMillis()}_${file.name}")
            if (file.renameTo(tempFile)) {
                tempFile.deleteOnExit()

                System.gc()
                Thread.sleep(100)

                tempFile.delete()
            }
        } catch (e: Exception) {
            plugin.localizationManager.warning("plotmanager.file.force.delete.failed",
                "%path%" to file.absolutePath)
        }
    }

    private fun clearChunkCache() {
        try {
            val craftServer = Bukkit.getServer()
            val serverClass = craftServer.javaClass

            val getServerMethod = serverClass.getMethod("getServer")
            getServerMethod.isAccessible = true
            val nmsServer = getServerMethod.invoke(craftServer)

            nmsServer.javaClass.methods.forEach { method ->
                if ((method.name == "getChunkProvider" || method.name == "getChunkSource") && method.parameterCount == 0) {
                    val chunkProvider = method.invoke(nmsServer)

                    chunkProvider.javaClass.methods.forEach { providerMethod ->
                        if (providerMethod.name.contains("clear") || providerMethod.name.contains("flush")) {
                            providerMethod.invoke(chunkProvider)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Игнорируем ошибки, так как это опциональный шаг
        }
    }
}