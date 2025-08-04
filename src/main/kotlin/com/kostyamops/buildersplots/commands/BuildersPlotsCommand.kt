package com.kostyamops.buildersplots.commands

import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.commands.subcommands.*
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Main command handler for BuildersPlots plugin
 */
class BuildersPlotsCommand(private val plugin: BuildersPlots) : CommandExecutor, TabCompleter {

    private val subcommands = mutableMapOf<String, SubCommand>()
    private val alwaysAvailableCommands = listOf("help", "ping")

    init {
        // Register all subcommands
        registerSubCommand(HelpCommand(plugin))
        registerSubCommand(PingCommand(plugin))
        registerSubCommand(CreateCommand(plugin))
        registerSubCommand(ListCommand(plugin))
        registerSubCommand(DeleteCommand(plugin))
        registerSubCommand(TeleportCommand(plugin))
        registerSubCommand(LeaveCommand(plugin))
    }

    private fun registerSubCommand(subCommand: SubCommand) {
        subcommands[subCommand.getName()] = subCommand

        // Register aliases if any
        subCommand.getAliases().forEach { alias ->
            subcommands[alias] = subCommand
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            plugin.localizationManager.sendMessage(sender, "messages.buildersplotscommand.player_only")
            return true
        }

        if (args.isEmpty()) {
            subcommands["help"]?.execute(sender, args)
            return true
        }

        val subCommand = args[0].lowercase()

        // Always allow help and ping commands
        if (subCommand in alwaysAvailableCommands) {
            subcommands[subCommand]?.execute(sender, args)
            return true
        }

        // Check connection status for other commands
        if (!plugin.communicationManager.isConnected()) {
            plugin.localizationManager.sendMessage(sender, "messages.buildersplotscommand.no_connection")
            plugin.localizationManager.sendMessage(sender, "messages.buildersplotscommand.try_ping")
            return true
        }

        // Execute the subcommand if it exists
        if (subcommands.containsKey(subCommand)) {
            subcommands[subCommand]?.execute(sender, args)
        } else {
            plugin.localizationManager.sendMessage(sender, "messages.buildersplotscommand.unknown_command")
        }

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String>? {
        if (args.size == 1) {
            // Always show help and ping in tab completion
            val commands = alwaysAvailableCommands.toMutableList()

            // Only show other commands if connected
            if (plugin.communicationManager.isConnected()) {
                subcommands.keys
                    .filter { it !in alwaysAvailableCommands }
                    .distinct()
                    .forEach { commands.add(it) }
            }

            return commands.distinct().filter { it.startsWith(args[0].lowercase()) }
        }

        // Let the subcommand handle its own tab completion
        val subCommand = args[0].lowercase()
        if (subcommands.containsKey(subCommand)) {
            return subcommands[subCommand]?.tabComplete(sender, args.copyOfRange(1, args.size))
        }

        return null
    }
}