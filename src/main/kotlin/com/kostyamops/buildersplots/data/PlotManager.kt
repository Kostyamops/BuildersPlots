package com.kostyamops.buildersplots.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.ServerType
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class PlotManager(val plugin: BuildersPlots) {

    private val plots = ConcurrentHashMap<String, Plot>()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val plotsFile = File(plugin.dataFolder, "plots.json")

    val worldManager = WorldManager(plugin, this)
    val autoSaveManager = AutoSaveManager(plugin, worldManager)
    val worldCleanup = PlotWorldCleanup(plugin, this)

    init {
        loadPlots()

        if (!worldManager.plotsWorldsFolder.exists()) {
            worldManager.plotsWorldsFolder.mkdirs()
            plugin.localizationManager.info("plotmanager.directory.created",
                "%path%" to worldManager.plotsWorldsFolder.absolutePath)
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
            worldCleanup.safelyDeletePlotWorld(worldName, plot)
        }

        plots.remove(name)
        savePlots()

        plugin.communicationManager.sendPlotDeletion(name)
        plugin.localizationManager.info("plotmanager.plot.deleted", "%name%" to name)

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

    fun ensurePlotWorldLoaded(plotName: String): Boolean {
        return worldManager.ensurePlotWorldLoaded(plotName)
    }

    fun playerEnteredPlotWorld(worldName: String) {
        worldManager.playerEnteredPlotWorld(worldName)
    }

    fun playerLeftPlotWorld(worldName: String) {
        worldManager.playerLeftPlotWorld(worldName)
    }

    fun startAutoSave() {
        autoSaveManager.startAutoSave()
    }

    fun loadAllPlotWorlds() {
        worldManager.loadAllPlotWorlds()
    }

    fun stopAutoSave() {
        autoSaveManager.stopAutoSave()
    }

    fun addMemberToPlot(plotName: String, player: Player, targetUUID: UUID, targetName: String): Boolean {
        val plot = getPlot(plotName) ?: return false

        // Check if player has permission to add members
        if (!plot.isCreator(player) && !player.isOp() && !player.hasPermission("buildersplots.admin.member.manage")) {
            return false
        }

        // Add member
        if (!plot.addMember(targetUUID, targetName)) {
            return false // Member already exists
        }

        // Save changes
        savePlots()

        // Send update to other server if on MAIN server
        if (plugin.serverType == ServerType.MAIN) {
            plugin.communicationManager.sendPlotCreation(plot)
        }

        plugin.localizationManager.info("plotmanager.member.added",
            "%plot%" to plotName, "%player%" to targetName)

        return true
    }

    /**
     * Removes a member from a plot
     * @param plotName name of the plot
     * @param player player executing the action (must be creator or admin)
     * @param targetUUID UUID of the member to remove
     * @return true if member was successfully removed
     */
    fun removeMemberFromPlot(plotName: String, player: Player, targetUUID: UUID): Boolean {
        val plot = getPlot(plotName) ?: return false

        // Check if player has permission to remove members
        if (!plot.isCreator(player) && !player.isOp() && !player.hasPermission("buildersplots.admin.member.manage")) {
            return false
        }

        // Remove member
        if (!plot.removeMember(targetUUID)) {
            return false // Member not found
        }

        // Save changes
        savePlots()

        // Send update to other server if on MAIN server
        if (plugin.serverType == ServerType.MAIN) {
            plugin.communicationManager.sendPlotCreation(plot)
        }

        plugin.localizationManager.info("plotmanager.member.removed",
            "%plot%" to plotName, "%uuid%" to targetUUID.toString())

        return true
    }

    /**
     * Gets the list of members for a plot
     * @param plotName name of the plot
     * @return list of members or null if plot not found
     */
    fun getPlotMembers(plotName: String): List<PlotMember>? {
        val plot = getPlot(plotName) ?: return null
        return plot.members.toList()
    }

    fun shutdown() {
        stopAutoSave()
        worldManager.cancelAllUnloadTasks()
        worldManager.saveAllPlotWorlds()
        worldManager.unloadAllPlotWorlds()
        plugin.localizationManager.info("plotmanager.shutdown.complete")
    }
}