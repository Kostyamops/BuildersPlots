package com.kostyamops.buildersplots.data

import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.ServerType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask

class AutoSaveManager(private val plugin: BuildersPlots, private val worldManager: WorldManager) {

    private var autoSaveTask: BukkitTask? = null

    fun startAutoSave() {
        autoSaveTask?.cancel()

        val autoSaveInterval = plugin.config.getLong("world-autosave-interval", 10)

        if (autoSaveInterval <= 0) {
            plugin.localizationManager.info("plotmanager.autosave.disabled")
            return
        }

        if (plugin.serverType == ServerType.TEST) {
            val intervalTicks = autoSaveInterval * 60 * 20
            autoSaveTask = object : BukkitRunnable() {
                override fun run() {
                    plugin.localizationManager.info("plotmanager.autosave.start")
                    worldManager.saveAllPlotWorlds()
                    plugin.localizationManager.info("plotmanager.autosave.complete")
                }
            }.runTaskTimer(plugin, intervalTicks, intervalTicks)

            plugin.localizationManager.info("plotmanager.autosave.enabled",
                "%interval%" to autoSaveInterval.toString())
        }
    }

    fun stopAutoSave() {
        autoSaveTask?.cancel()
        autoSaveTask = null
        plugin.localizationManager.info("plotmanager.autosave.stopped")
    }
}