package com.kostyamops.buildersplots.commands

import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.ServerType
import com.kostyamops.buildersplots.ui.PlotListMenu
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Main command handler for BuildersPlots plugin
 * @author Kostyamops
 * @updated 2025-08-04 01:35:44
 */
class BuildersPlotsCommand(private val plugin: BuildersPlots) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            plugin.localizationManager.sendMessage(sender, "messages.buildersplotscommand.player_only")
            return true
        }

        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "create" -> {
                if (plugin.serverType != ServerType.MAIN) {
                    plugin.localizationManager.sendMessage(sender, "messages.buildersplotscommand.create_main_only")
                    return true
                }

                if (args.size < 3) {
                    plugin.localizationManager.sendMessage(sender, "messages.buildersplotscommand.create_usage")
                    return true
                }

                val plotName = args[1]
                val radius = args[2].toIntOrNull()

                if (radius == null) {
                    plugin.localizationManager.sendMessage(sender, "messages.buildersplotscommand.radius_number")
                    return true
                }

                val plot = plugin.plotManager.createPlot(plotName, radius, sender)
                if (plot == null) {
                    plugin.localizationManager.sendMessage(sender, "messages.buildersplotscommand.create_failed")
                } else {
                    plugin.localizationManager.sendMessage(sender, "messages.buildersplotscommand.create_success",
                        "%name%" to plot.name, "%radius%" to plot.radius.toString())
                }
            }

            "list" -> {
                PlotListMenu(plugin).openMenu(sender)
            }

            "delete" -> {
                if (args.size < 2) {
                    plugin.localizationManager.sendMessage(sender, "messages.buildersplotscommand.delete_usage")
                    return true
                }

                val plotName = args[1]
                if (plugin.plotManager.deletePlot(plotName)) {
                    plugin.localizationManager.sendMessage(sender, "messages.buildersplotscommand.delete_success",
                        "%name%" to plotName)
                } else {
                    plugin.localizationManager.sendMessage(sender, "messages.buildersplotscommand.plot_not_found",
                        "%name%" to plotName)
                }
            }

            "tp", "teleport" -> {
                if (plugin.serverType != ServerType.TEST) {
                    plugin.localizationManager.sendMessage(sender, "messages.buildersplotscommand.tp_test_only")
                    return true
                }

                if (args.size < 2) {
                    plugin.localizationManager.sendMessage(sender, "messages.buildersplotscommand.tp_usage")
                    return true
                }

                val plotName = args[1]
                val plot = plugin.plotManager.getPlot(plotName)

                if (plot == null) {
                    plugin.localizationManager.sendMessage(sender, "messages.buildersplotscommand.plot_not_found",
                        "%name%" to plotName)
                    return true
                }

                val world = Bukkit.getWorld(plot.getTestWorldName())
                if (world == null) {
                    plugin.localizationManager.sendMessage(sender, "messages.buildersplotscommand.plot_world_not_found",
                        "%name%" to plotName)
                    return true
                }

                sender.teleport(world.getSpawnLocation())
                plugin.localizationManager.sendMessage(sender, "messages.buildersplotscommand.tp_success",
                    "%name%" to plotName)
            }

            "help" -> {
                sendHelp(sender)
            }

            "leave" -> {
                if (plugin.serverType != ServerType.TEST) {
                    plugin.localizationManager.sendMessage(sender, "messages.buildersplotscommand.leave_test_only")
                    return true
                }

                val worldName = sender.world.name
                val worldPrefix = plugin.config.getString("plot-world-prefix", "plot_")

                if (!worldPrefix?.let { worldName.startsWith(it) }!!) {
                    plugin.localizationManager.sendMessage(sender, "messages.buildersplotscommand.not_in_plot_world")
                    return true
                }

                val mainWorld = Bukkit.getWorlds()[0]
                sender.teleport(mainWorld.spawnLocation)
                plugin.localizationManager.sendMessage(sender, "messages.buildersplotscommand.returned_main_world")

                plugin.plotManager.playerLeftPlotWorld(worldName)
            }

            else -> {
                plugin.localizationManager.sendMessage(sender, "messages.buildersplotscommand.unknown_command")
            }
        }

        return true
    }

    private fun sendHelp(player: Player) {
        plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.help_header")
        plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.help_create")
        plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.help_list")
        plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.help_delete")

        if (plugin.serverType == ServerType.TEST) {
            plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.help_tp")
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String>? {
        if (args.size == 1) {
            val subCommands = mutableListOf("create", "list", "delete", "help")
            if (plugin.serverType == ServerType.TEST) {
                subCommands.add("tp")
            }
            return subCommands.filter { it.startsWith(args[0].lowercase()) }
        }

        if (args.size == 2 && (args[0].equals("delete", ignoreCase = true) || args[0].equals("tp", ignoreCase = true))) {
            val plotNames = plugin.plotManager.getAllPlots().map { it.name }
            return plotNames.filter { it.startsWith(args[1], ignoreCase = true) }
        }

        return null
    }
}