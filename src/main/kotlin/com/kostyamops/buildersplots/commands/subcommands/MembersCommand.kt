package com.kostyamops.buildersplots.commands.subcommands

import com.kostyamops.buildersplots.BuildersPlots
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Command for managing plot members
 * @author Kostyamops
 * @updated 2025-08-04 03:39:56
 */
class MembersCommand(private val plugin: BuildersPlots) : SubCommand {

    override fun execute(player: Player, args: Array<out String>) {
        // Check permission
        if (!player.hasPermission("buildersplots.command.members")) {
            plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.no_permission")
            return
        }

        if (args.size < 3) {
            plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.members_usage")
            return
        }

        val plotName = args[1]
        val action = args[2].lowercase()

        val plot = plugin.plotManager.getPlot(plotName)
        if (plot == null) {
            plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.plot_not_found",
                "%name%" to plotName)
            return
        }

        // Check if player has permission to manage members
        if (!plot.isCreator(player) && !player.isOp() && !player.hasPermission("buildersplots.admin.member.manage")) {
            plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.not_owner")
            return
        }

        when (action) {
            "list" -> {
                // Get and display list of members
                val members = plugin.plotManager.getPlotMembers(plotName)
                if (members.isNullOrEmpty()) {
                    plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.no_members",
                        "%name%" to plotName)
                    return
                }

                plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.members_header",
                    "%name%" to plotName,
                    "%count%" to members.size.toString())

                for (member in members) {
                    plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.member_entry",
                        "%name%" to member.name)
                }
            }

            "add" -> {
                if (args.size < 4) {
                    plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.members_add_usage")
                    return
                }

                val targetName = args[3]
                val targetPlayer = Bukkit.getPlayer(targetName)

                if (targetPlayer == null) {
                    plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.player_not_found",
                        "%name%" to targetName)
                    return
                }

                if (plugin.plotManager.addMemberToPlot(plotName, player, targetPlayer.uniqueId, targetPlayer.name)) {
                    plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.member_added",
                        "%name%" to targetName,
                        "%plot%" to plotName)

                    // Notify the added player
                    plugin.localizationManager.sendMessage(targetPlayer, "messages.buildersplotscommand.added_to_plot",
                        "%plot%" to plotName,
                        "%owner%" to player.name)
                } else {
                    plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.member_already_exists",
                        "%name%" to targetName,
                        "%plot%" to plotName)
                }
            }

            "remove", "delete" -> {
                if (args.size < 4) {
                    plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.members_remove_usage")
                    return
                }

                val targetName = args[3]
                val targetPlayer = Bukkit.getOfflinePlayer(targetName)

                if (plugin.plotManager.removeMemberFromPlot(plotName, player, targetPlayer.uniqueId)) {
                    plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.member_removed",
                        "%name%" to targetName,
                        "%plot%" to plotName)

                    // Notify the removed player if online
                    if (targetPlayer.isOnline) {
                        plugin.localizationManager.sendMessage(targetPlayer.player!!, "messages.buildersplotscommand.removed_from_plot",
                            "%plot%" to plotName,
                            "%owner%" to player.name)
                    }
                } else {
                    plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.member_not_found",
                        "%name%" to targetName,
                        "%plot%" to plotName)
                }
            }

            else -> {
                plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.members_unknown_action")
            }
        }
    }

    override fun getName(): String = "members"

    override fun getAliases(): List<String> = listOf("member")

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String>? {
        if (args.isEmpty()) {
            val plotNames = plugin.plotManager.getAllPlots().map { it.name }
            return plotNames
        }

        if (args.size == 1) {
            val plotNames = plugin.plotManager.getAllPlots().map { it.name }
            return plotNames.filter { it.startsWith(args[0], ignoreCase = true) }
        }

        if (args.size == 2) {
            val actions = listOf("add", "remove", "delete", "list")
            return actions.filter { it.startsWith(args[1], ignoreCase = true) }
        }

        if (args.size == 3 && (args[1].equals("add", ignoreCase = true) ||
                    args[1].equals("remove", ignoreCase = true) ||
                    args[1].equals("delete", ignoreCase = true))) {
            return Bukkit.getOnlinePlayers()
                .map { it.name }
                .filter { it.startsWith(args[2], ignoreCase = true) }
        }

        return null
    }
}