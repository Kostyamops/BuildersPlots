package ru.joutak.buildersplots.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerTeleportEvent
import ru.joutak.buildersplots.BuildersPlots

class PlotInteractionListener(private val plugin: BuildersPlots) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val toWorld = event.to.world

        // Check if teleporting to a plot world
        if (!toWorld.name.startsWith("plot_")) return

        val plotId = toWorld.name.removePrefix("plot_")
        val plot = plugin.plotManager.getPlot(plotId) ?: return

        // Check if player has access to the plot
        if (!plot.hasMember(event.player.uniqueId) && !event.player.isOp) {
            event.isCancelled = true
            event.player.sendMessage("§cYou don't have access to this plot.")
        }
    }
}