package com.kostyamops.buildersplots.listeners

import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.ServerType
import com.kostyamops.buildersplots.network.model.BlockChangeData
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent

class BlockChangeListener(private val plugin: BuildersPlots) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (plugin.serverType == ServerType.MAIN) {
            val location = event.block.location
            val plot = plugin.plotManager.getPlotAtLocation(location)

            if (plot != null) {
                Bukkit.getServer().getRegionScheduler().execute(plugin, location.world,
                    location.blockX shr 4, location.blockZ shr 4, Runnable {
                        val blockData = BlockChangeData(
                            type = "PLACE",
                            material = event.block.type.name,
                            blockData = event.block.blockData.asString,
                            plotName = plot.name,
                            x = location.blockX,
                            y = location.blockY,
                            z = location.blockZ
                        )

                        plugin.communicationManager.sendBlockChange(blockData)
                    })
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (plugin.serverType == ServerType.MAIN) {
            val location = event.block.location
            val plot = plugin.plotManager.getPlotAtLocation(location)

            if (plot != null) {
                Bukkit.getServer().getRegionScheduler().execute(plugin, location.world,
                    location.blockX shr 4, location.blockZ shr 4, Runnable {
                        val blockData = BlockChangeData(
                            type = "BREAK",
                            material = "AIR",
                            blockData = "minecraft:air",
                            plotName = plot.name,
                            x = location.blockX,
                            y = location.blockY,
                            z = location.blockZ
                        )

                        plugin.communicationManager.sendBlockChange(blockData)
                    })
            }
        }
    }
}