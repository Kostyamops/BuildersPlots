package com.kostyamops.buildersplots.data

import org.bukkit.Location
import java.util.UUID

data class Plot(
    val name: String,
    val radius: Int,
    val center: PlotLocation,
    val creatorUUID: UUID,
    val creationTime: Long = System.currentTimeMillis(),

) {
    // Default constructor for serialization/deserialization
    constructor() : this("", 0, PlotLocation("", 0.0, 0.0, 0.0), UUID.randomUUID(), 0L)

    // Calculate if a location is within this plot
    fun contains(location: PlotLocation): Boolean {
        if (location.world != center.world) return false

        val xDiff = Math.abs(location.x - center.x)
        val zDiff = Math.abs(location.z - center.z)

        return xDiff <= radius && zDiff <= radius
    }

    // Get the minimum and maximum corners of the plot
    fun getMinLocation(): PlotLocation {
        return PlotLocation(center.world, center.x - radius, 0.0, center.z - radius)
    }

    fun getMaxLocation(): PlotLocation {
        return PlotLocation(center.world, center.x + radius, 255.0, center.z + radius)
    }

    // Get test world name for this plot
    fun getTestWorldName(plugin: com.kostyamops.buildersplots.BuildersPlots): String {
        val worldPrefix = plugin.config.getString("plot-world-prefix", "plot_")
        return worldPrefix + name.lowercase().replace(" ", "_")
    }

    fun getTestWorldName(): String {
        // Используем значение по умолчанию, если плагин недоступен
        return "plot_" + name.lowercase().replace(" ", "_")
    }
}

data class PlotLocation(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double
) {
    // Default constructor for serialization/deserialization
    constructor() : this("", 0.0, 0.0, 0.0)

    constructor(location: Location) : this(
        location.world.name,
        location.x,
        location.y,
        location.z
    )
}