package com.kostyamops.buildersplots.commands.subcommands

import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.ui.PlotListMenu
import org.bukkit.entity.Player

/**
 * Command to show plot list
 */
class ListCommand(private val plugin: BuildersPlots) : SubCommand {

    override fun execute(player: Player, args: Array<out String>) {
        PlotListMenu(plugin).openMenu(player)
    }

    override fun getName(): String = "list"
}