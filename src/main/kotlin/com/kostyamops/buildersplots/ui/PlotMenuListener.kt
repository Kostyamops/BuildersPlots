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
            val worldName = plot.getTestWorldName(plugin)
            val world = Bukkit.getWorld(worldName)

            if (world == null) {
                plugin.localizationManager.sendMessage(player, "messages.plotmenulistener.world_not_found",
                    "%name%" to displayName)
                return
            }

            player.closeInventory()
            player.teleport(world.getSpawnLocation())
            plugin.localizationManager.sendMessage(player, "messages.plotmenulistener.teleported",
                "%name%" to displayName)
        } else {
            plugin.localizationManager.sendMessage(player, "messages.plotmenulistener.plot_info",
                "%name%" to plot.name,
                "%radius%" to plot.radius.toString())
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.inventory == inventory) {
            HandlerList.unregisterAll(this)
        }
    }
}