package com.kostyamops.buildersplots.commands.subcommands

import com.kostyamops.buildersplots.BuildersPlots
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Command to check connection with the test server
 */
class PingCommand(private val plugin: BuildersPlots) : SubCommand {

    override fun execute(player: Player, args: Array<out String>) {
        plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.ping_sending")

        // Run ping asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val pingFuture = plugin.communicationManager.pingAsync()

                // Wait for result (with timeout)
                val pingTime = pingFuture.get()

                // Process the result on the main thread
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (pingTime >= 0) {
                        // Ping successful
                        plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.ping_success",
                            "%time%" to pingTime.toString())
                    } else {
                        // Ping failed
                        plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.ping_failed")
                    }
                })

            } catch (e: Exception) {
                // Handle error on the main thread
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    plugin.localizationManager.sendMessage(player, "messages.buildersplotscommand.ping_error",
                        "%error%" to (e.message ?: "Unknown error"))
                })
            }
        })
    }

    override fun getName(): String = "ping"
}