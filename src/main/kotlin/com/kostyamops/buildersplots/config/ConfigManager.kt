package com.kostyamops.buildersplots.config

import com.kostyamops.buildersplots.BuildersPlots
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class ConfigManager(private val plugin: BuildersPlots) {
    private lateinit var config: FileConfiguration
    
    // Network settings
    var isMainServer: Boolean = false
    var targetServerIp: String = "localhost"
    var sendPort: Int = 25566
    var receivePort: Int = 25567
    
    // Plot settings
    var plotsWorldName: String = "plots"
    var maxPlotSize: Int = 200
    
    fun loadConfig() {
        val configFile = File(plugin.dataFolder, "config.yml")
        
        if (!configFile.exists()) {
            plugin.saveDefaultConfig()
        }
        
        config = plugin.config
        
        // Load network settings
        isMainServer = config.getBoolean("server.isMainServer", false)
        targetServerIp = config.getString("server.targetServerIp", "localhost") ?: "localhost"
        sendPort = config.getInt("server.sendPort", 25566)
        receivePort = config.getInt("server.receivePort", 25567)
        
        // Load plot settings
        plotsWorldName = config.getString("plots.worldName", "plots") ?: "plots"
        maxPlotSize = config.getInt("plots.maxPlotSize", 200)
        
        plugin.log("Configuration loaded. Server type: ${if(isMainServer) "MAIN" else "TEST"}")
    }
    
    fun saveConfig() {
        config.set("server.isMainServer", isMainServer)
        config.set("server.targetServerIp", targetServerIp)
        config.set("server.sendPort", sendPort)
        config.set("server.receivePort", receivePort)
        
        config.set("plots.worldName", plotsWorldName)
        config.set("plots.maxPlotSize", maxPlotSize)
        
        plugin.saveConfig()
    }
}