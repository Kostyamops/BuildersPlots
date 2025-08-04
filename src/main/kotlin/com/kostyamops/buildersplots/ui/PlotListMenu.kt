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
            plugin.localizationManager.sendMessage(player, "messages.plotlistmenu.no_plots")
            return
        }

        val inventorySize = (plots.size / 9 + 1) * 9
        val inventory = Bukkit.createInventory(
            null,
            inventorySize.coerceAtMost(54),
            plugin.localizationManager.getMessage("messages.plotlistmenu.inventory_title")
        )

        plots.forEachIndexed { index, plot ->
            if (index < 54) { // Max inventory size
                val item = ItemStack(Material.MAP)
                val meta = item.itemMeta

                meta.setDisplayName(plugin.localizationManager.getMessage(
                    "messages.plotlistmenu.plot_name",
                    "%name%" to plot.name
                ))

                val lore = mutableListOf(
                    plugin.localizationManager.getMessage(
                        "messages.plotlistmenu.creator",
                        "%creator%" to (Bukkit.getOfflinePlayer(plot.creatorUUID).name ?: "Unknown")
                    ),
                    plugin.localizationManager.getMessage(
                        "messages.plotlistmenu.radius",
                        "%radius%" to plot.radius.toString()
                    ),
                    plugin.localizationManager.getMessage(
                        "messages.plotlistmenu.world",
                        "%world%" to plot.center.world
                    )
                )

                if (plugin.serverType == ServerType.TEST) {
                    lore.add("")
                    lore.add(plugin.localizationManager.getMessage("messages.plotlistmenu.click_teleport"))
                }

                meta.lore = lore
                item.itemMeta = meta

                inventory.setItem(index, item)
            }
        }

        player.openInventory(inventory)

        Bukkit.getPluginManager().registerEvents(PlotMenuListener(plugin, inventory), plugin)
    }
}