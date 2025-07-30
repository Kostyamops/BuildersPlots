package ru.joutak.buildersplots.manager

import org.bukkit.Location
import org.bukkit.entity.Player
import ru.joutak.buildersplots.BuildersPlots
import ru.joutak.buildersplots.model.Plot
import ru.joutak.buildersplots.util.SchematicUtil
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.*
import kotlin.collections.HashMap

class PlotManager(private val plugin: BuildersPlots) {

    private val plots = HashMap<String, Plot>()
    private val playerSelections = HashMap<UUID, Pair<Location?, Location?>>()
    private val plotsDir = File(plugin.dataFolder, plugin.config.plotsDirectory)

    init {
        if (!plotsDir.exists()) {
            plotsDir.mkdirs()
        }
        loadPlots()

        // Настраиваем автосохранение (совместимо с Folia)
        setupAutoSave()
    }

    /**
     * Настраивает автоматическое сохранение плотов
     * с поддержкой Folia и обычных серверов
     */
    private fun setupAutoSave() {
        try {
            // Интервал в тиках (20 тиков в секунду)
            val intervalTicks = plugin.config.autoSaveInterval.toLong() * 20 * 60

            // Используем globalRegionScheduler для Folia
            plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { _ ->
                savePlots()
                plugin.logger.info("[BuildersPlots] Auto-saving plots...")
            }, 20 * 60, intervalTicks) // Первый запуск через минуту, затем по расписанию

            plugin.logger.info("[BuildersPlots] Auto-save scheduled with Folia scheduler every ${plugin.config.autoSaveInterval} minutes")
        } catch (e: Throwable) {
            plugin.logger.warning("[BuildersPlots] Failed to setup Folia auto-save: ${e.message}")
            plugin.logger.warning("[BuildersPlots] Will use manual saves only")

            // Будем полагаться на ручное сохранение при выключении плагина
            // и на сохранение при операциях с плотами
        }
    }

    private fun loadPlots() {
        var loadedCount = 0
        plotsDir.listFiles()?.filter { it.name.endsWith(".plot") }?.forEach { file ->
            try {
                ObjectInputStream(FileInputStream(file)).use { ois ->
                    val plot = ois.readObject() as Plot
                    plots[plot.name.lowercase()] = plot
                    loadedCount++
                    if (plugin.config.debugMode) {
                        plugin.logger.info("[BuildersPlots] Loaded plot: ${plot.name}")
                    }
                }
            } catch (e: Exception) {
                plugin.logger.warning("[BuildersPlots] Failed to load plot file ${file.name}: ${e.message}")
            }
        }
        plugin.logger.info("[BuildersPlots] Loaded $loadedCount plots")
    }

    fun savePlots() {
        var savedCount = 0
        plots.values.forEach { plot ->
            try {
                val file = File(plotsDir, "${plot.name}.plot")
                ObjectOutputStream(FileOutputStream(file)).use { oos ->
                    oos.writeObject(plot)
                    savedCount++
                }
            } catch (e: Exception) {
                plugin.logger.warning("[BuildersPlots] Failed to save plot ${plot.name}: ${e.message}")
            }
        }
        if (plugin.config.debugMode) {
            plugin.logger.info("[BuildersPlots] Saved $savedCount plots")
        }
    }

    // Method for creating a plot
    fun createPlot(player: Player, name: String): Plot? {
        // Проверка на существование плота
        if (plotExists(name)) {
            player.sendMessage(plugin.messages.get("general.plot-exists"))
            return null
        }

        // Получаем выделение игрока
        val selection = playerSelections[player.uniqueId]
        if (selection == null || selection.first == null || selection.second == null) {
            player.sendMessage(plugin.messages.get("commands.create-usage"))
            return null
        }

        val pos1 = selection.first!!
        val pos2 = selection.second!!

        // Проверка, что точки в одном мире
        if (pos1.world?.name != pos2.world?.name) {
            player.sendMessage(plugin.messages.get("general.selection-different-worlds"))
            return null
        }

        // Определяем координаты
        val minX = minOf(pos1.blockX, pos2.blockX)
        val minY = minOf(pos1.blockY, pos2.blockY)
        val minZ = minOf(pos1.blockZ, pos2.blockZ)
        val maxX = maxOf(pos1.blockX, pos2.blockX)
        val maxY = maxOf(pos1.blockY, pos2.blockY)
        val maxZ = maxOf(pos1.blockZ, pos2.blockZ)

        // Проверка на максимальный размер
        val width = maxX - minX + 1
        val height = maxY - minY + 1
        val length = maxZ - minZ + 1

        if (width > plugin.config.maxPlotWidth ||
            height > plugin.config.maxPlotHeight ||
            length > plugin.config.maxPlotLength) {
            player.sendMessage(plugin.messages.get("general.plot-too-large",
                plugin.config.maxPlotWidth,
                plugin.config.maxPlotHeight,
                plugin.config.maxPlotLength))
            return null
        }

        // Вычисляем центр плота
        val centerX = (minX + maxX) / 2.0
        val centerY = (minY + maxY) / 2.0
        val centerZ = (minZ + maxZ) / 2.0

        // Создаем новый плот
        val plot = Plot(
            id = UUID.randomUUID(),
            name = name,
            owner = player.uniqueId,
            createdAt = System.currentTimeMillis(),
            minX = minX,
            minY = minY,
            minZ = minZ,
            maxX = maxX,
            maxY = maxY,
            maxZ = maxZ,
            worldName = pos1.world?.name ?: "world",
            centerX = centerX,
            centerY = centerY,
            centerZ = centerZ,
            yaw = player.location.yaw,
            pitch = player.location.pitch
        )

        // Сохраняем плот
        plots[name.lowercase()] = plot
        savePlot(plot)

        // Логируем информацию
        plugin.logger.info("[BuildersPlots] Player ${player.name} created plot $name (${width}x${height}x${length})")

        // Сохраняем схематику плота, если включен debug режим
        if (plugin.config.debugMode) {
            val schematicFile = File(plotsDir, "${name}.schematic")
            SchematicUtil.saveSchematic(plot, schematicFile)
            plugin.logger.info("[BuildersPlots] Saved schematic for plot $name")
        }

        return plot
    }

    // Method for creating a plot from network
    fun createPlot(plot: Plot) {
        plots[plot.name.lowercase()] = plot
        savePlot(plot)
        plugin.logger.info("[BuildersPlots] Created plot ${plot.name} from network")
    }

    private fun savePlot(plot: Plot) {
        try {
            val file = File(plotsDir, "${plot.name}.plot")
            ObjectOutputStream(FileOutputStream(file)).use { oos ->
                oos.writeObject(plot)
            }
        } catch (e: Exception) {
            plugin.logger.warning("[BuildersPlots] Failed to save plot ${plot.name}: ${e.message}")
        }
    }

    fun getPlots(): List<Plot> = plots.values.toList()

    fun getPlotByName(name: String): Plot? = plots[name.lowercase()]

    fun plotExists(name: String): Boolean = plots.containsKey(name.lowercase())

    fun deletePlot(name: String): Boolean {
        val plot = plots.remove(name.lowercase()) ?: return false
        val file = File(plotsDir, "${plot.name}.plot")
        if (file.exists()) {
            file.delete()
        }
        plugin.logger.info("[BuildersPlots] Deleted plot ${plot.name}")
        return true
    }

    // Методы для работы с выделением области
    fun setFirstPoint(player: Player, location: Location) {
        val current = playerSelections[player.uniqueId]
        playerSelections[player.uniqueId] = Pair(location, current?.second)

        // Отправляем сообщение
        player.sendMessage(plugin.messages.get("plot.selection-first",
            location.blockX, location.blockY, location.blockZ))

        // Проверяем, завершено ли выделение
        checkSelectionComplete(player)
    }

    fun setSecondPoint(player: Player, location: Location) {
        val current = playerSelections[player.uniqueId]
        playerSelections[player.uniqueId] = Pair(current?.first, location)

        // Отправляем сообщение
        player.sendMessage(plugin.messages.get("plot.selection-second",
            location.blockX, location.blockY, location.blockZ))

        // Проверяем, завершено ли выделение
        checkSelectionComplete(player)
    }

    private fun checkSelectionComplete(player: Player) {
        val selection = playerSelections[player.uniqueId] ?: return
        if (selection.first != null && selection.second != null) {
            val pos1 = selection.first!!
            val pos2 = selection.second!!

            // Вычисляем размеры
            val width = Math.abs(pos1.blockX - pos2.blockX) + 1
            val height = Math.abs(pos1.blockY - pos2.blockY) + 1
            val length = Math.abs(pos1.blockZ - pos2.blockZ) + 1

            player.sendMessage(plugin.messages.get("plot.selection-complete", width, height, length))
        }
    }

    fun getPlayerSelection(player: Player): Pair<Location?, Location?> {
        return playerSelections[player.uniqueId] ?: Pair(null, null)
    }

    fun clearPlayerSelection(player: Player) {
        playerSelections.remove(player.uniqueId)
    }
}