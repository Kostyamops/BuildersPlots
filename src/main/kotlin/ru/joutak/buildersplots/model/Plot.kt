package ru.joutak.buildersplots.model

import org.bukkit.Bukkit
import org.bukkit.Location
import java.io.Serializable
import java.util.UUID

data class Plot(
    val id: UUID,
    val name: String,
    val owner: UUID,
    val createdAt: Long,
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val maxX: Int,
    val maxY: Int,
    val maxZ: Int,
    val worldName: String,
    val centerX: Double,
    val centerY: Double,
    val centerZ: Double,
    val yaw: Float = 0f,
    val pitch: Float = 0f
) : Serializable {
    
    // Необходимо для сериализации
    private companion object {
        private const val serialVersionUID = 1L
    }
    
    // Получить центральную точку плота
    fun getCenter(): Location? {
        val world = Bukkit.getWorld(worldName) ?: return null
        return Location(world, centerX, centerY, centerZ, yaw, pitch)
    }
    
    // Получить размеры плота
    fun getWidth(): Int = maxX - minX + 1
    fun getHeight(): Int = maxY - minY + 1
    fun getLength(): Int = maxZ - minZ + 1
    
    // Получить объем плота
    fun getVolume(): Int = getWidth() * getHeight() * getLength()
    
    // Проверка, находится ли точка внутри плота
    fun contains(x: Int, y: Int, z: Int): Boolean {
        return x in minX..maxX && y in minY..maxY && z in minZ..maxZ
    }
    
    // Проверка, находится ли локация внутри плота
    fun contains(location: Location): Boolean {
        if (location.world?.name != worldName) return false
        return contains(location.blockX, location.blockY, location.blockZ)
    }
    
    // Метод для форматирования времени создания
    fun getFormattedCreationTime(): String {
        val date = java.util.Date(createdAt)
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        return format.format(date)
    }
}