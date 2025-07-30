package ru.joutak.buildersplots.config

import org.bukkit.configuration.file.FileConfiguration
import ru.joutak.buildersplots.BuildersPlots
import ru.joutak.buildersplots.network.ServerRole
import java.io.File

class PluginConfig(private val plugin: BuildersPlots) {

    val serverRole: ServerRole
    val receiverPort: Int
    val senderPort: Int
    val receiverHost: String
    val plotsDirectory: String
    val language: String
    val debugMode: Boolean
    val maxPlotWidth: Int
    val maxPlotHeight: Int
    val maxPlotLength: Int
    val autoSaveInterval: Int

    init {
        // Используем getConfig() из JavaPlugin вместо свойства config
        val config = plugin.getConfig()
        
        // Load server role
        val roleString = config.getString("server-role", "SENDER")
        serverRole = try {
            ServerRole.valueOf(roleString?.uppercase() ?: "SENDER")
        } catch (e: IllegalArgumentException) {
            plugin.logger.warning("[BuildersPlots] Invalid server role: $roleString. Defaulting to SENDER.")
            ServerRole.SENDER
        }

        // Load network settings
        receiverPort = config.getInt("receiver-port", 5555)
        senderPort = config.getInt("sender-port", 5556)
        receiverHost = config.getString("receiver-host") ?: "localhost"

        // Load language setting
        language = config.getString("language") ?: "en"

        // Load storage settings
        plotsDirectory = config.getString("plots-directory") ?: "plots"
        
        // Load max plot size
        maxPlotWidth = config.getInt("max-plot-size.width", 256)
        maxPlotHeight = config.getInt("max-plot-size.height", 256)
        maxPlotLength = config.getInt("max-plot-size.length", 256)
        
        // Load auto-save interval
        autoSaveInterval = config.getInt("auto-save-interval", 30)
        
        // Load debug mode
        debugMode = config.getBoolean("debug-mode", false)

        // Create plots directory if it doesn't exist
        val plotsDir = File(plugin.dataFolder, plotsDirectory)
        if (!plotsDir.exists()) {
            plotsDir.mkdirs()
        }
        
        // Log configuration
        if (debugMode) {
            plugin.logger.info("[BuildersPlots] Configuration loaded:")
            plugin.logger.info("[BuildersPlots] Server role: $serverRole")
            plugin.logger.info("[BuildersPlots] Language: $language")
            plugin.logger.info("[BuildersPlots] Network: $receiverHost:$receiverPort/$senderPort")
        }
    }
}