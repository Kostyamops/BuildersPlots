package ru.joutak.buildersplots.manager

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import ru.joutak.buildersplots.BuildersPlots
import ru.joutak.buildersplots.model.PlotCreationRequest
import ru.joutak.buildersplots.network.ServerRole
import java.util.*

class CommandManager(private val plugin: BuildersPlots) : CommandExecutor, TabCompleter {

    fun registerCommands() {
        val pluginCommand = plugin.getCommand("bp")
        if (pluginCommand != null) {
            pluginCommand.setExecutor(this)
            pluginCommand.tabCompleter = this
        } else {
            plugin.logger.severe("Failed to register BuildersPlots command!")
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("§6BuildersPlots §7- §fHelp menu")
            sender.sendMessage("§6/bp create <name> <x,y,z> <radius> §7- Create a new plot")
            sender.sendMessage("§6/bp list §7- List your available plots")
            sender.sendMessage("§6/bp tp <id> §7- Teleport to a plot")
            sender.sendMessage("§6/bp add <id> <player> §7- Add a player to your plot")
            sender.sendMessage("§6/bp remove <id> <player> §7- Remove a player from your plot")
            sender.sendMessage("§6/bp delete <id> §7- Delete a plot")
            sender.sendMessage("§6/bp sync <id> §7- Disable synchronization for a plot")
            return true
        }

        when (args[0].lowercase()) {
            "create" -> handleCreateCommand(sender, args)
            "list" -> handleListCommand(sender)
            "tp" -> handleTeleportCommand(sender, args)
            "add" -> handleAddPlayerCommand(sender, args)
            "remove" -> handleRemovePlayerCommand(sender, args)
            "delete" -> handleDeleteCommand(sender, args)
            "sync" -> handleSyncCommand(sender, args)
            else -> {
                sender.sendMessage("§cUnknown command. Use /bp for help.")
            }
        }

        return true
    }

    private fun handleCreateCommand(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage("§cThis command can only be used by players.")
            return
        }

        if (plugin.config.serverRole != ServerRole.SENDER) {
            sender.sendMessage("§cPlots can only be created from the main server.")
            return
        }

        if (args.size < 4) {
            sender.sendMessage("§cUsage: /bp create <name> <x,y,z> <radius>")
            return
        }

        val plotName = args[1]

        // Parse coordinates
        val coords = args[2].split(",")
        if (coords.size != 3) {
            sender.sendMessage("§cCoordinates must be in the format x,y,z")
            return
        }

        val x = coords[0].toIntOrNull()
        val y = coords[1].toIntOrNull()
        val z = coords[2].toIntOrNull()

        if (x == null || y == null || z == null) {
            sender.sendMessage("§cInvalid coordinates. Must be numbers.")
            return
        }

        val radius = args[3].toIntOrNull()
        if (radius == null || radius <= 0) {
            sender.sendMessage("§cRadius must be a positive number.")
            return
        }

        // Create the plot request
        val request = PlotCreationRequest(
            plotName = plotName,
            owner = sender.uniqueId,
            worldName = sender.world.name,
            x = x,
            y = y,
            z = z,
            radius = radius
        )

