package ru.joutak.buildersplots

import org.bukkit.plugin.java.JavaPlugin
import ru.joutak.buildersplots.config.PluginConfig
import ru.joutak.buildersplots.manager.CommandManager
import ru.joutak.buildersplots.manager.PlotManager
import ru.joutak.buildersplots.network.NetworkManager
import ru.joutak.buildersplots.network.ServerRole

class BuildersPlots : JavaPlugin() {

    // Инициализация с null значениями вместо lateinit
    private var _config: PluginConfig? = null
    private var _plotManager: PlotManager? = null
    private var _networkManager: NetworkManager? = null

    // Свойства с геттерами для безопасного доступа
    val config: PluginConfig
        get() = _config ?: throw IllegalStateException("Config has not been initialized")

    val plotManager: PlotManager
        get() = _plotManager ?: throw IllegalStateException("PlotManager has not been initialized")

    val networkManager: NetworkManager
        get() = _networkManager ?: throw IllegalStateException("NetworkManager has not been initialized")

    override fun onEnable() {
        try {
            // Сначала сохраняем дефолтный конфиг
            saveDefaultConfig()

            // Инициализация компонентов
            _config = PluginConfig(this)
            _plotManager = PlotManager(this)
            _networkManager = NetworkManager(this)

            // Регистрация команд
            CommandManager(this).registerCommands()

            // Запуск сетевых сервисов в зависимости от роли сервера
            when (_config?.serverRole) {
                ServerRole.SENDER -> {
                    logger.info("Starting BuildersPlots in SENDER mode")
                    registerSenderListeners()
                }
                ServerRole.RECEIVER -> {
                    logger.info("Starting BuildersPlots in RECEIVER mode")
                    registerReceiverListeners()
                }
                else -> {
                    logger.warning("Unknown server role, plugin may not function correctly")
                }
            }

            logger.info("BuildersPlots has been enabled!")
        } catch (e: Exception) {
            logger.severe("Failed to enable BuildersPlots: ${e.message}")
            e.printStackTrace()
            server.pluginManager.disablePlugin(this)
        }
    }

    override fun onDisable() {
        try {
            // Безопасное выключение сетевых соединений
            _networkManager?.shutdown()

            // Сохранение данных плотов
            if (_config?.serverRole == ServerRole.RECEIVER) {
                _plotManager?.savePlots()
            }

            logger.info("BuildersPlots has been disabled!")
        } catch (e: Exception) {
            logger.severe("Error during plugin shutdown: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun registerSenderListeners() {
        server.pluginManager.registerEvents(
            ru.joutak.buildersplots.listener.BlockChangeListener(this),
            this
        )
    }

    private fun registerReceiverListeners() {
        server.pluginManager.registerEvents(
            ru.joutak.buildersplots.listener.PlotInteractionListener(this),
            this
        )
    }
}