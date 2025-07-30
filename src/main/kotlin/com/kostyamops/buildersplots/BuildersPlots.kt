package com.kostyamops.buildersplots

import com.kostyamops.buildersplots.commands.CommandManager
import com.kostyamops.buildersplots.config.ConfigManager
import com.kostyamops.buildersplots.listeners.BlockListener
import com.kostyamops.buildersplots.network.NetworkManager
import com.kostyamops.buildersplots.network.ServerConnection
import com.kostyamops.buildersplots.plots.PlotManager
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level

class BuildersPlots : JavaPlugin() {
    companion object {
        lateinit var instance: BuildersPlots
            private set
    }

    lateinit var configManager: ConfigManager
        private set
    lateinit var plotManager: PlotManager
        private set
    lateinit var networkManager: NetworkManager
        private set
    lateinit var serverConnection: ServerConnection
        private set

    override fun onEnable() {
        instance = this
        
        // Create plugin directory if it doesn't exist
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
        }
        
        // Initialize components
        configManager = ConfigManager(this)
        configManager.loadConfig()
        
        plotManager = PlotManager(this)
        
        networkManager = NetworkManager(this)
        serverConnection = ServerConnection(this, 
            configManager.sendPort, 
            configManager.receivePort, 
            configManager.isMainServer)
        
        // Register listeners
        if (configManager.isMainServer) {
            server.pluginManager.registerEvents(BlockListener(this), this)
        }
        
        // Register commands
        CommandManager(this).registerCommands()
        
        // Start network connection
        serverConnection.startConnection()
        
        logger.info("BuildersPlots has been enabled!")
        logger.info("Running as ${if (configManager.isMainServer) "SENDER (Main Server)" else "RECEIVER (Test Server)"}")
    }

    override fun onDisable() {
        // Close connection and save data
        serverConnection.stopConnection()
        plotManager.savePlots()
        
        logger.info("BuildersPlots has been disabled!")
    }
    
    fun log(message: String, level: Level = Level.INFO) {
        logger.log(level, message)
    }
}