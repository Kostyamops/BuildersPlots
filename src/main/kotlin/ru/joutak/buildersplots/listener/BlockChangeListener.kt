package ru.joutak.buildersplots.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import ru.joutak.buildersplots.BuildersPlots
import ru.joutak.buildersplots.model.BlockChange

class BlockChangeListener(private val plugin: BuildersPlots) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        // Send block placement to the receiver server
        val blockChange = BlockChange(
            worldName = event.block.world.name,
            x = event.block.x,
            y = event.block.y,
            z = event.block.z,
            blockData = event.blockPlaced.blockData.asString
        )

        plugin.networkManager.sendBlockChange(blockChange)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        // Send block break to the receiver server (air block)
        val blockChange = BlockChange(
            worldName = event.block.world.name,
            x = event.block.x,
            y = event.block.y,
            z = event.block.z,
            blockData = "minecraft:air"
        )

        plugin.networkManager.sendBlockChange(blockChange)
    }
}