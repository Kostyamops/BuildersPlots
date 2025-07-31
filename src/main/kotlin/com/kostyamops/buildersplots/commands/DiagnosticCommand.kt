package com.kostyamops.buildersplots.commands

import com.kostyamops.buildersplots.BuildersPlots
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.net.Socket
import java.io.IOException
import java.util.UUID

class DiagnosticCommand(private val plugin: BuildersPlots) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (command.name.equals("bpdiag", ignoreCase = true)) {

            sender.sendMessage("§e===== BuildersPlots Диагностика =====")

            // Проверка конфигурации
            val sendPort = plugin.config.getInt("server.sendPort")
            val receivePort = plugin.config.getInt("server.receivePort")
            val isMainServer = plugin.config.getBoolean("server.isMainServer")
            val targetIp = plugin.config.getString("server.targetServerIp") ?: "localhost"

            sender.sendMessage("§eКонфигурация:")
            sender.sendMessage("§7- Основной сервер: §f$isMainServer")
            sender.sendMessage("§7- Целевой IP: §f$targetIp")
            sender.sendMessage("§7- Порт отправки: §f$sendPort")
            sender.sendMessage("§7- Порт приема: §f$receivePort")

            // Проверка соединения
            sender.sendMessage("§eПроверка соединения:")
            val connectPort = if (isMainServer) sendPort else receivePort

            try {
                Socket(targetIp, connectPort).use { socket ->
                    sender.sendMessage("§aСоединение с $targetIp:$connectPort успешно!")
                }
            } catch (e: IOException) {
                sender.sendMessage("§cОшибка соединения с $targetIp:$connectPort: ${e.message}")

                // Предложение решения
                if (e.message?.contains("Connection refused") == true) {
                    sender.sendMessage("§eВозможное решение: убедитесь, что целевой сервер запущен и порт открыт.")
                }
            }

            // Проверка плотов
            val plots = plugin.plotManager.getAllPlots()
            sender.sendMessage("§eПлоты (${plots.size}):")
            if (plots.isEmpty()) {
                sender.sendMessage("§7- Нет доступных плотов")
            } else {
                plots.forEachIndexed { index, plot ->
                    // Получаем имя владельца по UUID
                    val ownerName = getPlayerNameByUUID(plot.createdBy)

                    sender.sendMessage("§7${index+1}. §f${plot.name} §7(${plot.id})")
                    sender.sendMessage("§7   Мир: §f${plot.worldName}, Владелец: §f$ownerName")
                }
            }

            // Доп. информация для отладки
            sender.sendMessage("§eВремя сервера: §f${java.time.LocalDateTime.now()}")

            return true
        }

        return false
    }

    // Вспомогательный метод для получения имени игрока по UUID
    private fun getPlayerNameByUUID(uuid: UUID): String {
        // Сначала проверяем онлайн игроков
        val onlinePlayer = Bukkit.getPlayer(uuid)
        if (onlinePlayer != null) {
            return onlinePlayer.name
        }

        // Затем проверяем в оффлайн данных
        val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
        if (offlinePlayer.name != null) {
            return offlinePlayer.name ?: "Неизвестный"
        }

        // Если имя не найдено, возвращаем UUID как строку
        return uuid.toString()
    }
}