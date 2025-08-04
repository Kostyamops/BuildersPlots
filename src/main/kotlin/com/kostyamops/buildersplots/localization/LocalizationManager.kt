package com.kostyamops.buildersplots.localization

import com.kostyamops.buildersplots.BuildersPlots
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.logging.Level

/**
 * Менеджер локализаций для плагина
 * Подгружает и управляет языковыми файлами
 */
class LocalizationManager(private val plugin: BuildersPlots) {

    private lateinit var messages: YamlConfiguration
    private lateinit var currentLanguage: String

    /**
     * Проверка статуса debug режима
     */
    private val debugEnabled: Boolean
        get() = plugin.config.getBoolean("debug.enabled", false)

    /**
     * Инициализирует менеджер локализаций
     */
    fun initialize() {
        try {
            // Создаем папку для языковых файлов, если ее нет
            val langFolder = File(plugin.dataFolder, "lang")
            if (!langFolder.exists()) {
                langFolder.mkdirs()
            }

            // Сохраняем стандартные языковые файлы из ресурсов плагина
            saveDefaultLanguageFile("ru-RU.yml")
            saveDefaultLanguageFile("en-US.yml")
            saveDefaultLanguageFile("es-ES.yml")

            // Загружаем выбранный в конфиге язык
            loadLanguage(plugin.config.getString("chat.language", "en-US"))

            if (debugEnabled) {
                plugin.logger.info(getLogMessage("logs.debug.enabled"))
            } else {
                plugin.logger.info(getLogMessage("logs.debug.disabled"))
            }

        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Error initializing localizations", e)
        }
    }

    /**
     * Загружает указанный языковой файл
     */
    fun loadLanguage(lang: String?) {
        val language = lang ?: "en-US"
        val langFile = File(plugin.dataFolder, "lang/$language.yml")

        if (!langFile.exists()) {
            plugin.logger.warning("Language file $language.yml not found. Using en-US.yml")
            // Если файл не найден, используем английский по умолчанию
            loadLanguage("en-US")
            return
        }

        messages = YamlConfiguration.loadConfiguration(langFile)
        currentLanguage = language
    }

    /**
     * Сохраняет языковой файл из ресурсов плагина
     */
    private fun saveDefaultLanguageFile(filename: String) {
        val langFile = File(plugin.dataFolder, "lang/$filename")

        if (!langFile.exists()) {
            try {
                plugin.saveResource("lang/$filename", false)
            } catch (e: Exception) {
                plugin.logger.warning("Failed to save language file $filename: ${e.message}")
            }
        }
    }

    /**
     * Получает сообщение по ключу
     */
    fun getMessage(key: String, vararg args: Pair<String, String>): String {
        var message = messages.getString(key) ?: key

        // Добавляем префикс, если сообщение не содержит [NOPREFIX] в начале
        if (!message.startsWith("[NOPREFIX]")) {
            message = "${plugin.prefix} $message"
        } else {
            message = message.substring(10) // Убираем [NOPREFIX]
        }

        // Заменяем плейсхолдеры
        args.forEach { (placeholder, value) ->
            message = message.replace(placeholder, value)
        }

        // Заменяем цветовые коды
        return ChatColor.translateAlternateColorCodes('&', message)
    }

    /**
     * Получает сообщение лога по ключу (с сохранением цветов для консоли)
     */
    fun getLogMessage(key: String, vararg args: Pair<String, String>): String {
        val message = messages.getString(key) ?: key

        // Заменяем плейсхолдеры
        val resultMessage = args.fold(message) { msg, (placeholder, value) ->
            msg.replace(placeholder, value)
        }

        //  // Переводим цветовые коды и возвращаем
        return ChatColor.translateAlternateColorCodes('&', resultMessage)
    }

    /**
     * Получает сообщение лога с цветами (для игроков с правами)
     */
    fun getColoredLogMessage(key: String, vararg args: Pair<String, String>): String {
        // Используем тот же метод, так как теперь мы сохраняем цвета в консоли
        return getLogMessage(key, *args)
    }

    /**
     * Логирует информационное сообщение в консоль
     * Выводится только если включен debug режим
     */
    fun info(key: String, vararg args: Pair<String, String>) {
        // Проверяем режим debug перед выводом информационных сообщений
        if (debugEnabled) {
            val message = getLogMessage(key, *args)
            plugin.logger.info(message)
        }
    }

    /**
     * Логирует предупреждение в консоль
     * Выводится всегда
     */
    fun warning(key: String, vararg args: Pair<String, String>) {
        val message = getLogMessage(key, *args)
        plugin.logger.warning(message)
    }

    /**
     * Логирует ошибку в консоль
     * Выводится всегда
     */
    fun severe(key: String, vararg args: Pair<String, String>) {
        val message = getLogMessage(key, *args)
        plugin.logger.severe(message)
    }

    /**
     * Отправляет сообщение игроку или в консоль
     */
    fun sendMessage(sender: CommandSender, key: String, vararg args: Pair<String, String>) {
        sender.sendMessage(getMessage(key, *args))
    }

    /**
     * Отправляет сообщение в ActionBar игрока
     */
    fun sendActionBar(player: Player, key: String, vararg args: Pair<String, String>) {
        val message = getMessage(key, *args)
        player.spigot().sendMessage(
            net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
            net.md_5.bungee.api.chat.TextComponent(message)
        )
    }

    /**
     * Отправляет заголовок и подзаголовок игроку
     */
    fun sendTitle(
        player: Player,
        titleKey: String,
        subtitleKey: String,
        fadeIn: Int = 10,
        stay: Int = 70,
        fadeOut: Int = 20,
        vararg args: Pair<String, String>
    ) {
        val title = getMessage(titleKey, *args)
        val subtitle = getMessage(subtitleKey, *args)
        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut)
    }

    /**
     * Проверяет наличие ключа в языковом файле
     */
    fun hasMessage(key: String): Boolean {
        return messages.contains(key)
    }

    /**
     * Возвращает текущий загруженный язык
     */
    fun getCurrentLanguage(): String {
        return currentLanguage
    }
}