package com.kostyamops.buildersplots.commands.subcommands

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Interface for BuildersPlots subcommands
 * @author Kostyamops
 * @updated 2025-08-04 02:48:35
 */
interface SubCommand {
    /**
     * Execute the command
     */
    fun execute(player: Player, args: Array<out String>)

    /**
     * Get the name of the command
     */
    fun getName(): String

    /**
     * Get command aliases
     */
    fun getAliases(): List<String> = emptyList()

    /**
     * Handle tab completion - принимает CommandSender вместо Player
     */
    fun tabComplete(sender: CommandSender, args: Array<out String>): List<String>? = null
}