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

    /**
     * Обрабатывает попытки телепортации в миры плотов
     */
    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        // Выполняем только на тестовом сервере
        if (plugin.serverType != ServerType.TEST) return

        val targetWorld = event.to.world
        val currentWorld = event.from.world

        // Если игрок телепортируется в тот же мир, ничего не делаем
        if (targetWorld.name == currentWorld.name) return

        // Проверяем, является ли целевой мир миром плота
        if (isPlotWorld(targetWorld.name)) {
            // Мир плота уже загружен, просто отмечаем вход игрока
            plugin.plotManager.playerEnteredPlotWorld(targetWorld.name)
        }

        // Проверяем, покидает ли игрок мир плота
        if (isPlotWorld(currentWorld.name)) {
            // Планируем проверку после телепортации, чтобы избежать ложных срабатываний
            object : BukkitRunnable() {
                override fun run() {
                    if (event.player.world.name != currentWorld.name) {
                        plugin.plotManager.playerLeftPlotWorld(currentWorld.name)
                    }
                }
            }.runTaskLater(plugin, 5L) // Небольшая задержка для надежности
        }
    }

    /**
     * Обрабатывает изменение мира игроком
     */
    @EventHandler
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        // Выполняем только на тестовом сервере
        if (plugin.serverType != ServerType.TEST) return

        val fromWorld = event.from
        val toWorld = event.player.world

        // Если игрок вошел в мир плота
        if (isPlotWorld(toWorld.name)) {
            plugin.plotManager.playerEnteredPlotWorld(toWorld.name)
        }

        // Если игрок покинул мир плота
        if (isPlotWorld(fromWorld.name)) {
            plugin.plotManager.playerLeftPlotWorld(fromWorld.name)
        }
    }

    /**
     * Обрабатывает выход игрока с сервера
     */
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        // Выполняем только на тестовом сервере
        if (plugin.serverType != ServerType.TEST) return

        val world = event.player.world

        // Если игрок был в мире плота
        if (isPlotWorld(world.name)) {
            // Проверяем, был ли это последний игрок в мире
            val remainingPlayers = world.players.size - 1 // -1 потому что игрок еще считается в мире

            if (remainingPlayers <= 0) {
                plugin.plotManager.playerLeftPlotWorld(world.name)
            }
        }
    }

    /**
     * Проверяет, является ли мир миром плота
     */
    private fun isPlotWorld(worldName: String): Boolean {
        return worldName.startsWith("plot_")
    }
}