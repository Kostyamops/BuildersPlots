package com.kostyamops.buildersplots

import com.kostyamops.buildersplots.commands.BuildersPlotsCommand
import com.kostyamops.buildersplots.data.PlotManager
import com.kostyamops.buildersplots.listeners.BlockChangeListener
import com.kostyamops.buildersplots.network.ServerCommunicationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class BuildersPlots : JavaPlugin() {

    lateinit var plotManager: PlotManager
    lateinit var communicationManager: ServerCommunicationManager
    lateinit var serverType: ServerType

    private val pluginScope = CoroutineScope(Dispatchers.IO)

    override fun onEnable() {
        try {
            // Save default config
            saveDefaultConfig()

            // Log plugin info
            logger.info("Enabling BuildersPlots v${description.version}")

            // Load server type
            val serverTypeStr = config.getString("server-type", "main")
            serverType = try {
                ServerType.valueOf(serverTypeStr!!.uppercase())
            } catch (e: IllegalArgumentException) {
                logger.warning("Invalid server type: $serverTypeStr. Defaulting to MAIN.")
                ServerType.MAIN
            }

            logger.info("Starting BuildersPlots as ${serverType.name} server")

            // Initialize data directory
            val dataDir = File(dataFolder, "plots")
            if (!dataDir.exists()) {
                dataDir.mkdirs()
            }

            plotManager = PlotManager(this)
            communicationManager = ServerCommunicationManager(this)
            server.pluginManager.registerEvents(BlockChangeListener(this), this)

            getCommand("bp")?.setExecutor(BuildersPlotsCommand(this))

            if (serverType == ServerType.TEST) {
                // Загружаем миры плотов и запускаем автосохранение
                plotManager.loadAllPlotWorlds()
                plotManager.startAutoSave()
            }

            // Register listeners
            server.pluginManager.registerEvents(BlockChangeListener(this), this)

            // Start network communication
            pluginScope.launch {
                communicationManager.startCommunication()
            }

            logger.info("BuildersPlots plugin enabled!")
        } catch (e: Exception) {
            logger.severe("Error enabling BuildersPlots: ${e.message}")
            e.printStackTrace()
            server.pluginManager.disablePlugin(this)
        }
    }

    override fun onDisable() {
        try {
            // Save plot data
            if (::plotManager.isInitialized) {
                plotManager.savePlots()
            }

            // Stop communication
            if (::communicationManager.isInitialized) {
                pluginScope.launch {
                    communicationManager.stopCommunication()
                }
            }

            plotManager.shutdown()

            logger.info("BuildersPlots plugin disabled!")
        } catch (e: Exception) {
            logger.severe("Error disabling BuildersPlots: ${e.message}")
            e.printStackTrace()
        }
    }
}

enum class ServerType {
    MAIN, TEST
}