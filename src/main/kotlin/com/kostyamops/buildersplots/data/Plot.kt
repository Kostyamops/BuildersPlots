package com.kostyamops.buildersplots.data

import com.kostyamops.buildersplots.BuildersPlots
import org.bukkit.entity.Player
import java.util.UUID

data class Plot(
    val name: String,
    val radius: Int,
    val center: PlotLocation,
    val creatorUUID: UUID,
    val creationTime: Long = System.currentTimeMillis(),
    val members: MutableSet<PlotMember> = mutableSetOf()
) {
    constructor() : this("", 0, PlotLocation("", 0.0, 0.0, 0.0), UUID.randomUUID(), 0L)

    fun contains(location: PlotLocation): Boolean {
        if (location.world != center.world) return false

        val xDiff = Math.abs(location.x - center.x)
        val zDiff = Math.abs(location.z - center.z)

        return xDiff <= radius && zDiff <= radius
    }

    fun getMinLocation(): PlotLocation {
        return PlotLocation(center.world, center.x - radius, 0.0, center.z - radius)
    }

    fun getMaxLocation(): PlotLocation {
        return PlotLocation(center.world, center.x + radius, 255.0, center.z + radius)
    }

    fun getTestWorldName(plugin: BuildersPlots): String {
        val worldPrefix = plugin.config.getString("plot-world-prefix", "plot_")
        return worldPrefix + name.lowercase().replace(" ", "_")
    }

    fun getTestWorldName(): String {
        return "plot_" + name.lowercase().replace(" ", "_")
    }

    /**
     * Checks if a player has access to the plot
     */
    fun hasAccess(player: Player): Boolean {
        // Admins always have access
        if (player.isOp() || player.hasPermission("buildersplots.admin.access.all")) {
            return true
        }

        // Creator has access
        if (player.uniqueId == creatorUUID) {
            return true
        }

        // Check members
        return members.any { it.uuid == player.uniqueId }
    }

    /**
     * Checks if a player is the creator of the plot
     */
    fun isCreator(player: Player): Boolean {
        return player.uniqueId == creatorUUID
    }

    /**
     * Adds a member to the plot
     */
    fun addMember(uuid: UUID, name: String): Boolean {
        if (members.any { it.uuid == uuid }) {
            return false // Member already exists
        }

        return members.add(PlotMember(uuid, name, System.currentTimeMillis()))
    }

    /**
     * Removes a member from the plot
     */
    fun removeMember(uuid: UUID): Boolean {
        return members.removeIf { it.uuid == uuid }
    }
}

/**
 * Represents a member of a plot
 */
data class PlotMember(
    val uuid: UUID,
    val name: String,
    val addedAt: Long = System.currentTimeMillis()
) {
    constructor() : this(UUID.randomUUID(), "", 0L)
}

data class PlotLocation(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double
) {
    constructor() : this("", 0.0, 0.0, 0.0)

    constructor(location: org.bukkit.Location) : this(
        location.world.name,
        location.x,
        location.y,
        location.z
    )
}