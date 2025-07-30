package com.kostyamops.buildersplots.listeners

import com.kostyamops.buildersplots.BuildersPlots
import io.papermc.paper.event.block.BlockBreakBlockEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent

class BlockListener(private val plugin: BuildersPlots) : Listener {
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        // Only track changes on the main server
        if (!plugin.configManager.isMainServer) return
        
        val block = event.block
        
        plugin.networkManager.sendBlockChange(
            world = block.world.name,
            x = block.x,
            y = block.y,
            z = block.z,
            material = block.type,
            blockData = block.blockData
        )
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        // Only track changes on the main server
        if (!plugin.configManager.isMainServer) return
        
        val block = event.block
        
        plugin.networkManager.sendBlockChange(
            world = block.world.name,
            x = block.x,
            y = block.y,
            z = block.z,
            material = event.block.type, // This will be AIR after the event
            blockData = null
        )
    }
    
    // This is a Folia-specific event for blocks being broken by other means
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreakBlock(event: BlockBreakBlockEvent) {
        // Only track changes on the main server
        if (!plugin.configManager.isMainServer) return
        
        val block = event.block
        
        plugin.networkManager.sendBlockChange(
            world = block.world.name,
            x = block.x,
            y = block.y,
            z = block.z,
            material = event.block.type,
            blockData = null
        )
    }
}