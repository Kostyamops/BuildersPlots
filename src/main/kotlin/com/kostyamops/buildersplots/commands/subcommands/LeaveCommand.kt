package com.kostyamops.buildersplots.commands.subcommands

import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.ServerType
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Command to leave a plot world
 */
class LeaveCommand(private val plugin: BuildersPlots) : SubCommand {

    override fun execute(player: Player, args: Array<out String>) {
        if (plugin.serverType != ServerType.TEST) {
            plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.leave_test_only")
            return
        }

        val worldName = player.world.name
        val worldPrefix = plugin.config.getString("plot-world-prefix", "plot_")

        if (!worldPrefix?.let { worldName.startsWith(it) }!!) {
            plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.not_in_plot_world")
            return
        }

        val mainWorld = Bukkit.getWorlds()[0]
        player.teleport(mainWorld.spawnLocation)
        plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.returned_main_world")

        plugin.plotManager.playerLeftPlotWorld(worldName)
    }

    override fun getName(): String = "leave"
}