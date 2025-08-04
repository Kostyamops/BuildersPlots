package com.kostyamops.buildersplots.commands.subcommands

import com.kostyamops.buildersplots.BuildersPlots
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Command to delete a plot
 */
class DeleteCommand(private val plugin: BuildersPlots) : SubCommand {

    override fun execute(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.delete_usage")
            return
        }

        val plotName = args[1]
        if (plugin.plotManager.deletePlot(plotName)) {
            plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.delete_success",
                "%name%" to plotName)
        } else {
            plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.plot_not_found",
                "%name%" to plotName)
        }
    }

    override fun getName(): String = "delete"

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String>? {
        if (args.isEmpty()) {
            val plotNames = plugin.plotManager.getAllPlots().map { it.name }
            return plotNames
        }

        val plotNames = plugin.plotManager.getAllPlots().map { it.name }
        return plotNames.filter { it.startsWith(args[0], ignoreCase = true) }
    }
}