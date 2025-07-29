package ru.joutak.buildersplots.model

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import java.io.Serializable
import java.util.*

data class Plot(
    val id: String,
    val name: String,
    val owner: UUID,
    val members: MutableSet<UUID> = HashSet(),
    val originWorld: String,
    val originX: Int,
    val originY: Int,
    val originZ: Int,
    val radius: Int,
    var syncEnabled: Boolean = true
) : Serializable {

    val plotWorld: World?
        get() = Bukkit.getWorld("plot_$id")

    fun containsLocation(location: Location): Boolean {
        if (location.world.name != "plot_$id") return false

        // Check if the location is within the plot boundaries
        val minX = originX - radius
        val maxX = originX + radius
        val minZ = originZ - radius
        val maxZ = originZ + radius

        return location.blockX in minX..maxX && location.blockZ in minZ..maxZ
    }

    fun addMember(uuid: UUID): Boolean {
        return members.add(uuid)
    }

    fun removeMember(uuid: UUID): Boolean {
        return members.remove(uuid)
    }

    fun hasMember(uuid: UUID): Boolean {
        return uuid == owner || members.contains(uuid) || Bukkit.getPlayer(uuid)?.isOp == true
    }

    fun disableSync() {
        syncEnabled = false
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}

data class PlotCreationRequest(
    val plotName: String,
    val owner: UUID,
    val worldName: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val radius: Int
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

data class BlockChange(
    val worldName: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val blockData: String,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}