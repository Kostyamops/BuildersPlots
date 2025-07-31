package com.kostyamops.buildersplots.commands

import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.network.packets.PlotManagementPacket
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.net.Socket

class TestPacketCommand(private val plugin: BuildersPlots) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (command.name.equals("bptest", ignoreCase = true)) {
            testDirectConnection(sender)

            sender.sendMessage("§eОтправка тестового пакета...")

            // Создаем простой тестовый пакет
            val packet = PlotManagementPacket(
                action = "TEST_PACKET",
                data = mapOf(
                    "timestamp" to System.currentTimeMillis(),
                    "source" to "TestCommand",
                    "sender" to (if (sender is Player) sender.name else "Console")
                )
            )

            // Отправляем пакет напрямую
            plugin.serverConnection.sendPacket(packet)

            sender.sendMessage("§aТестовый пакет отправлен. Проверьте логи обоих серверов.")
            return true
        }

        return false
    }

    // Добавить метод в TestPacketCommand
    private fun testDirectConnection(sender: CommandSender) {
        try {
            val targetIp = plugin.config.getString("server.targetServerIp") ?: "localhost"
            val port = plugin.config.getInt("server.sendPort")

            sender.sendMessage("§eПроверка прямого соединения с $targetIp:$port...")

            Socket(targetIp, port).use { socket ->
                sender.sendMessage("§aСоединение установлено! Порт открыт и доступен.")
            }
        } catch (e: Exception) {
            sender.sendMessage("§cОшибка соединения: ${e.message}")
        }
    }
}