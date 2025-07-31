package com.kostyamops.buildersplots.commands

import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.ServerType
import com.kostyamops.buildersplots.ui.PlotListMenu
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class BuildersPlotsCommand(private val plugin: BuildersPlots) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("${ChatColor.RED}This command can only be used by players.")
            return true
        }
        
        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }
        
        when (args[0].lowercase()) {
            "create" -> {
                // Only allow plot creation on main server
                if (plugin.serverType != ServerType.MAIN) {
                    sender.sendMessage("${ChatColor.RED}Plot creation is only allowed on the main server.")
                    return true
                }
                
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.RED}Usage: /bp create <name> <radius>")
                    return true
                }
                
                val plotName = args[1]
                val radius = args[2].toIntOrNull()
                
                if (radius == null) {
                    sender.sendMessage("${ChatColor.RED}Radius must be a number.")
                    return true
                }
                
                val plot = plugin.plotManager.createPlot(plotName, radius, sender)
                if (plot == null) {
                    sender.sendMessage("${ChatColor.RED}Failed to create plot. Plot name might already exist or radius is invalid.")
                } else {
                    sender.sendMessage("${ChatColor.GREEN}Plot '${plot.name}' created with radius ${plot.radius}.")
                }
            }
            
            "list" -> {
                PlotListMenu(plugin).openMenu(sender)
            }
            
            "delete" -> {
                if (args.size < 2) {
                    sender.sendMessage("${ChatColor.RED}Usage: /bp delete <name>")
                    return true
                }
                
                val plotName = args[1]
                if (plugin.plotManager.deletePlot(plotName)) {
                    sender.sendMessage("${ChatColor.GREEN}Plot '$plotName' deleted successfully.")
                } else {
                    sender.sendMessage("${ChatColor.RED}Plot '$plotName' not found.")
                }
            }
            
            "tp", "teleport" -> {
                // Only allow teleportation on test server
                if (plugin.serverType != ServerType.TEST) {
                    sender.sendMessage("${ChatColor.RED}Plot teleportation is only allowed on the test server.")
                    return true
                }
                
                if (args.size < 2) {
                    sender.sendMessage("${ChatColor.RED}Usage: /bp tp <name>")
                    return true
                }
                
                val plotName = args[1]
                val plot = plugin.plotManager.getPlot(plotName)
                
                if (plot == null) {
                    sender.sendMessage("${ChatColor.RED}Plot '$plotName' not found.")
                    return true
                }
                
                val world = Bukkit.getWorld(plot.getTestWorldName())
                if (world == null) {
                    sender.sendMessage("${ChatColor.RED}Plot world for '$plotName' not found.")
                    return true
                }
                
                sender.teleport(world.getSpawnLocation())
                sender.sendMessage("${ChatColor.GREEN}Teleported to plot '$plotName'.")
            }
            
            "help" -> {
                sendHelp(sender)
            }

            "leave" -> {
                // Only process on the test server
                if (plugin.serverType != ServerType.TEST) {
                    sender.sendMessage("${ChatColor.RED}Данная команда доступна только на тестовом сервере.")
                    return true
                }

                // Check if player is in a plot world
                val worldName = sender.world.name
                val worldPrefix = plugin.config.getString("plot-world-prefix", "plot_")

                if (!worldPrefix?.let { worldName.startsWith(it) }!!) {
                    sender.sendMessage("${ChatColor.RED}Вы не находитесь в мире плота!")
                    return true
                }

                // Teleport to the main world
                val mainWorld = Bukkit.getWorlds()[0]
                sender.teleport(mainWorld.spawnLocation)
                sender.sendMessage("${ChatColor.GREEN}Вы вернулись в основной мир.")

                // Notify the plot manager that player left the world
                plugin.plotManager.playerLeftPlotWorld(worldName)
            }

            else -> {
                sender.sendMessage("${ChatColor.RED}Unknown command. Use /bp help for help.")
            }
        }
        
        return true
    }

    private fun sendHelp(player: Player) {
        player.sendMessage("${ChatColor.GOLD}=== BuildersPlots Commands ===")
        player.sendMessage("${ChatColor.YELLOW}/bp create <name> <radius> ${ChatColor.WHITE}- Create a new plot")
        player.sendMessage("${ChatColor.YELLOW}/bp list ${ChatColor.WHITE}- List all available plots")
        player.sendMessage("${ChatColor.YELLOW}/bp delete <name> ${ChatColor.WHITE}- Delete a plot")
        
        if (plugin.serverType == ServerType.TEST) {
            player.sendMessage("${ChatColor.YELLOW}/bp tp <name> ${ChatColor.WHITE}- Teleport to a plot")
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