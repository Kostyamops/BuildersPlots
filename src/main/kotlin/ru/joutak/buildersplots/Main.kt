package ru.joutak.buildersplots

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() {

    override fun onEnable() {
        logger.info("Плагин BuildersPlots включён!")
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (command.name.equals("bp", ignoreCase = true) || command.name.equals("buildersplots", ignoreCase = true)) {
            sender.sendMessage("§aПлагин BuildersPlots работает")
            return true
        }
        return false
    }
}
