package ru.joutak.buildersplots.manager

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.entity.Player
import ru.joutak.buildersplots.BuildersPlots
import ru.joutak.buildersplots.model.BlockChange
import ru.joutak.buildersplots.model.Plot
import ru.joutak.buildersplots.model.PlotCreationRequest
import ru.joutak.buildersplots.network.ServerRole
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class PlotManager(private val plugin: BuildersPlots) {

    private val plots = ConcurrentHashMap<String, Plot>()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    init {
        if (plugin.config.serverRole == ServerRole.RECEIVER) {
            loadPlots()
        }
    }

    fun createPlot(request: PlotCreationRequest) {
        if (plugin.config.serverRole != ServerRole.RECEIVER) {
            plugin.logger.warning("Cannot create plot on sender server")
            return
        }

        // Generate a unique ID for this plot
        val plotId = UUID.randomUUID().toString().substring(0, 8)

        // Create a new plot instance
        val plot = Plot(
            id = plotId,
            name = request.plotName,
            owner = request.owner,
            originWorld = request.worldName,
            originX = request.x,
            originY = request.y,
            originZ = request.z,
            radius = request.radius
        )

        // Create a new world for this plot
        val plotWorldName = "plot_$plotId"
        val worldCreator = WorldCreator(plotWorldName)
        val plotWorld = worldCreator.createWorld()

        if (plotWorld != null) {
            // Clone the region from the main world (this would need to be done via network)
            // For now, we'll just create an empty world
            plotWorld.setSpawnLocation(0, 100, 0)

            // Store the plot
            plots[plotId] = plot
            savePlot(plot)

            plugin.logger.info("Created new plot: ${plot.name} (ID: $plotId)")
        } else {
            plugin.logger.severe("Failed to create world for plot: ${plot.name}")
        }
    }

    fun getPlot(id: String): Plot? {
        return plots[id]
    }

    fun getPlotByWorld(worldName: String): Plot? {
        val plotId = worldName.removePrefix("plot_")
        return plots[plotId]
    }

    fun getPlotForLocation(location: Location): Plot? {
        val worldName = location.world.name
        if (!worldName.startsWith("plot_")) return null

        val plotId = worldName.removePrefix("plot_")
        val plot = plots[plotId] ?: return null

        return if (plot.containsLocation(location)) plot else null
    }

    fun getPlayerPlots(player: Player): List<Plot> {
        return plots.values.filter {
            it.owner == player.uniqueId ||
                    it.members.contains(player.uniqueId) ||
                    player.isOp
        }
    }

    fun applyBlockChange(change: BlockChange) {
        if (plugin.config.serverRole != ServerRole.RECEIVER) {
            return
        }

        // Find all plots that correspond to this block change
        val affectedPlots = plots.values.filter { plot ->
            if (!plot.syncEnabled) return@filter false

            val minX = plot.originX - plot.radius
            val maxX = plot.originX + plot.radius
            val minZ = plot.originZ - plot.radius
            val maxZ = plot.originZ + plot.radius

            plot.originWorld == change.worldName &&
                    change.x in minX..maxX &&
                    change.z in minZ..maxZ
        }

        // Apply the change to each affected plot
        for (plot in affectedPlots) {
            val plotWorld = Bukkit.getWorld("plot_${plot.id}") ?: continue

            // Calculate the relative position in the plot world
            val relativeX = change.x - (plot.originX - plot.radius)
            val relativeY = change.y
            val relativeZ = change.z - (plot.originZ - plot.radius)

            // Set the block
            val location = Location(plotWorld, relativeX.toDouble(), relativeY.toDouble(), relativeZ.toDouble())
            val blockData = Bukkit.createBlockData(change.blockData)
            location.block.blockData = blockData
        }
    }

    fun deletePlot(plotId: String): Boolean {
        val plot = plots.remove(plotId) ?: return false

        // Delete the plot's world
        val plotWorld = Bukkit.getWorld("plot_${plot.id}")
        if (plotWorld != null) {
            // Teleport any players in this world to the main world
            val mainWorld = Bukkit.getWorlds()[0]
            plotWorld.players.forEach { player ->
                player.teleport(mainWorld.spawnLocation)
            }

            // Unload and delete the world
            Bukkit.unloadWorld(plotWorld, false)

            // Delete the world directory
            val worldDir = File(Bukkit.getWorldContainer(), "plot_${plot.id}")
            if (worldDir.exists()) {
                worldDir.deleteRecursively()
            }
        }

        // Delete the plot file
        val plotFile = File(plugin.dataFolder, "${plugin.config.plotsDirectory}/${plot.id}.json")
        if (plotFile.exists()) {
            plotFile.delete()
        }

        return true
    }

    fun savePlots() {
        plots.values.forEach { savePlot(it) }
    }

    private fun savePlot(plot: Plot) {
        val plotsDir = File(plugin.dataFolder, plugin.config.plotsDirectory)
        if (!plotsDir.exists()) {
            plotsDir.mkdirs()
        }

        val plotFile = File(plotsDir, "${plot.id}.json")
        FileWriter(plotFile).use { writer ->
            gson.toJson(plot, writer)
        }
    }

    private fun loadPlots() {
        val plotsDir = File(plugin.dataFolder, plugin.config.plotsDirectory)
        if (!plotsDir.exists()) {
            plotsDir.mkdirs()
            return
        }

        plotsDir.listFiles { file -> file.isFile && file.extension == "json" }?.forEach { file ->
            try {
                FileReader(file).use { reader ->
                    val plot = gson.fromJson(reader, Plot::class.java)
                    plots[plot.id] = plot

                    // Make sure the plot world is loaded
                    val worldName = "plot_${plot.id}"
                    if (Bukkit.getWorld(worldName) == null) {
                        val worldCreator = WorldCreator(worldName)
                        worldCreator.createWorld()
                    }
                }
            } catch (e: Exception) {
                plugin.logger.warning("Failed to load plot from file ${file.name}: ${e.message}")
            }
        }

        plugin.logger.info("Loaded ${plots.size} plots")
    }

    fun togglePlotSync(plotId: String): Boolean {
        val plot = plots[plotId] ?: return false

        if (!plot.syncEnabled) {
            return false // Can't re-enable sync
        }

        plot.disableSync()
        savePlot(plot)
        return true
    }
}