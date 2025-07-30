package ru.joutak.buildersplots.commands

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import ru.joutak.buildersplots.BuildersPlots
import ru.joutak.buildersplots.model.Plot
import ru.joutak.buildersplots.network.ServerRole
import java.util.*

class PlotCommand(private val plugin: BuildersPlots) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(plugin.messages.get("commands.player-only"))
            return true
        }

        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "create" -> handleCreate(sender, args)
            "list" -> handleList(sender)
            "tp", "teleport" -> handleTeleport(sender, args)
            "info" -> handleInfo(sender)
            "ping" -> handlePing(sender)
            "delete" -> handleDelete(sender, args)
            "help" -> sendHelp(sender)
            else -> {
                sender.sendMessage(plugin.messages.get("commands.unknown-command"))
            }
        }

        return true
    }

    private fun handleCreate(player: Player, args: Array<out String>) {
        // Проверяем, на каком сервере выполняется команда
        if (plugin.config.serverRole == ServerRole.RECEIVER) {
            player.sendMessage(plugin.messages.get("network.receiver-only"))
            return
        }
        
        if (args.size < 2) {
            player.sendMessage(plugin.messages.get("commands.create-usage"))
            return
        }
        
        val plotName = args[1]
        
        // Создаем плот
        val plot = plugin.plotManager.createPlot(player, plotName)
        if (plot != null) {
            player.sendMessage(plugin.messages.get("general.plot-created", plotName))
            
            // Отправляем на RECEIVER
            try {
                player.sendMessage(plugin.messages.get("network.sending-plot", plotName))
                val success = plugin.networkManager.createPlotOnReceiver(plot)
                
                if (success) {
                    player.sendMessage(plugin.messages.get("general.plot-sent", plotName))
                } else {
                    player.sendMessage(plugin.messages.get("network.plot-sent-failed", "Receiver reported failure"))
                }
            } catch (e: Exception) {
                player.sendMessage(plugin.messages.get("network.plot-sent-failed", e.message ?: "Unknown error"))
                plugin.logger.warning("[BuildersPlots] Failed to send plot to receiver: ${e.message}")
            }
        }
    }
    
    private fun handleList(player: Player) {
        val plots = plugin.plotManager.getPlots()
        if (plots.isEmpty()) {
            player.sendMessage(plugin.messages.get("general.plots-empty"))
            return
        }
        
        player.sendMessage(plugin.messages.get("general.plots-list-header", plots.size))
        plots.forEach { plot ->
            val ownerName = plugin.server.getOfflinePlayer(plot.owner).name ?: "Unknown"
            player.sendMessage("§a- §e${plot.name} §7(${plot.getWidth()}x${plot.getHeight()}x${plot.getLength()}, ${ownerName})")
        }
    }
    
    private fun handleTeleport(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(plugin.messages.get("commands.tp-usage"))
            return
        }
        
        val plotName = args[1]
        val plot = plugin.plotManager.getPlotByName(plotName)
        
        if (plot == null) {
            player.sendMessage(plugin.messages.get("general.plot-not-found", plotName))
            return
        }
        
        val location = plot.getCenter()
        if (location == null) {
            player.sendMessage(plugin.messages.get("general.world-not-found", plot.worldName))
            return
        }
        
        player.teleport(location)
        player.sendMessage(plugin.messages.get("general.plot-teleported", plot.name))
    }
    
    private fun handleInfo(player: Player) {
        val version = plugin.description.version
        player.sendMessage(plugin.messages.get("general.plugin-info", version))
        player.sendMessage(plugin.messages.get("general.plugin-description"))
        player.sendMessage(plugin.messages.get("general.plugin-author"))
        player.sendMessage(plugin.messages.get("general.plugin-api", plugin.server.bukkitVersion))
        
        // Show server role
        val roleText = when (plugin.config.serverRole) {
            ServerRole.SENDER -> "SENDER (Test Server)"
            ServerRole.RECEIVER -> "RECEIVER (Main Server)"
        }
        player.sendMessage("§7Server role: §f$roleText")
        
        // Show network settings
        player.sendMessage("§7Network: §f${plugin.config.receiverHost}:${plugin.config.receiverPort}")
        
        // Show plot stats
        val plots = plugin.plotManager.getPlots()
        player.sendMessage("§7Plots: §f${plots.size}")
    }

    private fun handlePing(player: Player) {
        val targetRole = when (plugin.config.serverRole) {
            ServerRole.SENDER -> ServerRole.RECEIVER
            ServerRole.RECEIVER -> ServerRole.SENDER
        }

        player.sendMessage(plugin.messages.get("network.ping-sending", targetRole.name))

        // Use Folia's async scheduler
        try {
            plugin.server.asyncScheduler.runNow(plugin, { task ->
                val (success, latency) = plugin.networkManager.pingServer(targetRole)

                // Run callback on the player's region scheduler
                plugin.server.regionScheduler.execute(plugin, player.location) {
                    if (success) {
                        player.sendMessage(plugin.messages.get("network.ping-success", targetRole.name, latency))
                    } else {
                        player.sendMessage(plugin.messages.get("network.ping-failed", targetRole.name, "Connection failed"))
                    }
                }
            })
        } catch (e: Exception) {
            // Fallback for non-Folia servers
            try {
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    val (success, latency) = plugin.networkManager.pingServer(targetRole)

                    plugin.server.scheduler.runTask(plugin, Runnable {
                        if (success) {
                            player.sendMessage(plugin.messages.get("network.ping-success", targetRole.name, latency))
                        } else {
                            player.sendMessage(plugin.messages.get("network.ping-failed", targetRole.name, "Connection failed"))
                        }
                    })
                })
            } catch (e: Exception) {
                player.sendMessage(plugin.messages.get("network.ping-failed", targetRole.name, e.message ?: "Scheduler error"))
                plugin.logger.warning("[BuildersPlots] Error scheduling ping: ${e.message}")
            }
        }
    }
    
    private fun handleDelete(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(plugin.messages.get("commands.delete-usage"))
            return
        }
        
        val plotName = args[1]
        
        if (plugin.plotManager.deletePlot(plotName)) {
            player.sendMessage(plugin.messages.get("general.plot-deleted", plotName))
        } else {
            player.sendMessage(plugin.messages.get("general.plot-not-found", plotName))
        }
    }
    
    private fun sendHelp(player: Player) {
        player.sendMessage(plugin.messages.get("commands.help-header"))
        player.sendMessage(plugin.messages.get("commands.help-create"))
        player.sendMessage(plugin.messages.get("commands.help-list"))
        player.sendMessage(plugin.messages.get("commands.help-tp"))
        player.sendMessage(plugin.messages.get("commands.help-info"))
        player.sendMessage(plugin.messages.get("commands.help-ping"))
        player.sendMessage(plugin.messages.get("commands.help-delete"))
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.size == 1) {
            return listOf("create", "list", "tp", "teleport", "info", "ping", "delete", "help")
                .filter { it.startsWith(args[0].lowercase()) }
        } else if (args.size == 2 && (args[0].equals("tp", true) || 
                                     args[0].equals("teleport", true) ||
                                     args[0].equals("delete", true))) {
            return plugin.plotManager.getPlots()
                .map { it.name }
                .filter { it.startsWith(args[1], true) }
        }
        return emptyList()
    }
}