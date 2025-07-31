package com.kostyamops.buildersplots.ui

import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.ServerType
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory

class PlotMenuListener(private val plugin: BuildersPlots, private val inventory: Inventory) : Listener {
    
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.inventory != inventory) return
        
        event.isCancelled = true
        
        val player = event.whoClicked as? Player ?: return
        val clickedItem = event.currentItem ?: return
        
        if (!clickedItem.hasItemMeta() || !clickedItem.itemMeta.hasDisplayName()) return

        val rawName = clickedItem.itemMeta.displayName
        val displayName = ChatColor.stripColor(rawName) ?: return
        val plot = plugin.plotManager.getPlot(displayName) ?: return
        
        if (plugin.serverType == ServerType.TEST) {
            val worldName = plot.getTestWorldName()
            val world = Bukkit.getWorld(worldName)
            
            if (world == null) {
                player.sendMessage("${ChatColor.RED}Plot world for '$displayName' not found.")
                return
            }
            
            player.closeInventory()
            player.teleport(world.getSpawnLocation())
            player.sendMessage("${ChatColor.GREEN}Teleported to plot '$displayName'.")
        } else {
            player.sendMessage("${ChatColor.YELLOW}Plot information: ${plot.name} (Radius: ${plot.radius})")
        }
    }
    
    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.inventory == inventory) {
            HandlerList.unregisterAll(this)
        }
    }
}