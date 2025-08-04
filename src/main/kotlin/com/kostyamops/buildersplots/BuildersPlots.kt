package com.kostyamops.buildersplots

import com.kostyamops.buildersplots.commands.BuildersPlotsCommand
import com.kostyamops.buildersplots.data.PlotManager
import com.kostyamops.buildersplots.listeners.BlockChangeListener
import com.kostyamops.buildersplots.localization.LocalizationManager
import com.kostyamops.buildersplots.network.ServerCommunicationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bukkit.ChatColor
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class BuildersPlots : JavaPlugin() {

    lateinit var plotManager: PlotManager
    lateinit var communicationManager: ServerCommunicationManager
    lateinit var localizationManager: LocalizationManager
    lateinit var serverType: ServerType
    lateinit var prefix: String

    private val pluginScope = CoroutineScope(Dispatchers.IO)

    override fun onEnable() {
        try {
            // Сохраняем дефолтный конфиг, если его нет
            saveDefaultConfig()

            val rawPrefix = config.getString("chat.prefix", "&f[&4BuildersPlots&f]") ?: "&f[&4BuildersPlots&f]"
            prefix = ChatColor.translateAlternateColorCodes('&', rawPrefix)

            // Инициализация менеджера локализаций
            localizationManager = LocalizationManager(this)
            localizationManager.initialize()

            // Логи запуска с использованием локализации
            localizationManager.info("logs.buildersplots.loading", "%version%" to description.version)

            // Определяем тип сервера
            val serverTypeStr = config.getString("server-type", "main")
            serverType = try {
                ServerType.valueOf(serverTypeStr!!.uppercase())
            } catch (e: IllegalArgumentException) {
                localizationManager.warning("logs.buildersplots.invalid_server_type", "%type%" to serverTypeStr!!)
                ServerType.MAIN
            }

            localizationManager.info("logs.buildersplots.server_type_set", "%type%" to serverType.name)

            // Создаем директорию для данных плотов
            val dataDir = File(dataFolder, "plots")
            if (!dataDir.exists()) {
                dataDir.mkdirs()
                localizationManager.info("logs.plotmanager.directory.created", "%path%" to dataDir.absolutePath)
            }

            // Инициализация менеджеров и слушателей
            plotManager = PlotManager(this)
            communicationManager = ServerCommunicationManager(this)
            server.pluginManager.registerEvents(BlockChangeListener(this), this)

            // Регистрация команд
            getCommand("bp")?.setExecutor(BuildersPlotsCommand(this))
            localizationManager.info("logs.buildersplots.commands_registered")

            // Дополнительные настройки для тестового сервера
            if (serverType == ServerType.TEST) {
                plotManager.loadAllPlotWorlds()
                plotManager.startAutoSave()
                localizationManager.info("logs.buildersplots.test_worlds_loaded")
            }

            // Запуск сетевой коммуникации
            pluginScope.launch {
                communicationManager.startCommunication()
            }
            localizationManager.info("logs.servercommunication.starting_server",
                "%port%" to config.getString("communication.port", "25567")!!)

            // Финальное сообщение об успешном запуске
            localizationManager.info("logs.buildersplots.enabled")
        } catch (e: Exception) {
            if (::localizationManager.isInitialized) {
                localizationManager.severe("logs.buildersplots.enable_failed", "%error%" to e.message.toString())
            } else {
                logger.severe("Error enabling BuildersPlots: ${e.message}")
            }
            e.printStackTrace()
            server.pluginManager.disablePlugin(this)
        }
    }

    override fun onDisable() {
        try {
            // Сохранение данных перед выключением
            if (::plotManager.isInitialized) {
                plotManager.savePlots()
                localizationManager.info("logs.plotmanager.plots.saved", "%count%" to "all")
            }

            // Остановка сетевой коммуникации
            if (::communicationManager.isInitialized) {
                pluginScope.launch {
                    communicationManager.stopCommunication()
                }
                localizationManager.info("logs.buildersplots.network_stopping")
            }

            // Завершение работы менеджера плотов
            if (::plotManager.isInitialized) {
                plotManager.shutdown()
            }

            // Сообщение о выключении
            if (::localizationManager.isInitialized) {
                localizationManager.info("logs.buildersplots.disabled")
            } else {
                logger.info("BuildersPlots plugin disabled!")
            }
        } catch (e: Exception) {
            if (::localizationManager.isInitialized) {
                localizationManager.severe("logs.buildersplots.disable_failed", "%error%" to e.message.toString())
            } else {
                logger.severe("Error disabling BuildersPlots: ${e.message}")
            }
            e.printStackTrace()
        }
    }

    /**
     * Перезагружает конфигурацию и языковые файлы
     */
    fun reload() {
        reloadConfig()
        // Не забываем перевести цветовые коды при перезагрузке конфига
        val rawPrefix = config.getString("chat.prefix", "&f[&4BuildersPlots&f]") ?: "&f[&4BuildersPlots&f]"
        prefix = ChatColor.translateAlternateColorCodes('&', rawPrefix)

        if (::localizationManager.isInitialized) {
            localizationManager.loadLanguage(config.getString("chat.language"))
            localizationManager.info("logs.buildersplots.config_reloaded")
        }
    }
}

enum class ServerType {
    MAIN, TEST
}