package com.kostyamops.buildersplots.world

import org.bukkit.World
import org.bukkit.generator.BiomeProvider
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.WorldInfo
import org.bukkit.block.Biome
import java.util.Random

/**
 * Генератор пустых миров
 */
class EmptyWorldGenerator : ChunkGenerator() {

    override fun generateNoise(worldInfo: WorldInfo, random: Random, chunkX: Int, chunkZ: Int, chunkData: ChunkData) {
        // Ничего не генерируем - только воздух
    }

    override fun generateSurface(worldInfo: WorldInfo, random: Random, chunkX: Int, chunkZ: Int, chunkData: ChunkData) {
        // Поверхность не генерируем
    }

    override fun generateBedrock(worldInfo: WorldInfo, random: Random, chunkX: Int, chunkZ: Int, chunkData: ChunkData) {
        // Бедрок не генерируем
    }

    override fun getDefaultBiomeProvider(worldInfo: WorldInfo): BiomeProvider {
        return object : BiomeProvider() {
            override fun getBiome(worldInfo: WorldInfo, x: Int, y: Int, z: Int): Biome {
                return Biome.PLAINS
            }

            override fun getBiomes(worldInfo: WorldInfo): List<Biome> {
                return listOf(Biome.PLAINS)
            }
        }
    }

    override fun canSpawn(world: World, x: Int, z: Int): Boolean {
        return true
    }

    override fun getFixedSpawnLocation(world: World, random: Random): org.bukkit.Location {
        return org.bukkit.Location(world, 0.0, 100.0, 0.0)
    }
}