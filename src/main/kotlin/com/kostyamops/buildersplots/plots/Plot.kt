package com.kostyamops.buildersplots.plots

import org.bukkit.Location
import org.bukkit.World
import java.io.Serializable
import java.util.*

data class Plot(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val worldName: String,
    val originalWorld: String,
    val centerX: Int,
    val centerZ: Int,
    val radius: Int,
    val createdBy: UUID,
    val createdAt: Long = System.currentTimeMillis(),
    val members: MutableSet<UUID> = mutableSetOf(),
    var syncEnabled: Boolean = true
) : Serializable {
    
    fun isInside(world: World, x: Int, y: Int, z: Int): Boolean {
        if (world.name != worldName) return false
        
        val minX = centerX - radius
        val maxX = centerX + radius
        val minZ = centerZ - radius
        val maxZ = centerZ + radius
        
        return x in minX..maxX && z in minZ..maxZ && y in -64..320
    }
    
    fun isInside(location: Location): Boolean {
        return isInside(location.world, location.blockX, location.blockY, location.blockZ)
    }
    
    fun getOriginalCoordinate(x: Int, z: Int): Pair<Int, Int> {
        // Convert plot coordinates back to original world coordinates
        val relativeX = x - (centerX - radius)
        val relativeZ = z - (centerZ - radius)
        
        val originalX = relativeX + (centerX - radius)
        val originalZ = relativeZ + (centerZ - radius)
        
        return Pair(originalX, originalZ)
    }
    
    fun hasMember(uuid: UUID): Boolean {
        return uuid == createdBy || members.contains(uuid)
    }
    
    fun addMember(uuid: UUID): Boolean {
        return members.add(uuid)
    }
    
    fun removeMember(uuid: UUID): Boolean {
        if (uuid == createdBy) return false
        return members.remove(uuid)
    }
    
    fun disableSync() {
        syncEnabled = false
    }
}