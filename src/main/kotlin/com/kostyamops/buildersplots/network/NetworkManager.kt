package com.kostyamops.buildersplots.network

import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.network.packets.BlockChangePacket
import com.kostyamops.buildersplots.network.packets.Packet
import com.kostyamops.buildersplots.network.packets.PlotManagementPacket
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import java.util.logging.Level

class NetworkManager(private val plugin: BuildersPlots) {

    fun sendBlockChange(world: String, x: Int, y: Int, z: Int, material: Material, blockData: BlockData?) {
        if (!plugin.configManager.isMainServer) {
            return // Only main server sends block changes
        }

        val packet = BlockChangePacket(
            world = world,
            x = x,
            y = y,
            z = z,
            material = material.name,
            blockData = blockData?.asString ?: ""
        )

        plugin.serverConnection.sendPacket(packet)
        plugin.log("Sent block change at $world ($x,$y,$z): ${material.name}")
    }

    fun handleBlockChange(packet: BlockChangePacket) {
        if (plugin.configManager.isMainServer) {
            return // Only test server processes block changes
        }

        // Apply the block change to all affected plots
        applyBlockChangeToPlots(packet)
    }

    private fun applyBlockChangeToPlots(packet: BlockChangePacket) {
        // Find all plots that correspond to this original world location
        val affectedPlots = plugin.plotManager.getAllPlots().filter {
            it.originalWorld == packet.world && it.syncEnabled
        }

        if (affectedPlots.isEmpty()) return

        // Check if the block is within any of the plots' areas
        val blockX = packet.x
        val blockZ = packet.z

        val material = try {
            Material.valueOf(packet.material)
        } catch (e: IllegalArgumentException) {
            plugin.log("Unknown material: ${packet.material}", Level.WARNING)
            return
        }

        for (plot in affectedPlots) {
            // Check if the block is within this plot's original area
            val minX = plot.centerX - plot.radius
            val maxX = plot.centerX + plot.radius
            val minZ = plot.centerZ - plot.radius
            val maxZ = plot.centerZ + plot.radius

            if (blockX in minX..maxX && blockZ in minZ..maxZ) {
                // Calculate the corresponding location in the plot world
                val plotX = blockX - minX
                val plotZ = blockZ - minZ

                // Get the plot world
                val plotWorld = Bukkit.getWorld(plot.worldName) ?: continue

                // Apply the change to the plot world
                val blockData = if (packet.blockData.isNotEmpty()) {
                    try {
                        Bukkit.createBlockData(packet.blockData)
                    } catch (e: Exception) {
                        plugin.log("Invalid block data: ${packet.blockData}", Level.WARNING)
                        null
                    }
                } else null

                // Schedule the block change in the plot world using Folia's API
                // For Folia 1.21.4, we'll use the global scheduler and specify the region
                val loc = Location(plotWorld, plotX.toDouble(), packet.y.toDouble(), plotZ.toDouble())

                // Use Folia's ThreadedRegionizer API
                Bukkit.getServer().scheduler.runTask(plugin, Runnable {
                    try {
                        if (blockData != null) {
                            plotWorld.getBlockAt(plotX, packet.y, plotZ).blockData = blockData
                        } else {
                            plotWorld.getBlockAt(plotX, packet.y, plotZ).type = material
                        }
                        plugin.log("Applied block change to plot ${plot.name} at ($plotX,${packet.y},$plotZ): ${material.name}")
                    } catch (e: Exception) {
                        plugin.log("Error applying block change: ${e.message}", Level.WARNING)
                    }
                })
            }
        }
    }

    fun sendPlotManagement(action: String, plotData: Map<String, Any>) {
        val packet = PlotManagementPacket(action, plotData)
        plugin.serverConnection.sendPacket(packet)
        plugin.log("Sent plot management packet: $action")
    }

    fun handlePlotManagement(packet: PlotManagementPacket) {
        when (packet.action) {
            "CREATE" -> {
                // Handle plot creation
                val name = packet.data["name"] as? String ?: return
                val originalWorld = packet.data["originalWorld"] as? String ?: return
                val centerX = packet.data["centerX"] as? Int ?: return
                val centerZ = packet.data["centerZ"] as? Int ?: return
                val radius = packet.data["radius"] as? Int ?: return
                val creatorUuid = java.util.UUID.fromString(packet.data["creatorUuid"] as? String ?: return)

                plugin.plotManager.createPlot(name, originalWorld, centerX, centerZ, radius, creatorUuid)
                plugin.log("Created plot from network request: $name")
            }
            "DELETE" -> {
                // Handle plot deletion
                val plotId = java.util.UUID.fromString(packet.data["plotId"] as? String ?: return)
                plugin.plotManager.deletePlot(plotId)
                plugin.log("Deleted plot from network request: $plotId")
            }
            "ADD_MEMBER" -> {
                // Handle adding member
                val plotId = java.util.UUID.fromString(packet.data["plotId"] as? String ?: return)
                val playerUuid = java.util.UUID.fromString(packet.data["playerUuid"] as? String ?: return)
                plugin.plotManager.addMemberToPlot(plotId, playerUuid)
                plugin.log("Added member to plot from network request: $playerUuid to $plotId")
            }
            "REMOVE_MEMBER" -> {
                // Handle removing member
                val plotId = java.util.UUID.fromString(packet.data["plotId"] as? String ?: return)
                val playerUuid = java.util.UUID.fromString(packet.data["playerUuid"] as? String ?: return)
                plugin.plotManager.removeMemberFromPlot(plotId, playerUuid)
                plugin.log("Removed member from plot from network request: $playerUuid from $plotId")
            }
            "DISABLE_SYNC" -> {
                // Handle disabling sync
                val plotId = java.util.UUID.fromString(packet.data["plotId"] as? String ?: return)
                plugin.plotManager.disablePlotSync(plotId)
                plugin.log("Disabled sync for plot from network request: $plotId")
            }
        }
    }
}