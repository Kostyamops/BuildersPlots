package com.kostyamops.buildersplots.listeners

import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.ServerType
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.scheduler.BukkitRunnable

class WorldLoadingListener(private val plugin: BuildersPlots) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        if (plugin.serverType != ServerType.TEST) return

        val targetWorld = event.to.world
        val currentWorld = event.from.world

        if (targetWorld.name == currentWorld.name) return

        if (isPlotWorld(targetWorld.name)) {
            plugin.plotManager.playerEnteredPlotWorld(targetWorld.name)
        }

        if (isPlotWorld(currentWorld.name)) {
            object : BukkitRunnable() {
                override fun run() {
                    if (event.player.world.name != currentWorld.name) {
                        plugin.plotManager.playerLeftPlotWorld(currentWorld.name)
                    }
                }
            }.runTaskLater(plugin, 5L)
        }
    }

    @EventHandler
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        if (plugin.serverType != ServerType.TEST) return

        val fromWorld = event.from
        val toWorld = event.player.world

        if (isPlotWorld(toWorld.name)) {
            plugin.plotManager.playerEnteredPlotWorld(toWorld.name)
        }

        if (isPlotWorld(fromWorld.name)) {
            plugin.plotManager.playerLeftPlotWorld(fromWorld.name)
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (plugin.serverType != ServerType.TEST) return

        val world = event.player.world

        if (isPlotWorld(world.name)) {
            val remainingPlayers = world.players.size - 1 // -1 потому что игрок еще считается в мире

            if (remainingPlayers <= 0) {
                plugin.plotManager.playerLeftPlotWorld(world.name)
            }
        }
    }
    private fun isPlotWorld(worldName: String): Boolean {
        return worldName.startsWith("plot_")
    }
}