package com.kostyamops.buildersplots.ui

import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.ServerType
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

class PlotListMenu(private val plugin: BuildersPlots) {
    
    fun openMenu(player: Player) {
        val plots = plugin.plotManager.getAllPlots()
        
        if (plots.isEmpty()) {
            player.sendMessage("${ChatColor.YELLOW}No plots have been created yet.")
            return
        }
        
        // Calculate inventory size (must be multiple of 9)
        val inventorySize = (plots.size / 9 + 1) * 9
        val inventory = Bukkit.createInventory(null, inventorySize.coerceAtMost(54), "Available Plots")
        
        plots.forEachIndexed { index, plot ->
            if (index < 54) { // Max inventory size
                val item = ItemStack(Material.MAP)
                val meta = item.itemMeta
                
                meta.setDisplayName("${ChatColor.GREEN}${plot.name}")
                
                val lore = mutableListOf(
                    "${ChatColor.GRAY}Creator: ${Bukkit.getOfflinePlayer(plot.creatorUUID).name}",
                    "${ChatColor.GRAY}Radius: ${plot.radius} blocks",
                    "${ChatColor.GRAY}World: ${plot.center.world}"
                )
                
                if (plugin.serverType == ServerType.TEST) {
                    lore.add("")
                    lore.add("${ChatColor.YELLOW}Click to teleport")
                }
                
                meta.lore = lore
                item.itemMeta = meta
                
                inventory.setItem(index, item)
            }
        }
        
        player.openInventory(inventory)
        
        // Register inventory click listener
        Bukkit.getPluginManager().registerEvents(PlotMenuListener(plugin, inventory), plugin)
    }
}