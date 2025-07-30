package ru.joutak.buildersplots.commands

import ru.joutak.buildersplots.BuildersPlots

class CommandManager(private val plugin: BuildersPlots) {
    
    fun registerCommands() {
        val plotCommand = PlotCommand(plugin)
        plugin.getCommand("bp")?.setExecutor(plotCommand)
        plugin.getCommand("bp")?.tabCompleter = plotCommand
        plugin.logger.info("[BuildersPlots] Commands registered")
    }
}