package com.kostyamops.buildersplots.gui

import com.kostyamops.buildersplots.BuildersPlots
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import java.text.SimpleDateFormat
import java.util.*

class PlotSelectionMenu(private val plugin: BuildersPlots) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")
    private val plotIdKey = NamespacedKey(plugin, "plot-id")

    fun open(player: Player) {
        // Get plots the player has access to
        val plots = if (player.isOp) {
            plugin.plotManager.getAllPlots().toList()
        } else {
            plugin.plotManager.getPlayerPlots(player.uniqueId)
        }

        // Calculate inventory size (multiple of 9, at least 9, at most 54)
        val size = ((plots.size + 8) / 9) * 9
        val adjustedSize = size.coerceIn(9, 54)

        // Create inventory
        val inventory = Bukkit.createInventory(null, adjustedSize, "Your Plots")

        // Add plot items
        plots.forEachIndexed { index, plot ->
            if (index < adjustedSize) {
                val icon = if (plot.syncEnabled) Material.GREEN_CONCRETE else Material.RED_CONCRETE
                val item = ItemStack(icon)
                val meta = item.itemMeta

                if (meta != null) {
                    meta.setDisplayName("${ChatColor.GOLD}${plot.name}")

                    val lore = mutableListOf<String>()
                    lore.add("${ChatColor.GRAY}Location: ${ChatColor.WHITE}${plot.centerX}, ${plot.centerZ}")
                    lore.add("${ChatColor.GRAY}Radius: ${ChatColor.WHITE}${plot.radius}")
                    lore.add("${ChatColor.GRAY}Created: ${ChatColor.WHITE}${dateFormat.format(Date(plot.createdAt))}")

                    if (plot.syncEnabled) {
                        lore.add("${ChatColor.GREEN}Sync Enabled")
                    } else {
                        lore.add("${ChatColor.RED}Sync Disabled")
                    }

                    if (plot.createdBy == player.uniqueId) {
                        lore.add("${ChatColor.GOLD}You are the owner")
                    } else if (player.isOp) {
                        lore.add("${ChatColor.YELLOW}Admin access")
                    } else {
                        lore.add("${ChatColor.GRAY}You are a member")
                    }

                    lore.add("")
                    lore.add("${ChatColor.YELLOW}Click to teleport")

                    meta.lore = lore

                    // Store plot ID in item's persistent data container
                    meta.persistentDataContainer.set(plotIdKey, PersistentDataType.STRING, plot.id.toString())

                    item.itemMeta = meta
                }

                inventory.setItem(index, item)
            }
        }

        // Open inventory for player
        player.openInventory(inventory)

        // Register click listener for this inventory
        plugin.server.pluginManager.registerEvents(object : org.bukkit.event.Listener {
            @org.bukkit.event.EventHandler
            fun onInventoryClick(event: org.bukkit.event.inventory.InventoryClickEvent) {
                if (event.view.title == "Your Plots" && event.whoClicked is Player) {
                    event.isCancelled = true

                    val clickedItem = event.currentItem ?: return
                    val meta = clickedItem.itemMeta ?: return

                    val plotIdString = meta.persistentDataContainer.get(plotIdKey, PersistentDataType.STRING)
                    if (plotIdString != null && plotIdString.isNotEmpty()) {
                        try {
                            val plotId = UUID.fromString(plotIdString)
                            val plot = plugin.plotManager.getPlot(plotId)

                            if (plot != null) {
                                // Close inventory
                                event.whoClicked.closeInventory()

                                // Teleport to plot if on test server
                                if (!plugin.configManager.isMainServer) {
                                    val targetWorld = Bukkit.getWorld(plot.worldName)
                                    if (targetWorld != null) {
                                        // Teleport to the center of the plot
                                        val target = org.bukkit.Location(
                                            targetWorld,
                                            plot.radius.toDouble(),
                                            100.0, // Y coordinate, could be better to find the highest block
                                            plot.radius.toDouble()
                                        )

                                        // Make sure we teleport to a safe location
                                        target.y = targetWorld.getHighestBlockYAt(target.blockX, target.blockZ) + 1.0

                                        // Use Folia's teleport method
                                        val player = event.whoClicked as Player
                                        player.teleportAsync(target).thenAccept { success ->
                                            if (success) {
                                                player.sendMessage("${ChatColor.GREEN}Teleported to plot ${ChatColor.GOLD}${plot.name}")
                                            } else {
                                                player.sendMessage("${ChatColor.RED}Failed to teleport to plot")
                                            }
                                        }
                                    } else {
                                        (event.whoClicked as Player).sendMessage("${ChatColor.RED}Plot world not found!")
                                    }
                                } else {
                                    // On main server, we just teleport to the plot location
                                    val targetWorld = Bukkit.getWorld(plot.originalWorld)
                                    if (targetWorld != null) {
                                        val target = org.bukkit.Location(
                                            targetWorld,
                                            plot.centerX.toDouble(),
                                            100.0,
                                            plot.centerZ.toDouble()
                                        )

                                        // Make sure we teleport to a safe location
                                        target.y = targetWorld.getHighestBlockYAt(target.blockX, target.blockZ) + 1.0

                                        // Use Folia's teleport method
                                        val player = event.whoClicked as Player
                                        player.teleportAsync(target).thenAccept { success ->
                                            if (success) {
                                                player.sendMessage("${ChatColor.GREEN}Teleported to plot location ${ChatColor.GOLD}${plot.name}")
                                            } else {
                                                player.sendMessage("${ChatColor.RED}Failed to teleport to plot location")
                                            }
                                        }
                                    } else {
                                        (event.whoClicked as Player).sendMessage("${ChatColor.RED}Plot world not found!")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            plugin.log("Error handling plot menu click: ${e.message}", java.util.logging.Level.WARNING)
                        }
                    }
                }
            }

            @org.bukkit.event.EventHandler
            fun onInventoryClose(event: org.bukkit.event.inventory.InventoryCloseEvent) {
                if (event.view.title == "Your Plots") {
                    // Unregister this listener when inventory is closed
                    org.bukkit.event.HandlerList.unregisterAll(this)
                }
            }
        }, plugin)
    }
}