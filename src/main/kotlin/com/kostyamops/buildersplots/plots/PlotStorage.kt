package com.kostyamops.buildersplots.plots

import com.kostyamops.buildersplots.BuildersPlots
import java.io.*
import java.util.*
import java.util.logging.Level

class PlotStorage(private val plugin: BuildersPlots) {
    private val plotsDir = File(plugin.dataFolder, "plots")
    
    init {
        if (!plotsDir.exists()) {
            plotsDir.mkdirs()
        }
    }
    
    fun savePlot(plot: Plot) {
        val plotFile = File(plotsDir, "${plot.id}.dat")
        
        try {
            ObjectOutputStream(FileOutputStream(plotFile)).use { oos ->
                oos.writeObject(plot)
            }
        } catch (e: Exception) {
            plugin.log("Failed to save plot ${plot.name}: ${e.message}", Level.SEVERE)
            e.printStackTrace()
        }
    }
    
    fun loadPlot(id: UUID): Plot? {
        val plotFile = File(plotsDir, "$id.dat")
        
        if (!plotFile.exists()) {
            return null
        }
        
        try {
            ObjectInputStream(FileInputStream(plotFile)).use { ois ->
                return ois.readObject() as Plot
            }
        } catch (e: Exception) {
            plugin.log("Failed to load plot $id: ${e.message}", Level.SEVERE)
            e.printStackTrace()
            return null
        }
    }
    
    fun deletePlot(plot: Plot) {
        val plotFile = File(plotsDir, "${plot.id}.dat")
        
        if (plotFile.exists()) {
            plotFile.delete()
        }
        
        // Also delete the world directory if it exists
        val worldDir = File(plugin.server.worldContainer, plot.worldName)
        if (worldDir.exists() && worldDir.isDirectory) {
            // This is just a marker, actual world deletion would require more complex logic
            plugin.log("World directory for plot ${plot.name} should be deleted", Level.INFO)
        }
    }
    
    fun loadAllPlots(): List<Plot> {
        val plots = mutableListOf<Plot>()
        
        plotsDir.listFiles { file -> file.isFile && file.name.endsWith(".dat") }?.forEach { file ->
            try {
                val id = UUID.fromString(file.nameWithoutExtension)
                val plot = loadPlot(id)
                
                if (plot != null) {
                    plots.add(plot)
                }
            } catch (e: Exception) {
                plugin.log("Failed to load plot from file ${file.name}: ${e.message}", Level.WARNING)
            }
        }
        
        return plots
    }
}