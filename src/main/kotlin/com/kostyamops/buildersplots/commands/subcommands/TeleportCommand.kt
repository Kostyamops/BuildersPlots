package com.kostyamops.buildersplots.commands.subcommands

import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.ServerType
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Command to teleport to a plot world
 */
class TeleportCommand(private val plugin: BuildersPlots) : SubCommand {

    override fun execute(player: Player, args: Array<out String>) {
        if (plugin.serverType != ServerType.TEST) {
            plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.tp_test_only")
            return
        }

        if (args.size < 2) {
            plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.tp_usage")
            return
        }

        val plotName = args[1]
        val plot = plugin.plotManager.getPlot(plotName)

        if (plot == null) {
            plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.plot_not_found",
                "%name%" to plotName)
            return
        }

        val world = Bukkit.getWorld(plot.getTestWorldName())
        if (world == null) {
            plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.plot_world_not_found",
                "%name%" to plotName)
            return
        }

        player.teleport(world.getSpawnLocation())
        plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.tp_success",
            "%name%" to plotName)
    }

    override fun getName(): String = "tp"

    override fun getAliases(): List<String> = listOf("teleport")

    override fun tabComplete(player: CommandSender, args: Array<out String>): List<String>? {
        if (args.isEmpty()) {
            val plotNames = plugin.plotManager.getAllPlots().map { it.name }
            return plotNames
        }

        val plotNames = plugin.plotManager.getAllPlots().map { it.name }
        return plotNames.filter { it.startsWith(args[0], ignoreCase = true) }
    }
}