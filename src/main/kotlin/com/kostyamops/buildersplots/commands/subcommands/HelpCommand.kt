package com.kostyamops.buildersplots.commands.subcommands

import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.ServerType
import org.bukkit.entity.Player

/**
 * Command to display help information
 */
class HelpCommand(private val plugin: BuildersPlots) : SubCommand {

    override fun execute(player: Player, args: Array<out String>) {
        plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.help_header")
        plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.help_ping")
        plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.help_create")
        plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.help_list")
        plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.help_members")
        plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.help_delete")

        if (plugin.serverType == ServerType.TEST) {
            plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.help_tp")
            plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.help_leave")
        }
    }

    override fun getName(): String = "help"
}