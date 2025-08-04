package com.kostyamops.buildersplots.data

import com.kostyamops.buildersplots.BuildersPlots
import org.bukkit.Bukkit
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class PlotWorldCleanup(private val plugin: BuildersPlots, private val plotManager: PlotManager) {

    fun safelyDeletePlotWorld(worldName: String, plot: Plot) {
        if (Bukkit.isPrimaryThread()) {
            deleteWorldSafely(worldName, plot)
        } else {
            try {
                val future = CompletableFuture<Boolean>()

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
                    future.get(5, TimeUnit.SECONDS)
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

    private fun deleteWorldSafely(worldName: String, plot: Plot): Boolean {
        if (!Bukkit.isPrimaryThread()) {
            plugin.localizationManager.severe("plotmanager.world.delete.thread.error")
            return false
        }

        plotManager.worldManager.cancelUnloadTask(worldName)

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
            val pluginWorldFolder = File(plotManager.worldManager.plotsWorldsFolder, worldName)
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

    fun deleteWorldCompletely(worldName: String, plot: Plot): Boolean {
        plotManager.worldManager.cancelUnloadTask(worldName)

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

        val pluginWorldFolder = File(plotManager.worldManager.plotsWorldsFolder, worldName)
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