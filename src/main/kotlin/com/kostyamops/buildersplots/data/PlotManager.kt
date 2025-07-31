package com.kostyamops.buildersplots.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.ServerType
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlotManager(private val plugin: BuildersPlots) {

    private val plots = ConcurrentHashMap<String, Plot>()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val plotsFile = File(plugin.dataFolder, "plots.json")

    // Поля для управления автосохранением
    private var autoSaveTask: BukkitTask? = null
    private val plotsWorldsFolder: File
        get() = File(plugin.dataFolder, "plots")

    init {
        loadPlots()

        // Создаем папку для миров плотов, если её нет
        if (!plotsWorldsFolder.exists()) {
            plotsWorldsFolder.mkdirs()
            plugin.logger.info("Создана папка для миров плотов: ${plotsWorldsFolder.absolutePath}")
        }
    }

    fun createPlot(name: String, radius: Int, player: Player): Plot? {
        // Check if plot name already exists
        if (plots.containsKey(name)) {
            return null
        }

        // Check if radius is within allowed limits
        val maxRadius = plugin.config.getInt("plots.max-radius", 100)
        if (radius <= 0 || radius > maxRadius) {
            return null
        }

        val plotLocation = PlotLocation(player.location)
        val plot = Plot(name, radius, plotLocation, player.uniqueId)

        plots[name] = plot
        savePlots()

        // If this is the main server, send the plot creation to the test server
        if (plugin.serverType == ServerType.MAIN) {
            plugin.communicationManager.sendPlotCreation(plot)
        }

        return plot
    }

    fun deletePlot(name: String): Boolean {
        // Получаем плот, но не удаляем его из реестра сразу
        val plot = plots[name] ?: return false

        // Если мы на тестовом сервере, сначала удаляем мир
        if (plugin.serverType == ServerType.TEST) {
            val worldName = plot.getTestWorldName(plugin)

            // ВАЖНО: используем синхронную задачу для выгрузки мира
            if (Bukkit.isPrimaryThread()) {
                // Если мы уже в основном потоке, выполняем сразу
                deleteWorldSafely(worldName, plot)
            } else {
                // Иначе планируем синхронную задачу
                try {
                    // Используем CompletableFuture для ожидания завершения
                    val future = java.util.concurrent.CompletableFuture<Boolean>()

                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        try {
                            val result = deleteWorldSafely(worldName, plot)
                            future.complete(result)
                        } catch (e: Exception) {
                            plugin.logger.severe("Ошибка при удалении мира: ${e.message}")
                            e.printStackTrace()
                            future.complete(false)
                        }
                    })

                    // Ждем завершения операции, но не более 5 секунд
                    try {
                        future.get(5, java.util.concurrent.TimeUnit.SECONDS)
                    } catch (e: Exception) {
                        plugin.logger.severe("Таймаут при удалении мира: ${e.message}")
                    }
                } catch (e: Exception) {
                    plugin.logger.severe("Ошибка планирования удаления мира: ${e.message}")
                }
            }
        }

        // Удаляем плот из реестра только после попытки удаления мира
        plots.remove(name)
        savePlots()

        // Отправляем сообщение на другой сервер
        plugin.communicationManager.sendPlotDeletion(name)

        return true
    }

    /**
     * Безопасно удаляет мир плота (должен вызываться из основного потока)
     * @param worldName имя мира для удаления
     * @param plot данные плота
     * @return true если удаление прошло успешно
     */
    private fun deleteWorldSafely(worldName: String, plot: Plot): Boolean {
        // Проверяем, что мы в основном потоке
        if (!Bukkit.isPrimaryThread()) {
            plugin.logger.severe("Попытка удалить мир из неосновного потока")
            return false
        }

        // Отменяем задачи выгрузки
        cancelUnloadTask(worldName)

        var success = true
        val world = Bukkit.getWorld(worldName)

        if (world != null) {
            // Телепортируем игроков в основной мир
            val defaultWorld = Bukkit.getWorlds()[0]
            for (player in world.players) {
                player.teleport(defaultWorld.spawnLocation)
                player.sendMessage("§cПлот, на котором вы находились, был удален.")
            }

            // Выгружаем мир
            try {
                success = Bukkit.unloadWorld(world, false) // не сохраняем при выгрузке
                if (!success) {
                    plugin.logger.warning("Не удалось выгрузить мир $worldName")
                }
            } catch (e: Exception) {
                plugin.logger.severe("Ошибка при выгрузке мира $worldName: ${e.message}")
                success = false
            }
        }

        // Удаляем файлы мира даже если выгрузка не удалась
        try {
            // Удаляем из папки плагина
            val pluginWorldFolder = File(plotsWorldsFolder, worldName)
            if (pluginWorldFolder.exists()) {
                deleteDirectoryCompletely(pluginWorldFolder)
            }

            // Удаляем из корневой папки
            val rootWorldFolder = File(Bukkit.getWorldContainer(), worldName)
            if (rootWorldFolder.exists()) {
                deleteDirectoryCompletely(rootWorldFolder)
            }
        } catch (e: Exception) {
            plugin.logger.severe("Ошибка при удалении файлов мира: ${e.message}")
            success = false
        }

        // Запускаем сборщик мусора, чтобы освободить ресурсы
        System.gc()

        return success
    }

    /**
     * Полностью удаляет директорию со всеми файлами
     * @param directory директория для удаления
     */
    private fun deleteDirectoryCompletely(directory: File) {
        if (!directory.exists()) return

        try {
            // Специальная обработка папок с регионами (они часто вызывают проблемы)
            val regionFolders = arrayOf(
                File(directory, "region"),
                File(directory, "DIM1/region"),
                File(directory, "DIM-1/region")
            )

            // Сначала удаляем файлы регионов
            for (regionFolder in regionFolders) {
                if (regionFolder.exists() && regionFolder.isDirectory) {
                    regionFolder.listFiles()?.forEach { file ->
                        if (file.name.endsWith(".mca")) {
                            if (!file.delete()) {
                                // Если не удалось удалить, пометим для удаления при выходе
                                file.deleteOnExit()
                            }
                        }
                    }
                }
            }

            // Затем удаляем всю директорию
            Files.walkFileTree(directory.toPath(), object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    try {
                        Files.delete(file)
                    } catch (e: Exception) {
                        // Если не удалось удалить, пометим для удаления при выходе
                        file.toFile().deleteOnExit()
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    try {
                        Files.delete(dir)
                    } catch (e: Exception) {
                        // Если не удалось удалить, пометим для удаления при выходе
                        dir.toFile().deleteOnExit()
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (e: Exception) {
            plugin.logger.severe("Ошибка при удалении директории: ${e.message}")
        }
    }

    fun getPlot(name: String): Plot? {
        return plots[name]
    }

    fun getAllPlots(): List<Plot> {
        return plots.values.toList()
    }

    fun getPlotAtLocation(location: Location): Plot? {
        val plotLocation = PlotLocation(location)
        return plots.values.find { it.contains(plotLocation) }
    }

    fun savePlots() {
        try {
            if (!plotsFile.exists()) {
                plotsFile.createNewFile()
            }

            FileWriter(plotsFile).use { writer ->
                gson.toJson(plots.values.toList(), writer)
            }
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save plots: ${e.message}")
        }
    }

    private fun loadPlots() {
        try {
            if (plotsFile.exists()) {
                FileReader(plotsFile).use { reader ->
                    val loadedPlots = gson.fromJson(reader, Array<Plot>::class.java)
                    plots.clear()
                    loadedPlots.forEach { plot ->
                        plots[plot.name] = plot
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("Failed to load plots: ${e.message}")
        }
    }

    fun addOrUpdatePlot(plot: Plot) {
        plots[plot.name] = plot
        savePlots()
    }

    /**
     * Запускает задачу автосохранения миров
     */
    fun startAutoSave() {
        // Отменяем предыдущую задачу, если она существует
        autoSaveTask?.cancel()

        // Получаем интервал автосохранения из конфига
        val autoSaveInterval = plugin.config.getLong("world-autosave-interval", 10)

        // Проверяем, включено ли автосохранение
        if (autoSaveInterval <= 0) {
            plugin.logger.info("Автосохранение миров отключено")
            return
        }

        // Запускаем новую задачу только на тестовом сервере
        if (plugin.serverType == ServerType.TEST) {
            val intervalTicks = autoSaveInterval * 60 * 20 // переводим минуты в тики (20 тиков = 1 секунда)
            autoSaveTask = object : BukkitRunnable() {
                override fun run() {
                    plugin.logger.info("Автоматическое сохранение миров плотов...")
                    saveAllPlotWorlds()
                    plugin.logger.info("Автосохранение завершено")
                }
            }.runTaskTimer(plugin, intervalTicks, intervalTicks)

            plugin.logger.info("Запущено автосохранение миров с интервалом $autoSaveInterval минут")
        }
    }

    /**
     * Останавливает задачу автосохранения
     */
    fun stopAutoSave() {
        autoSaveTask?.cancel()
        autoSaveTask = null
    }

    /**
     * Сохраняет все загруженные миры плотов
     */
    fun saveAllPlotWorlds() {
        if (plugin.serverType != ServerType.TEST) return

        var savedCount = 0
        for (plot in plots.values) {
            val worldName = plot.getTestWorldName(plugin)
            val world = Bukkit.getWorld(worldName)

            if (world != null) {
                try {
                    // Сохраняем мир
                    world.save()

                    // Проверяем, нужно ли перемещать мир из корневой директории
                    val rootWorldFolder = File(Bukkit.getWorldContainer(), worldName)
                    val pluginWorldFolder = File(plotsWorldsFolder, worldName)

                    if (rootWorldFolder.exists() && rootWorldFolder.isDirectory) {
                        if (!pluginWorldFolder.exists()) {
                            // Если папки нет в плагине, перемещаем мир
                            moveWorldFolder(rootWorldFolder, pluginWorldFolder)
                        } else {
                            // Если папка есть, обновляем её (удаляем и копируем)
                            deleteDirectory(pluginWorldFolder)
                            copyDirectory(rootWorldFolder, pluginWorldFolder)
                        }
                        plugin.logger.info("Мир $worldName перемещен в папку плагина")
                    }

                    savedCount++
                } catch (e: Exception) {
                    plugin.logger.severe("Ошибка сохранения мира $worldName: ${e.message}")
                    e.printStackTrace()
                }
            }
        }

        if (savedCount > 0) {
            plugin.logger.info("Сохранено $savedCount миров плотов")
        }
    }

    /**
     * Загружает миры для всех плотов при старте сервера
     */
    fun loadAllPlotWorlds() {
        if (plugin.serverType != ServerType.TEST) return

        plugin.logger.info("Загрузка миров плотов...")

        var loadedCount = 0
        for (plot in plots.values) {
            try {
                val world = loadPlotWorld(plot.name)
                if (world != null) {
                    loadedCount++
                }
            } catch (e: Exception) {
                plugin.logger.severe("Ошибка загрузки мира для плота ${plot.name}: ${e.message}")
                e.printStackTrace()
            }
        }

        plugin.logger.info("Загружено $loadedCount миров плотов")
    }

    /**
     * Загружает мир для конкретного плота
     */
    fun loadPlotWorld(plotName: String): World? {
        val plot = getPlot(plotName) ?: return null
        val worldName = plot.getTestWorldName(plugin)

        // Проверяем, не загружен ли уже этот мир
        val existingWorld = Bukkit.getWorld(worldName)
        if (existingWorld != null) {
            plugin.logger.info("Мир $worldName уже загружен")
            return existingWorld
        }

        // Проверяем, существует ли папка мира в директории плагина
        val worldFolder = File(plotsWorldsFolder, worldName)
        if (!worldFolder.exists() || !worldFolder.isDirectory) {
            // Проверяем, может ли мир быть в корневой директории
            val rootWorldFolder = File(Bukkit.getWorldContainer(), worldName)
            if (rootWorldFolder.exists() && rootWorldFolder.isDirectory) {
                plugin.logger.info("Найден мир плота в корневой директории, перемещаем в папку плагина...")

                try {
                    // Создаем директорию назначения, если её нет
                    if (!plotsWorldsFolder.exists()) {
                        plotsWorldsFolder.mkdirs()
                    }

                    // Перемещаем мир из корневой директории в папку плагина
                    moveWorldFolder(rootWorldFolder, worldFolder)
                    plugin.logger.info("Мир $worldName успешно перемещен в папку плагина")
                } catch (e: Exception) {
                    plugin.logger.severe("Ошибка при перемещении мира: ${e.message}")
                    e.printStackTrace()
                    return null
                }
            } else {
                plugin.logger.warning("Папка мира не существует: $worldName")
                return null
            }
        }

        plugin.logger.info("Загрузка мира плота: $worldName из ${worldFolder.absolutePath}")

        try {
            // Устанавливаем системное свойство для указания папки мира
            val originalWorldContainer = System.getProperty("org.bukkit.worldContainer")
            System.setProperty("org.bukkit.worldContainer", plotsWorldsFolder.absolutePath)

            // Создаем WorldCreator
            val creator = WorldCreator(worldName)
                .generator(com.kostyamops.buildersplots.world.EmptyWorldGenerator())
                .environment(World.Environment.NORMAL)
                .generateStructures(false)

            // Создаем/загружаем мир
            val world = creator.createWorld()

            // Восстанавливаем оригинальное свойство
            if (originalWorldContainer != null) {
                System.setProperty("org.bukkit.worldContainer", originalWorldContainer)
            } else {
                System.clearProperty("org.bukkit.worldContainer")
            }

            if (world != null) {
                plugin.logger.info("Мир $worldName успешно загружен из ${worldFolder.absolutePath}!")

                // Применяем настройки
                world.difficulty = org.bukkit.Difficulty.PEACEFUL
                world.time = 6000
                world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false)
                world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false)
                world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false)
                world.setGameRule(org.bukkit.GameRule.DO_FIRE_TICK, false)
                world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, true)

                return world
            } else {
                plugin.logger.severe("Не удалось загрузить мир: $worldName")
                return null
            }
        } catch (e: Exception) {
            plugin.logger.severe("Ошибка загрузки мира $worldName: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    /**
     * Выгружает все миры плотов при остановке сервера
     */
    fun unloadAllPlotWorlds() {
        if (plugin.serverType != ServerType.TEST) return

        plugin.logger.info("Выгрузка миров плотов...")

        var unloadedCount = 0
        for (plot in plots.values) {
            val worldName = plot.getTestWorldName(plugin)
            val world = Bukkit.getWorld(worldName)

            if (world != null) {
                try {
                    // Сначала сохраняем мир
                    world.save()

                    // Перемещаем мир в папку плагина
                    val rootWorldFolder = File(Bukkit.getWorldContainer(), worldName)
                    val pluginWorldFolder = File(plotsWorldsFolder, worldName)

                    if (rootWorldFolder.exists() && rootWorldFolder.isDirectory) {
                        // Если папки нет в плагине, перемещаем мир
                        if (!pluginWorldFolder.exists()) {
                            moveWorldFolder(rootWorldFolder, pluginWorldFolder)
                        } else {
                            // Если папка есть, обновляем её (удаляем и копируем)
                            deleteDirectory(pluginWorldFolder)
                            copyDirectory(rootWorldFolder, pluginWorldFolder)
                        }
                    }

                    // Телепортируем всех игроков из мира
                    val defaultWorld = Bukkit.getWorlds()[0]
                    world.players.forEach { player ->
                        player.teleport(defaultWorld.spawnLocation)
                        player.sendMessage("§cМир плота выгружается, вы телепортированы в основной мир.")
                    }

                    // Выгружаем мир
                    val success = Bukkit.unloadWorld(world, true)
                    if (success) {
                        plugin.logger.info("Выгружен мир: $worldName")
                        unloadedCount++
                    } else {
                        plugin.logger.warning("Не удалось выгрузить мир: $worldName")
                    }
                } catch (e: Exception) {
                    plugin.logger.severe("Ошибка выгрузки мира $worldName: ${e.message}")
                    e.printStackTrace()
                }
            }
        }

        plugin.logger.info("Выгружено $unloadedCount миров плотов")
    }

    /**
     * Запускается при остановке плагина
     */

    /**
     * Перемещает папку мира из одного места в другое
     */
    private fun moveWorldFolder(source: File, destination: File) {
        if (destination.exists()) {
            deleteDirectory(destination)
        }

        try {
            // Пытаемся использовать Files.move для перемещения директории
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            // Если не удалось переместить, копируем и удаляем исходную директорию
            copyDirectory(source, destination)
            deleteDirectory(source)
        }
    }

    /**
     * Копирует директорию со всем содержимым
     */
    private fun copyDirectory(source: File, destination: File) {
        if (!destination.exists()) {
            destination.mkdirs()
        }

        val sourcePath = source.toPath()
        val destinationPath = destination.toPath()

        Files.walkFileTree(sourcePath, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val targetDir = destinationPath.resolve(sourcePath.relativize(dir))
                try {
                    Files.createDirectories(targetDir)
                } catch (e: Exception) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val targetFile = destinationPath.resolve(sourcePath.relativize(file))
                try {
                    Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING)
                } catch (e: Exception) {
                    // Игнорируем ошибки отдельных файлов
                }
                return FileVisitResult.CONTINUE
            }
        })
    }

    /**
     * Удаляет директорию со всем содержимым
     */
    private fun deleteDirectory(directory: File) {
        try {
            Files.walkFileTree(directory.toPath(), object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (e: Exception) {
            // Игнорируем ошибки
        }
    }

    // Добавьте эти поля в класс PlotManager

    // Задержка перед выгрузкой пустого мира (в минутах)
    private val worldUnloadDelay = plugin.config.getLong("world-unload-delay", 5)

    // Хранит планировщики выгрузки миров по имени мира
    private val worldUnloadTasks = ConcurrentHashMap<String, BukkitTask>()

// Добавьте эти методы

    /**
     * Пытается загрузить мир плота, если игрок пытается телепортироваться
     * @return true, если мир загружен или был уже загружен
     */
    fun ensurePlotWorldLoaded(plotName: String): Boolean {
        val world = Bukkit.getWorld("plot_${plotName.lowercase().replace(" ", "_")}")

        // Если мир уже загружен, просто возвращаем true
        if (world != null) {
            return true
        }

        // Пытаемся загрузить мир
        return loadPlotWorld(plotName) != null
    }

    /**
     * Отмечает, что игрок вошел в мир плота
     */
    fun playerEnteredPlotWorld(worldName: String) {
        // Отменяем задачу выгрузки мира, если она была запланирована
        cancelUnloadTask(worldName)
    }

    /**
     * Отмечает, что игрок покинул мир плота
     */
    fun playerLeftPlotWorld(worldName: String) {
        val world = Bukkit.getWorld(worldName) ?: return

        // Проверяем, остались ли игроки в мире
        if (world.players.isEmpty()) {
            // Если мир пуст, планируем его выгрузку через заданный интервал
            scheduleWorldUnload(worldName)
        }
    }

    /**
     * Планирует выгрузку мира через заданный интервал
     */
    private fun scheduleWorldUnload(worldName: String) {
        // Отменяем предыдущую задачу, если она существует
        cancelUnloadTask(worldName)

        // Если задержка выгрузки равна 0, миры не выгружаются автоматически
        if (worldUnloadDelay <= 0) return

        // Создаем новую задачу выгрузки
        val task = object : BukkitRunnable() {
            override fun run() {
                val world = Bukkit.getWorld(worldName)
                if (world != null && world.players.isEmpty()) {
                    try {
                        // Сохраняем мир перед выгрузкой
                        world.save()

                        // Перемещаем мир в папку плагина
                        val rootWorldFolder = File(Bukkit.getWorldContainer(), worldName)
                        val pluginWorldFolder = File(plotsWorldsFolder, worldName)

                        if (rootWorldFolder.exists() && rootWorldFolder.isDirectory) {
                            if (!pluginWorldFolder.exists()) {
                                moveWorldFolder(rootWorldFolder, pluginWorldFolder)
                            } else {
                                deleteDirectory(pluginWorldFolder)
                                copyDirectory(rootWorldFolder, pluginWorldFolder)
                            }
                        }

                        // Выгружаем мир
                        val success = Bukkit.unloadWorld(world, true)
                        if (success) {
                            plugin.logger.info("Выгружен пустой мир: $worldName")
                        } else {
                            plugin.logger.warning("Не удалось выгрузить пустой мир: $worldName")
                        }
                    } catch (e: Exception) {
                        plugin.logger.severe("Ошибка выгрузки мира $worldName: ${e.message}")
                        e.printStackTrace()
                    }
                }

                // Удаляем задачу из списка
                worldUnloadTasks.remove(worldName)
            }
        }.runTaskLater(plugin, worldUnloadDelay * 60 * 20) // минуты в тики

        // Сохраняем задачу в реестре
        worldUnloadTasks[worldName] = task

        plugin.logger.info("Запланирована выгрузка пустого мира $worldName через $worldUnloadDelay минут")
    }

    /**
     * Отменяет запланированную выгрузку мира
     */
    private fun cancelUnloadTask(worldName: String) {
        val task = worldUnloadTasks.remove(worldName)
        task?.cancel()
    }

    /**
     * Отменяет все задачи выгрузки миров
     */
    private fun cancelAllUnloadTasks() {
        worldUnloadTasks.values.forEach { it.cancel() }
        worldUnloadTasks.clear()
    }

    // Обновите метод shutdown()
    fun shutdown() {
        // Останавливаем автосохранение
        stopAutoSave()

        // Отменяем все запланированные выгрузки миров
        cancelAllUnloadTasks()

        // Сохраняем все миры
        saveAllPlotWorlds()

        // Выгружаем все миры
        unloadAllPlotWorlds()
    }

    private fun deleteWorldCompletely(worldName: String, plot: Plot): Boolean {
        // Отменяем все задачи, связанные с этим миром
        cancelUnloadTask(worldName)

        // Выгружаем мир, если он загружен
        val world = Bukkit.getWorld(worldName)
        if (world != null) {
            // Телепортируем всех игроков из мира
            val defaultWorld = Bukkit.getWorlds()[0]
            for (player in world.players) {
                player.teleport(defaultWorld.spawnLocation)
                player.sendMessage("§cПлот, на котором вы находились, был удален.")
            }

            try {
                // Выгружаем мир
                if (!Bukkit.unloadWorld(world, false)) { // false = не сохраняем мир при выгрузке
                    plugin.logger.warning("Не удалось выгрузить мир $worldName")
                    // Продолжаем, чтобы попытаться удалить файлы мира в любом случае
                }
            } catch (e: Exception) {
                plugin.logger.severe("Ошибка при выгрузке мира $worldName: ${e.message}")
                // Продолжаем, чтобы попытаться удалить файлы мира в любом случае
            }
        }

        // Очищаем кэш чанков сервера (метод доступен только через отражение)
        try {
            clearChunkCache()
        } catch (e: Exception) {
            plugin.logger.warning("Не удалось очистить кэш чанков: ${e.message}")
        }

        // Удаляем файлы мира из всех возможных местоположений
        var success = true

        // 1. Удаляем из папки плагина
        val pluginWorldFolder = File(plotsWorldsFolder, worldName)
        if (pluginWorldFolder.exists()) {
            if (!deleteWorldDirectoryComplete(pluginWorldFolder)) {
                plugin.logger.warning("Не удалось полностью удалить мир из папки плагина: $worldName")
                success = false
            }
        }

        // 2. Удаляем из корневой директории сервера
        val rootWorldFolder = File(Bukkit.getWorldContainer(), worldName)
        if (rootWorldFolder.exists()) {
            if (!deleteWorldDirectoryComplete(rootWorldFolder)) {
                plugin.logger.warning("Не удалось полностью удалить мир из корневой директории: $worldName")
                success = false
            }
        }

        // 3. Проверяем и удаляем старые сессии и другие связанные файлы
        try {
            val sessionFolder = File(Bukkit.getWorldContainer(), "session.lock")
            if (sessionFolder.exists()) {
                sessionFolder.delete()
            }

            val uidFile = File(rootWorldFolder, "uid.dat")
            if (uidFile.exists()) {
                uidFile.delete()
            }
        } catch (e: Exception) {
            plugin.logger.warning("Ошибка удаления служебных файлов мира: ${e.message}")
        }

        // Запускаем сборщик мусора, чтобы освободить память
        System.gc()

        return success
    }

    /**
     * Полное удаление директории мира с особым вниманием к файлам регионов
     * @param directory директория мира для удаления
     * @return true если директория была полностью удалена
     */
    private fun deleteWorldDirectoryComplete(directory: File): Boolean {
        if (!directory.exists()) {
            return true
        }

        var success = true

        try {
            // Особое внимание к папкам регионов (region, DIM1/region, DIM-1/region)
            val regionFolders = listOf(
                File(directory, "region"),
                File(directory, "DIM1/region"),
                File(directory, "DIM-1/region")
            )

            // Сначала удаляем все файлы регионов (r.*.*.mca)
            for (regionFolder in regionFolders) {
                if (regionFolder.exists() && regionFolder.isDirectory) {
                    val regionFiles = regionFolder.listFiles { file ->
                        file.name.startsWith("r.") && file.name.endsWith(".mca")
                    }

                    regionFiles?.forEach { file ->
                        if (!file.delete()) {
                            // Если не удалось удалить файл напрямую, пробуем принудительно
                            forceDeleteFile(file)
                            success = false
                        }
                    }
                }
            }

            // Затем удаляем всю директорию рекурсивно
            Files.walkFileTree(directory.toPath(), object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    try {
                        Files.delete(file)
                    } catch (e: Exception) {
                        success = false
                        plugin.logger.warning("Не удалось удалить файл: ${file.fileName}")
                        // Пробуем принудительно удалить
                        forceDeleteFile(file.toFile())
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    try {
                        Files.delete(dir)
                    } catch (e: Exception) {
                        success = false
                        plugin.logger.warning("Не удалось удалить директорию: ${dir.fileName}")
                    }
                    return FileVisitResult.CONTINUE
                }
            })

            // Проверяем, что директория действительно удалена
            if (directory.exists()) {
                plugin.logger.warning("Директория все еще существует после попытки удаления: ${directory.absolutePath}")
                success = false
            }
        } catch (e: Exception) {
            plugin.logger.severe("Ошибка при удалении директории мира: ${e.message}")
            e.printStackTrace()
            success = false
        }

        return success
    }

    /**
     * Принудительное удаление файла с использованием JVM garbage collector
     */
    private fun forceDeleteFile(file: File) {
        try {
            // Переименовываем файл во временный для разрыва связей
            val tempFile = File(file.parentFile, "temp_delete_${System.currentTimeMillis()}_${file.name}")
            if (file.renameTo(tempFile)) {
                // Устанавливаем файл на удаление при выходе из JVM
                tempFile.deleteOnExit()

                // Пытаемся принудительно вызвать GC
                System.gc()
                Thread.sleep(100)

                // Пробуем удалить переименованный файл
                tempFile.delete()
            }
        } catch (e: Exception) {
            plugin.logger.warning("Не удалось принудительно удалить файл: ${file.absolutePath}")
        }
    }

    /**
     * Очистка кэша чанков сервера через отражение
     */
    private fun clearChunkCache() {
        try {
            val craftServer = Bukkit.getServer()
            val serverClass = craftServer.javaClass

            // Получаем доступ к внутреннему серверу через отражение
            val getServerMethod = serverClass.getMethod("getServer")
            getServerMethod.isAccessible = true
            val nmsServer = getServerMethod.invoke(craftServer)

            // Ищем метод для очистки кэша чанков
            nmsServer.javaClass.methods.forEach { method ->
                if ((method.name == "getChunkProvider" || method.name == "getChunkSource") && method.parameterCount == 0) {
                    val chunkProvider = method.invoke(nmsServer)

                    // Ищем методы очистки кэша в ChunkProvider
                    chunkProvider.javaClass.methods.forEach { providerMethod ->
                        if (providerMethod.name.contains("clear") || providerMethod.name.contains("flush")) {
                            providerMethod.invoke(chunkProvider)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Игнорируем ошибки, так как это опциональный шаг
        }
    }
}