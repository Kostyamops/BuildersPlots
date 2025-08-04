package com.kostyamops.buildersplots.commands.subcommands

import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.ServerType
import org.bukkit.entity.Player

/**
 * Command to create a new plot
 */
class CreateCommand(private val plugin: BuildersPlots) : SubCommand {

    override fun execute(player: Player, args: Array<out String>) {
        if (plugin.serverType != ServerType.MAIN) {
            plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.create_main_only")
            return
        }

        if (args.size < 3) {
            plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.create_usage")
            return
        }

        val plotName = args[1]
        val radius = args[2].toIntOrNull()

        if (radius == null) {
            plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.radius_number")
            return
        }

        val plot = plugin.plotManager.createPlot(plotName, radius, player)
        if (plot == null) {
            plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.create_failed")
        } else {
            plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.create_success",
                "%name%" to plot.name, "%radius%" to plot.radius.toString())
        }
    }

    override fun getName(): String = "create"
}