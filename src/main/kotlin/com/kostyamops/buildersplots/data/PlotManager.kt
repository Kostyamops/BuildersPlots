package com.kostyamops.buildersplots.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.ServerType
import org.bukkit.Location
import org.bukkit.entity.Player
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlotManager(private val plugin: BuildersPlots) {
    
    private val plots = ConcurrentHashMap<String, Plot>()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val plotsFile = File(plugin.dataFolder, "plots.json")
    
    init {
        loadPlots()
    }
    
    fun createPlot(name: String, radius: Int, player: Player): Plot? {
        // Check if plot name already exists
        if (plots.containsKey(name)) {
            return null
        }
        
        // Check if radius is within allowed limits
        val maxRadius = plugin.config.getInt("plots.max-radius", 100)
        if (radius <= 0 || radius > maxRadius) {
            return null
        }
        
        val plotLocation = PlotLocation(player.location)
        val plot = Plot(name, radius, plotLocation, player.uniqueId)
        
        plots[name] = plot
        savePlots()
        
        // If this is the main server, send the plot creation to the test server
        if (plugin.serverType == ServerType.MAIN) {
            plugin.communicationManager.sendPlotCreation(plot)
        }
        
        return plot
    }
    
    fun deletePlot(name: String): Boolean {
        val plot = plots.remove(name) ?: return false
        savePlots()
        
        // Send delete command to other server
        plugin.communicationManager.sendPlotDeletion(name)
        
        return true
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
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save plots: ${e.message}")
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
            }
        } catch (e: Exception) {
            plugin.logger.severe("Failed to load plots: ${e.message}")
        }
    }
    
    fun addOrUpdatePlot(plot: Plot) {
        plots[plot.name] = plot
        savePlots()
    }
}