        // Send to receiver server
        plugin.networkManager.sendPlotCreationRequest(request).thenAccept { success ->
            if (success) {
                sender.sendMessage("§aPlot creation request sent. The plot will be created shortly.")
            } else {
                sender.sendMessage("§cFailed to send plot creation request. Please try again later.")
            }
        }
    }

    private fun handleListCommand(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage("§cThis command can only be used by players.")
            return
        }

        if (plugin.config.serverRole != ServerRole.RECEIVER) {
            sender.sendMessage("§cPlots can only be listed on the test server.")
            return
        }

        val plots = plugin.plotManager.getPlayerPlots(sender)

        if (plots.isEmpty()) {
            sender.sendMessage("§cYou don't have any plots.")
            return
        }

        sender.sendMessage("§6Your plots:")
        plots.forEach { plot ->
            val ownerTag = if (plot.owner == sender.uniqueId) "§7(Owner)" else ""
            val syncStatus = if (plot.syncEnabled) "§a[Sync ON]" else "§c[Sync OFF]"
            sender.sendMessage("§6${plot.id}: §f${plot.name} $ownerTag $syncStatus")
        }
    }

    private fun handleTeleportCommand(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage("§cThis command can only be used by players.")
            return
        }

        if (plugin.config.serverRole != ServerRole.RECEIVER) {
            sender.sendMessage("§cYou can only teleport to plots on the test server.")
            return
        }

        if (args.size < 2) {
            sender.sendMessage("§cUsage: /bp tp <id>")
            return
        }

        val plotId = args[1]
        val plot = plugin.plotManager.getPlot(plotId)

        if (plot == null) {
            sender.sendMessage("§cPlot not found.")
            return
        }

        if (!plot.hasMember(sender.uniqueId) && !sender.isOp) {
            sender.sendMessage("§cYou don't have access to this plot.")
            return
        }

        val plotWorld = Bukkit.getWorld("plot_${plot.id}")
        if (plotWorld == null) {
            sender.sendMessage("§cPlot world not found.")
            return
        }

        // Teleport to the center of the plot
        val spawnX = plot.radius.toDouble()
        val spawnY = 100.0 // Safe height
        val spawnZ = plot.radius.toDouble()

        sender.teleport(org.bukkit.Location(plotWorld, spawnX, spawnY, spawnZ))
        sender.sendMessage("§aTeleported to plot: §6${plot.name}")
    }

    private fun handleAddPlayerCommand(sender: CommandSender, args: Array<out String>) {
        if (plugin.config.serverRole != ServerRole.RECEIVER) {
            sender.sendMessage("§cPlayer management can only be done on the test server.")
            return
        }

        if (args.size < 3) {
            sender.sendMessage("§cUsage: /bp add <id> <player>")
            return
        }

        val plotId = args[1]
        val plot = plugin.plotManager.getPlot(plotId)

        if (plot == null) {
            sender.sendMessage("§cPlot not found.")
            return
        }

        if (sender is Player && plot.owner != sender.uniqueId && !sender.isOp) {
            sender.sendMessage("§cOnly the plot owner can add members.")
            return
        }

        val targetPlayer = Bukkit.getPlayerExact(args[2])
        if (targetPlayer == null) {
            sender.sendMessage("§cPlayer not found.")
            return
        }

        if (plot.hasMember(targetPlayer.uniqueId)) {
            sender.sendMessage("§cPlayer is already a member of this plot.")
            return
        }

        plot.addMember(targetPlayer.uniqueId)
        sender.sendMessage("§aAdded §6${targetPlayer.name}§a to plot §6${plot.name}")
        targetPlayer.sendMessage("§aYou have been added to plot §6${plot.name}")
    }

    private fun handleRemovePlayerCommand(sender: CommandSender, args: Array<out String>) {
        if (plugin.config.serverRole != ServerRole.RECEIVER) {
            sender.sendMessage("§cPlayer management can only be done on the test server.")
            return
        }

        if (args.size < 3) {
            sender.sendMessage("§cUsage: /bp remove <id> <player>")
            return
        }

        val plotId = args[1]
        val plot = plugin.plotManager.getPlot(plotId)

        if (plot == null) {
            sender.sendMessage("§cPlot not found.")
            return
        }

        if (sender is Player && plot.owner != sender.uniqueId && !sender.isOp) {
            sender.sendMessage("§cOnly the plot owner can remove members.")
            return
        }

        // Get UUID of player to remove (they might be offline)
        val playerName = args[2]
        val targetUuid = Bukkit.getOfflinePlayer(playerName).uniqueId

        if (targetUuid == plot.owner) {
            sender.sendMessage("§cCannot remove the plot owner.")
            return
        }

        if (!plot.hasMember(targetUuid)) {
            sender.sendMessage("§cPlayer is not a member of this plot.")
            return
        }

        plot.removeMember(targetUuid)
        sender.sendMessage("§aRemoved §6$playerName§a from plot §6${plot.name}")

        // Notify player if they're online
        Bukkit.getPlayer(targetUuid)?.sendMessage("§cYou have been removed from plot §6${plot.name}")
    }

    private fun handleDeleteCommand(sender: CommandSender, args: Array<out String>) {
        if (plugin.config.serverRole != ServerRole.RECEIVER) {
            sender.sendMessage("§cPlots can only be deleted on the test server.")
            return
        }

        if (args.size < 2) {
            sender.sendMessage("§cUsage: /bp delete <id>")
            return
        }

        val plotId = args[1]
        val plot = plugin.plotManager.getPlot(plotId)

        if (plot == null) {
            sender.sendMessage("§cPlot not found.")
            return
        }

        if (sender is Player && plot.owner != sender.uniqueId && !sender.isOp) {
            sender.sendMessage("§cOnly the plot owner can delete this plot.")
            return
        }

        if (plugin.plotManager.deletePlot(plotId)) {
            sender.sendMessage("§aDeleted plot §6${plot.name}")
        } else {
            sender.sendMessage("§cFailed to delete plot.")
        }
    }

    private fun handleSyncCommand(sender: CommandSender, args: Array<out String>) {
        if (plugin.config.serverRole != ServerRole.RECEIVER) {
            sender.sendMessage("§cSync settings can only be modified on the test server.")
            return
        }

        if (args.size < 2) {
            sender.sendMessage("§cUsage: /bp sync <id>")
            return
        }

        val plotId = args[1]
        val plot = plugin.plotManager.getPlot(plotId)

        if (plot == null) {
            sender.sendMessage("§cPlot not found.")
            return
        }

        if (sender is Player && plot.owner != sender.uniqueId && !sender.isOp) {
            sender.sendMessage("§cOnly the plot owner can modify sync settings.")
            return
        }

        if (!plot.syncEnabled) {
            sender.sendMessage("§cSync is already disabled and cannot be re-enabled.")
            return
        }

        if (plugin.plotManager.togglePlotSync(plotId)) {
            sender.sendMessage("§aSynchronization from main server has been §cDISABLED§a for plot §6${plot.name}")
            sender.sendMessage("§cWarning: This cannot be undone!")
        } else {
            sender.sendMessage("§cFailed to modify sync settings.")
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String>? {
        if (command.name.equals("bp", ignoreCase = true)) {
            if (args.size == 1) {
                val completions = listOf("create", "list", "tp", "add", "remove", "delete", "sync")
                return completions.filter { it.startsWith(args[0].lowercase()) }
            } else if (args.size == 2) {
                when (args[0].lowercase()) {
                    "tp", "add", "remove", "delete", "sync" -> {
                        if (sender is Player && plugin.config.serverRole == ServerRole.RECEIVER) {
                            val plots = plugin.plotManager.getPlayerPlots(sender)
                            return plots.map { it.id }.filter { it.startsWith(args[1]) }
                        }
                    }
                }
            } else if (args.size == 3) {
                when (args[0].lowercase()) {
                    "add", "remove" -> {
                        return Bukkit.getOnlinePlayers()
                            .map { it.name }
                            .filter { it.startsWith(args[2], ignoreCase = true) }
                    }
                }
            }
        }
        return null
    }
}