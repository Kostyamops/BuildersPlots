package ru.joutak.buildersplots

import org.bukkit.plugin.java.JavaPlugin
import ru.joutak.buildersplots.commands.CommandManager
import ru.joutak.buildersplots.config.PluginConfig
import ru.joutak.buildersplots.i18n.Messages
import ru.joutak.buildersplots.manager.PlotManager
import ru.joutak.buildersplots.network.NetworkManager
import ru.joutak.buildersplots.network.ServerRole

class BuildersPlots : JavaPlugin() {

    // Использование private свойств с getters для избежания проблем с инициализацией
    private var _config: PluginConfig? = null
    private var _plotManager: PlotManager? = null
    private var _networkManager: NetworkManager? = null
    private var _messages: Messages? = null
    
    val config: PluginConfig
        get() = _config ?: throw IllegalStateException("Config not initialized")
    
    val plotManager: PlotManager
        get() = _plotManager ?: throw IllegalStateException("PlotManager not initialized")
    
    val networkManager: NetworkManager
        get() = _networkManager ?: throw IllegalStateException("NetworkManager not initialized")
    
    val messages: Messages
        get() = _messages ?: throw IllegalStateException("Messages not initialized")

    override fun onEnable() {
        try {
            // Сначала сохраняем дефолтные конфиги
            saveDefaultConfig()
            saveResource("messages_en.yml", false)
            saveResource("messages_ru.yml", false)
            
            // Инициализация компонентов
            _config = PluginConfig(this)
            _messages = Messages(this)
            _plotManager = PlotManager(this)
            _networkManager = NetworkManager(this)

            // Регистрация команд
            CommandManager(this).registerCommands()

            // Запуск сетевых сервисов в зависимости от роли сервера
            when (_config?.serverRole) {
                ServerRole.SENDER -> {
                    logger.info("[BuildersPlots] Starting in SENDER mode")
                    _networkManager?.startSender()
                }
                ServerRole.RECEIVER -> {
                    logger.info("[BuildersPlots] Starting in RECEIVER mode")
                    _networkManager?.startReceiver()
                }
                else -> {
                    logger.warning("[BuildersPlots] Unknown server role, plugin may not function correctly")
                }
            }

            logger.info("[BuildersPlots] Plugin enabled successfully!")
        } catch (e: Exception) {
            logger.severe("[BuildersPlots] Failed to enable plugin: ${e.message}")
            e.printStackTrace()
            server.pluginManager.disablePlugin(this)
        }
    }

    override fun onDisable() {
        try {
            // Безопасное выключение сетевых соединений
            _networkManager?.shutdown()

            // Сохранение данных плотов
            _plotManager?.savePlots()

            logger.info("[BuildersPlots] Plugin disabled!")
        } catch (e: Exception) {
            logger.severe("[BuildersPlots] Error during plugin shutdown: ${e.message}")
            e.printStackTrace()
        }
    }
}