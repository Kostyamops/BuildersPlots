package ru.joutak.buildersplots.i18n

import org.bukkit.configuration.file.YamlConfiguration
import ru.joutak.buildersplots.BuildersPlots
import java.io.File

class Messages(private val plugin: BuildersPlots) {

    private var messages: YamlConfiguration
    private val language: Lang

    init {
        // Получение языка из конфига
        val langCode = plugin.getConfig().getString("language") ?: "en"
        language = Lang.fromCode(langCode)
        plugin.logger.info("[BuildersPlots] Using language: ${language.name}")

        // Загрузка файла сообщений
        val messagesFile = File(plugin.dataFolder, language.fileName)
        messages = YamlConfiguration.loadConfiguration(messagesFile)
    }

    fun get(key: String, vararg args: Any): String {
        val message = messages.getString(key) ?: key
        if (args.isEmpty()) return message.replace("&", "§")
        
        // Подстановка аргументов {0}, {1}, ...
        var result = message
        args.forEachIndexed { index, arg ->
            result = result.replace("{$index}", arg.toString())
        }
        return result.replace("&", "§")
    }

    fun reload() {
        val messagesFile = File(plugin.dataFolder, language.fileName)
        messages = YamlConfiguration.loadConfiguration(messagesFile)
    }
}