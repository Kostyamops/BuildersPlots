package com.kostyamops.buildersplots.commands

import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.gui.PlotSelectionMenu // Add this import
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.*

class CommandManager(private val plugin: BuildersPlots) : CommandExecutor, TabCompleter {
    
    fun registerCommands() {
        val pluginCommand = plugin.getCommand("bp")
        if (pluginCommand != null) {
            pluginCommand.setExecutor(this)
            pluginCommand.tabCompleter = this
        }
    }
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("§6BuildersPlots §7- §eVersion ${plugin.description.version}")
            sender.sendMessage("§7Use §e/bp help §7for commands")
            return true
        }
        
        when (args[0].lowercase()) {
            "help" -> {
                showHelp(sender)
            }
            "create" -> {
                if (!plugin.configManager.isMainServer) {
                    sender.sendMessage("§cThis command can only be used on the main server!")
                    return true
                }

                if (sender !is Player) {
                    sender.sendMessage("§cOnly players can use this command!")
                    return true
                }

                if (args.size < 3) {
                    sender.sendMessage("§cUsage: /bp create <name> <radius>")
                    return true
                }

                val name = args[1]
                val radius = args[2].toIntOrNull()

                if (radius == null || radius <= 0) {
                    sender.sendMessage("§cRadius must be a positive number!")
                    return true
                }

                if (radius > plugin.configManager.maxPlotSize) {
                    sender.sendMessage("§cRadius cannot exceed ${plugin.configManager.maxPlotSize}!")
                    return true
                }

                val location = sender.location
                val centerX = location.blockX
                val centerZ = location.blockZ

                // Используем новый метод с начальной синхронизацией
                val plot = plugin.plotManager.createPlotWithInitialSync(
                    name = name,
                    originalWorld = location.world.name,
                    centerX = centerX,
                    centerZ = centerZ,
                    radius = radius,
                    creatorUuid = sender.uniqueId
                )

                if (plot != null) {
                    // Больше не нужно отправлять отдельный пакет CREATE, так как
                    // createPlotWithInitialSync уже делает это и добавляет синхронизацию

                    sender.sendMessage("§aСоздан плот §e$name §aс радиусом §e$radius§a! Началась синхронизация блоков.")
                    sender.sendMessage("§7Центр: §e$centerX, $centerZ")
                    sender.sendMessage("§7Плот будет полностью скопирован на тестовый сервер.")
                } else {
                    sender.sendMessage("§cНе удалось создать плот! Проверьте логи сервера.")
                }
            }
            "list" -> {
                if (sender !is Player) {
                    sender.sendMessage("§cOnly players can use this command!")
                    return true
                }

                try {
                    // Логгируем для отладки
                    plugin.log("Открываем меню выбора плотов для ${sender.name}")

                    // Сначала проверяем, есть ли плоты вообще
                    val plots = plugin.plotManager.getAllPlots()
                    if (plots.isEmpty()) {
                        sender.sendMessage("§cНет доступных плотов. Создайте новый плот с помощью /bp create.")
                        return true
                    }

                    // Открываем меню
                    plugin.server.globalRegionScheduler.execute(plugin, Runnable {
                        try {
                            PlotSelectionMenu(plugin).open(sender)
                        } catch (e: Exception) {
                            plugin.log("Ошибка при открытии меню: ${e.message}")
                            e.printStackTrace()
                            sender.sendMessage("§cОшибка при открытии меню. Подробности в логах сервера.")
                        }
                    })
                } catch (e: Exception) {
                    plugin.log("Ошибка при обработке команды list: ${e.message}")
                    e.printStackTrace()
                    sender.sendMessage("§cПроизошла ошибка. Подробности в логах сервера.")
                }

                return true
            }
            "delete" -> {
                if (sender !is Player && args.size < 2) {
                    sender.sendMessage("§cConsole must specify a plot name: /bp delete <name>")
                    return true
                }
                
                val plotName = if (args.size >= 2) args[1] else null
                val plot = if (plotName != null) {
                    plugin.plotManager.getPlotByName(plotName)
                } else {
                    null
                }
                
                if (plot == null) {
                    sender.sendMessage("§cPlot not found! Use /bp list to see available plots.")
                    return true
                }
                
                // Check permissions
                if (sender is Player && !sender.isOp && plot.createdBy != sender.uniqueId) {
                    sender.sendMessage("§cYou don't have permission to delete this plot!")
                    return true
                }
                
                // Delete the plot
                if (plugin.plotManager.deletePlot(plot.id)) {
                    // Send delete command to other server
                    val plotData = mapOf(
                        "plotId" to plot.id.toString()
                    )
                    
                    plugin.networkManager.sendPlotManagement("DELETE", plotData)
                    
                    sender.sendMessage("§aDeleted plot §e${plot.name}§a!")
                } else {
                    sender.sendMessage("§cFailed to delete plot! See console for details.")
                }
            }
            "addmember" -> {
                if (args.size < 3) {
                    sender.sendMessage("§cUsage: /bp addmember <plot> <player>")
                    return true
                }
                
                val plotName = args[1]
                val playerName = args[2]
                
                val plot = plugin.plotManager.getPlotByName(plotName)
                if (plot == null) {
                    sender.sendMessage("§cPlot not found!")
                    return true
                }
                
                // Check permissions
                if (sender is Player && !sender.isOp && plot.createdBy != sender.uniqueId) {
                    sender.sendMessage("§cYou don't have permission to add members to this plot!")
                    return true
                }
                
                // Get player UUID
                val offlinePlayer = plugin.server.getOfflinePlayer(playerName)
                if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline) {
                    sender.sendMessage("§cPlayer not found!")
                    return true
                }
                
                // Add member
                if (plugin.plotManager.addMemberToPlot(plot.id, offlinePlayer.uniqueId)) {
                    // Send add member command to other server
                    val plotData = mapOf(
                        "plotId" to plot.id.toString(),
                        "playerUuid" to offlinePlayer.uniqueId.toString()
                    )
                    
                    plugin.networkManager.sendPlotManagement("ADD_MEMBER", plotData)
                    
                    sender.sendMessage("§aAdded §e$playerName §ato plot §e${plot.name}§a!")
                } else {
                    sender.sendMessage("§cPlayer is already a member of this plot!")
                }
            }
            "removemember" -> {
                if (args.size < 3) {
                    sender.sendMessage("§cUsage: /bp removemember <plot> <player>")
                    return true
                }
                
                val plotName = args[1]
                val playerName = args[2]
                
                val plot = plugin.plotManager.getPlotByName(plotName)
                if (plot == null) {
                    sender.sendMessage("§cPlot not found!")
                    return true
                }
                
                // Check permissions
                if (sender is Player && !sender.isOp && plot.createdBy != sender.uniqueId) {
                    sender.sendMessage("§cYou don't have permission to remove members from this plot!")
                    return true
                }
                
                // Get player UUID
                val offlinePlayer = plugin.server.getOfflinePlayer(playerName)
                
                // Remove member
                if (plugin.plotManager.removeMemberFromPlot(plot.id, offlinePlayer.uniqueId)) {
                    // Send remove member command to other server
                    val plotData = mapOf(
                        "plotId" to plot.id.toString(),
                        "playerUuid" to offlinePlayer.uniqueId.toString()
                    )
                    
                    plugin.networkManager.sendPlotManagement("REMOVE_MEMBER", plotData)
                    
                    sender.sendMessage("§aRemoved §e$playerName §afrom plot §e${plot.name}§a!")
                } else {
                    sender.sendMessage("§cPlayer is not a member of this plot or is the owner!")
                }
            }
            "disablesync" -> {
                if (args.size < 2) {
                    sender.sendMessage("§cUsage: /bp disablesync <plot>")
                    return true
                }
                
                val plotName = args[1]
                
                val plot = plugin.plotManager.getPlotByName(plotName)
                if (plot == null) {
                    sender.sendMessage("§cPlot not found!")
                    return true
                }
                
                // Check permissions
                if (sender is Player && !sender.isOp && plot.createdBy != sender.uniqueId) {
                    sender.sendMessage("§cYou don't have permission to disable sync for this plot!")
                    return true
                }
                
                if (!plot.syncEnabled) {
                    sender.sendMessage("§cSync is already disabled for this plot!")
                    return true
                }
                
                // Confirm prompt
                if (args.size < 3 || args[2] != "confirm") {
                    sender.sendMessage("§c⚠ WARNING: This action cannot be undone! ⚠")
                    sender.sendMessage("§cOnce sync is disabled, this plot will no longer receive updates from the main server.")
                    sender.sendMessage("§cTo confirm, type: §e/bp disablesync ${plot.name} confirm")
                    return true
                }
                
                // Disable sync
                if (plugin.plotManager.disablePlotSync(plot.id)) {
                    // Send disable sync command to other server
                    val plotData = mapOf(
                        "plotId" to plot.id.toString()
                    )
                    
                    plugin.networkManager.sendPlotManagement("DISABLE_SYNC", plotData)
                    
                    sender.sendMessage("§aDisabled sync for plot §e${plot.name}§a!")
                    sender.sendMessage("§7This plot will no longer receive updates from the main server.")
                } else {
                    sender.sendMessage("§cFailed to disable sync! See console for details.")
                }
            }
            "info" -> {
                if (args.size < 2 && sender !is Player) {
                    sender.sendMessage("§cConsole must specify a plot name: /bp info <name>")
                    return true
                }
                
                val plot = if (args.size >= 2) {
                    plugin.plotManager.getPlotByName(args[1])
                } else if (sender is Player) {
                    // If player is standing in a plot, show that plot's info
                    val playerLocation = (sender as Player).location
                    plugin.plotManager.getPlotByLocation(playerLocation) ?:
                    // Otherwise, try to find a plot owned by the player
                    plugin.plotManager.getPlayerPlots(sender.uniqueId).firstOrNull()
                } else {
                    null
                }
                
                if (plot == null) {
                    sender.sendMessage("§cPlot not found! Use /bp list to see available plots.")
                    return true
                }
                
                // Display plot info
                sender.sendMessage("§6Plot Information: §e${plot.name}")
                sender.sendMessage("§7Owner: §e${plugin.server.getOfflinePlayer(plot.createdBy).name}")
                sender.sendMessage("§7Created: §e${Date(plot.createdAt)}")
                sender.sendMessage("§7Coordinates: §e${plot.centerX}, ${plot.centerZ} §7(radius: §e${plot.radius}§7)")
                sender.sendMessage("§7Original World: §e${plot.originalWorld}")
                sender.sendMessage("§7Sync Enabled: §e${if (plot.syncEnabled) "Yes" else "No"}")
                
                // List members
                val members = plot.members.mapNotNull { plugin.server.getOfflinePlayer(it).name }
                if (members.isNotEmpty()) {
                    sender.sendMessage("§7Members: §e${members.joinToString(", ")}")
                } else {
                    sender.sendMessage("§7Members: §eNone")
                }
            }
            else -> {
                sender.sendMessage("§cUnknown command. Use /bp help for help.")
            }
        }
        
        return true
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String>? {
        if (args.isEmpty()) return null
        
        val completions = mutableListOf<String>()
        
        when (args.size) {
            1 -> {
                // First argument - subcommand
                val subcommands = listOf("help", "create", "list", "delete", "addmember", "removemember", "disablesync", "info")
                completions.addAll(subcommands.filter { it.startsWith(args[0].lowercase()) })
            }
            2 -> {
                // Second argument - usually plot name
                when (args[0].lowercase()) {
                    "delete", "addmember", "removemember", "disablesync", "info" -> {
                        // Complete with plot names
                        val plotNames = plugin.plotManager.getAllPlots().map { it.name }
                        completions.addAll(plotNames.filter { it.startsWith(args[1], ignoreCase = true) })
                    }
                }
            }
            3 -> {
                // Third argument
                when (args[0].lowercase()) {
                    "addmember", "removemember" -> {
                        // Complete with player names
                        val playerNames = plugin.server.onlinePlayers.map { it.name }
                        completions.addAll(playerNames.filter { it.startsWith(args[2], ignoreCase = true) })
                    }
                    "disablesync" -> {
                        // Offer "confirm" as the only option
                        if ("confirm".startsWith(args[2], ignoreCase = true)) {
                            completions.add("confirm")
                        }
                    }
                }
            }
        }
        
        return completions
    }
    
    private fun showHelp(sender: CommandSender) {
        sender.sendMessage("§6BuildersPlots §7- §eCommands:")
        sender.sendMessage("§e/bp help §7- Show this help")
        sender.sendMessage("§e/bp create <name> <radius> §7- Create a new plot")
        sender.sendMessage("§e/bp list §7- Show plots you have access to")
        sender.sendMessage("§e/bp delete <plot> §7- Delete a plot")
        sender.sendMessage("§e/bp addmember <plot> <player> §7- Add a player to a plot")
        sender.sendMessage("§e/bp removemember <plot> <player> §7- Remove a player from a plot")
        sender.sendMessage("§e/bp disablesync <plot> §7- Disable synchronization from main server")
        sender.sendMessage("§e/bp info [plot] §7- Show information about a plot")
    }
}