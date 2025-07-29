package ru.joutak.buildersplots.config

import ru.joutak.buildersplots.BuildersPlots
import ru.joutak.buildersplots.network.ServerRole
import java.lang.reflect.Method

class PluginConfig(private val plugin: BuildersPlots) {

    val serverRole: ServerRole
    val receiverPort: Int
    val senderPort: Int
    val receiverHost: String
    val plotsDirectory: String

    init {
        // Используем getConfig() из JavaPlugin вместо свойства config
        val config = plugin.getConfig()

        // Получение методов
        val getStringMethod: Method = config.javaClass.getMethod("getString", String::class.java)
        val getStringDefaultMethod: Method = config.javaClass.getMethod("getString", String::class.java, String::class.java)
        val getIntMethod: Method = config.javaClass.getMethod("getInt", String::class.java, Int::class.javaPrimitiveType)

        // Load server role
        val roleString = getStringDefaultMethod.invoke(config, "server-role", "sender") as String
        serverRole = try {
            ServerRole.valueOf(roleString.uppercase())
        } catch (e: IllegalArgumentException) {
            plugin.logger.warning("Invalid server role: $roleString. Defaulting to SENDER.")
            ServerRole.SENDER
        }

        // Load network settings
        receiverPort = getIntMethod.invoke(config, "receiver-port", 5555) as Int
        senderPort = getIntMethod.invoke(config, "sender-port", 5556) as Int

        val receiverHostObj = getStringMethod.invoke(config, "receiver-host")
        receiverHost = receiverHostObj?.toString() ?: "localhost"

        val plotsDirObj = getStringMethod.invoke(config, "plots-directory")
        plotsDirectory = plotsDirObj?.toString() ?: "plots"

        // Create plots directory if it doesn't exist (on receiver)
        if (serverRole == ServerRole.RECEIVER) {
            val plotsDir = plugin.dataFolder.resolve(plotsDirectory)
            if (!plotsDir.exists()) {
                plotsDir.mkdirs()
            }
        }
    }

    fun saveDefaultConfig() {
        plugin.saveDefaultConfig()
    }
}