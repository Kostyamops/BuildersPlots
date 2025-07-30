package ru.joutak.buildersplots.util

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.data.BlockData
import ru.joutak.buildersplots.model.Plot
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object SchematicUtil {
    
    /**
     * Serialize a plot area to a schematic byte array
     */
    fun serializePlotToSchematic(plot: Plot): ByteArray? {
        try {
            val world = Bukkit.getWorld(plot.worldName) ?: return null
            
            val width = plot.getWidth()
            val height = plot.getHeight()
            val length = plot.getLength()
            
            // Create output stream for compressed data
            val byteArrayOutputStream = java.io.ByteArrayOutputStream()
            val gzipOutputStream = GZIPOutputStream(byteArrayOutputStream)
            val dataOutputStream = java.io.DataOutputStream(gzipOutputStream)
            
            // Write header
            dataOutputStream.writeInt(width)
            dataOutputStream.writeInt(height)
            dataOutputStream.writeInt(length)
            
            // Write blocks
            for (y in plot.minY..plot.maxY) {
                for (z in plot.minZ..plot.maxZ) {
                    for (x in plot.minX..plot.maxX) {
                        val block = world.getBlockAt(x, y, z)
                        val blockData = block.blockData.getAsString()
                        dataOutputStream.writeUTF(blockData)
                    }
                }
            }
            
            dataOutputStream.flush()
            gzipOutputStream.finish()
            gzipOutputStream.close()
            
            return byteArrayOutputStream.toByteArray()
        } catch (e: Exception) {
            Bukkit.getLogger().warning("[BuildersPlots] Error serializing plot to schematic: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Load a schematic file to the world
     */
    fun loadSchematicToWorld(file: File, plot: Plot): Boolean {
        try {
            val world = Bukkit.getWorld(plot.worldName)
            if (world == null) {
                Bukkit.getLogger().warning("[BuildersPlots] World ${plot.worldName} not found")
                return false
            }
            
            val inputStream = FileInputStream(file)
            val gzipInputStream = GZIPInputStream(inputStream)
            val dataInputStream = java.io.DataInputStream(gzipInputStream)
            
            // Read header
            val width = dataInputStream.readInt()
            val height = dataInputStream.readInt()
            val length = dataInputStream.readInt()
            
            // Verify dimensions
            if (width != plot.getWidth() || height != plot.getHeight() || length != plot.getLength()) {
                Bukkit.getLogger().warning("[BuildersPlots] Schematic dimensions don't match plot dimensions")
                return false
            }
            
            // Read blocks
            for (y in plot.minY..plot.maxY) {
                for (z in plot.minZ..plot.maxZ) {
                    for (x in plot.minX..plot.maxX) {
                        val blockDataString = dataInputStream.readUTF()
                        val blockData = Bukkit.createBlockData(blockDataString)
                        world.getBlockAt(x, y, z).setBlockData(blockData, false)
                    }
                }
            }
            
            dataInputStream.close()
            gzipInputStream.close()
            inputStream.close()
            
            return true
        } catch (e: Exception) {
            Bukkit.getLogger().warning("[BuildersPlots] Error loading schematic: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Save a plot to a schematic file
     */
    fun saveSchematic(plot: Plot, file: File): Boolean {
        try {
            val schematicData = serializePlotToSchematic(plot)
            if (schematicData == null) {
                return false
            }
            
            val outputStream = FileOutputStream(file)
            outputStream.write(schematicData)
            outputStream.flush()
            outputStream.close()
            
            return true
        } catch (e: Exception) {
            Bukkit.getLogger().warning("[BuildersPlots] Error saving schematic: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
}