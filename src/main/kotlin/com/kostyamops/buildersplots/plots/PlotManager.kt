package com.kostyamops.buildersplots.plots

import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.network.packets.PlotManagementPacket
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import java.io.File
import java.util.*
import java.util.logging.Level

class PlotManager(private val plugin: BuildersPlots) {
    private val plots = mutableMapOf<UUID, Plot>()
    private val storage = PlotStorage(plugin)
    
    init {
        if (!plugin.configManager.isMainServer) {
            loadPlots()
        }
    }
    
    fun createPlot(name: String, originalWorld: String, centerX: Int, centerZ: Int, radius: Int, creatorUuid: UUID): Plot? {
        // Validate input
        if (radius <= 0 || radius > plugin.configManager.maxPlotSize) {
            plugin.log("Invalid plot radius: $radius. Must be between 1 and ${plugin.configManager.maxPlotSize}", Level.WARNING)
            return null
        }
        
        if (getPlotByName(name) != null) {
            plugin.log("A plot with name '$name' already exists", Level.WARNING)
            return null
        }
        
        // Create the plot
        val plotWorldName = "${plugin.configManager.plotsWorldName}_${UUID.randomUUID().toString().substring(0, 8)}"
        
        val plot = Plot(
            name = name,
            worldName = plotWorldName,
            originalWorld = originalWorld,
            centerX = centerX,
            centerZ = centerZ,
            radius = radius,
            createdBy = creatorUuid
        )
        
        plots[plot.id] = plot
        storage.savePlot(plot)
        
        plugin.log("Created new plot: $name at ($centerX, $centerZ) with radius $radius")
        
        // If this is the test server, we need to actually create the world
        if (!plugin.configManager.isMainServer) {
            // Create world logic would be here
            // We'd need to use the Folia API to create a new world
            plugin.log("Creating new world for plot: $plotWorldName")
        }
        
        return plot
    }
    
    fun deletePlot(id: UUID): Boolean {
        val plot = plots[id] ?: return false
        
        plots.remove(id)
        storage.deletePlot(plot)
        
        // If on test server, we should clean up the world
        if (!plugin.configManager.isMainServer) {
            // World deletion logic here
            plugin.log("Deleting world for plot: ${plot.worldName}")
        }
        
        plugin.log("Deleted plot: ${plot.name}")
        return true
    }
    
    fun getPlot(id: UUID): Plot? {
        return plots[id]
    }
    
    fun getPlotByName(name: String): Plot? {
        return plots.values.find { it.name.equals(name, ignoreCase = true) }
    }
    
    fun getPlotByLocation(location: Location): Plot? {
        return plots.values.find { it.isInside(location) }
    }
    
    fun getPlayerPlots(playerUuid: UUID, includeAccess: Boolean = true): List<Plot> {
        return plots.values.filter { 
            it.createdBy == playerUuid || (includeAccess && it.hasMember(playerUuid))
        }
    }
    
    fun getAllPlots(): Collection<Plot> {
        return plots.values
    }
    
    fun disablePlotSync(id: UUID): Boolean {
        val plot = plots[id] ?: return false
        
        if (!plot.syncEnabled) return false
        
        plot.disableSync()
        storage.savePlot(plot)
        
        plugin.log("Disabled synchronization for plot: ${plot.name}")
        return true
    }
    
    fun addMemberToPlot(plotId: UUID, playerUuid: UUID): Boolean {
        val plot = plots[plotId] ?: return false
        
        if (plot.hasMember(playerUuid)) return false
        
        val result = plot.addMember(playerUuid)
        if (result) {
            storage.savePlot(plot)
            plugin.log("Added player $playerUuid to plot: ${plot.name}")
        }
        
        return result
    }
    
    fun removeMemberFromPlot(plotId: UUID, playerUuid: UUID): Boolean {
        val plot = plots[plotId] ?: return false
        
        val result = plot.removeMember(playerUuid)
        if (result) {
            storage.savePlot(plot)
            plugin.log("Removed player $playerUuid from plot: ${plot.name}")
        }
        
        return result
    }
    
    fun loadPlots() {
        plots.clear()
        val loadedPlots = storage.loadAllPlots()
        
        loadedPlots.forEach { plot ->
            plots[plot.id] = plot
        }
        
        plugin.log("Loaded ${plots.size} plots")
    }
    
    fun savePlots() {
        plots.values.forEach { plot ->
            storage.savePlot(plot)
        }
        
        plugin.log("Saved ${plots.size} plots")
    }
    
    fun canPlayerAccessPlot(player: Player, plotId: UUID): Boolean {
        val plot = plots[plotId] ?: return false
        
        // Ops can access all plots
        if (player.isOp) return true
        
        return plot.hasMember(player.uniqueId)
    }

    fun createPlotWithInitialSync(name: String, originalWorld: String, centerX: Int, centerZ: Int, radius: Int, creatorUuid: UUID): Plot? {
        // Создаем плот
        val plot = createPlot(name, originalWorld, centerX, centerZ, radius, creatorUuid) ?: return null

        // Если мы на основном сервере, инициируем копирование блоков
        if (plugin.configManager.isMainServer) {
            val minX = centerX - radius
            val maxX = centerX + radius
            val minZ = centerZ - radius
            val maxZ = centerZ + radius

            // Отправляем команду на копирование области
            val plotData = mapOf(
                "plotId" to plot.id.toString(),
                "originalWorld" to originalWorld,
                "minX" to minX,
                "maxX" to maxX,
                "minZ" to minZ,
                "maxZ" to maxZ,
                "minY" to -64,
                "maxY" to 320
            )

            plugin.networkManager.sendPlotManagement("INITIAL_SYNC", plotData)
            plugin.log("Запрошена начальная синхронизация для плота ${plot.name}")
        }

        return plot
    }
}