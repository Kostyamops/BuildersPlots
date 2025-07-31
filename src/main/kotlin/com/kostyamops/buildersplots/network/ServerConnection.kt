package com.kostyamops.buildersplots.network

import com.kostyamops.buildersplots.BuildersPlots
import com.kostyamops.buildersplots.network.packets.Packet
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.logging.Level

class ServerConnection(
    private val plugin: BuildersPlots,
    private val sendPort: Int,
    private val receivePort: Int,
    private val isMainServer: Boolean
) {
    private var running = false
    private var receiverThread: Thread? = null
    private var senderThread: Thread? = null
    private var serverSocket: ServerSocket? = null

    private val packetQueue = ConcurrentLinkedQueue<Packet>()

    fun startConnection() {
        running = true

        // Добавим больше отладочной информации
        plugin.log("Инициализация соединения... Порт отправки: $sendPort, Порт приема: $receivePort")

        // Запускаем отдельный поток для проверки сети сразу при старте
        Thread {
            try {
                // Проверяем, открыт ли порт приема
                try {
                    Socket("127.0.0.1", receivePort).use { _ ->
                        plugin.log("ПРЕДУПРЕЖДЕНИЕ: Порт $receivePort уже используется! Это может привести к проблемам.")
                    }
                } catch (e: IOException) {
                    plugin.log("Порт $receivePort свободен и готов к использованию.")
                }

                // Тестовое соединение с целевым сервером
                try {
                    Socket("127.0.0.1", sendPort).use { socket ->
                        plugin.log("Успешно установлено тестовое соединение с портом $sendPort")
                    }
                } catch (e: IOException) {
                    plugin.log("ОШИБКА: Не удалось подключиться к порту $sendPort. Убедитесь, что другой сервер запущен.")
                }
            } catch (e: Exception) {
                plugin.log("Ошибка при диагностике сети: ${e.message}")
            }
        }.start()

        // Start receiver thread
        receiverThread = Thread {
            runReceiver()
        }.apply {
            name = "BuildersPlots-Receiver"
            isDaemon = true
            start()
        }

        // Start sender thread
        senderThread = Thread {
            runSender()
        }.apply {
            name = "BuildersPlots-Sender"
            isDaemon = true
            start()
        }

        plugin.log("Network connection started. Listening on port $receivePort, sending to port $sendPort")
    }

    fun stopConnection() {
        running = false

        try {
            serverSocket?.close()
        } catch (e: Exception) {
            plugin.log("Error closing sockets: ${e.message}")
        }

        receiverThread?.interrupt()
        senderThread?.interrupt()

        plugin.log("Network connection stopped")
    }

    fun sendPacket(packet: Packet) {
        packetQueue.add(packet)
        plugin.log("Пакет добавлен в очередь: ${packet.javaClass.simpleName}. Размер очереди: ${packetQueue.size}")
    }

    private fun runReceiver() {
        try {
            serverSocket = ServerSocket(receivePort)
            plugin.log("Приемник запущен на порту $receivePort и ожидает соединений...")

            while (running) {
                try {
                    val socket = serverSocket!!.accept()
                    plugin.log("Получено соединение от ${socket.inetAddress.hostAddress}:${socket.port}")

                    try {
                        ObjectInputStream(socket.getInputStream()).use { input ->
                            try {
                                val packet = input.readObject()
                                plugin.log("Объект получен: ${packet?.javaClass?.name}")

                                if (packet is Packet) {
                                    plugin.log("Получен пакет типа: ${packet.javaClass.simpleName}")

                                    // Выполняем обработку пакета в глобальном регионе
                                    plugin.server.globalRegionScheduler.execute(plugin, Runnable {
                                        try {
                                            when (packet) {
                                                is com.kostyamops.buildersplots.network.packets.BlockChangePacket ->
                                                    plugin.networkManager.handleBlockChange(packet)
                                                is com.kostyamops.buildersplots.network.packets.PlotManagementPacket ->
                                                    plugin.networkManager.handlePlotManagement(packet)
                                                else ->
                                                    plugin.log("Неизвестный тип пакета: ${packet.javaClass.name}")
                                            }
                                        } catch (e: Exception) {
                                            plugin.log("Ошибка при обработке пакета: ${e.message}")
                                            e.printStackTrace()
                                        }
                                    })
                                } else {
                                    plugin.log("ОШИБКА: Полученный объект не является пакетом: ${packet?.javaClass?.name}")
                                }
                            } catch (e: ClassNotFoundException) {
                                plugin.log("ОШИБКА: Класс пакета не найден: ${e.message}")
                            } catch (e: Exception) {
                                plugin.log("ОШИБКА при чтении пакета: ${e.message}")
                                e.printStackTrace()
                            }
                        }
                    } catch (e: Exception) {
                        plugin.log("ОШИБКА при обработке входящего пакета: ${e.message}")
                        e.printStackTrace()
                    } finally {
                        try {
                            socket.close()
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                } catch (e: Exception) {
                    if (running) {
                        plugin.log("ОШИБКА в потоке приемника: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            if (running) {
                plugin.log("Критическая ошибка в потоке приемника: ${e.message}")
                e.printStackTrace()

                // Пытаемся перезапустить приемник
                plugin.log("Попытка перезапустить приемник через 5 секунд...")
                try {
                    Thread.sleep(5000)
                    if (running) {
                        plugin.log("Перезапуск приемника...")
                        runReceiver()
                    }
                } catch (ie: InterruptedException) {
                    // Ignore
                }
            }
        }
    }

    private fun runSender() {
        plugin.log("Поток отправки запущен. Порт отправки: $sendPort")

        while (running) {
            if (!packetQueue.isEmpty()) {
                val packet = packetQueue.poll()
                plugin.log("Отправка пакета типа: ${packet.javaClass.simpleName}")

                try {
                    Socket("127.0.0.1", sendPort).use { socket ->
                        plugin.log("Соединение установлено для отправки на порт $sendPort")

                        ObjectOutputStream(socket.getOutputStream()).use { output ->
                            plugin.log("Сериализация пакета...")
                            output.writeObject(packet)
                            output.flush()
                            plugin.log("Пакет успешно отправлен!")
                        }
                    }
                } catch (e: Exception) {
                    plugin.log("ОШИБКА отправки пакета: ${e.message}")
                    e.printStackTrace()

                    // Возвращаем пакет в очередь для повторной отправки
                    packetQueue.add(packet)
                    plugin.log("Пакет возвращен в очередь для повторной попытки. Новый размер очереди: ${packetQueue.size}")

                    // Ждем 5 секунд перед повторной попыткой
                    try {
                        Thread.sleep(5000)
                    } catch (ie: InterruptedException) {
                        // Ignore
                    }
                }
            } else {
                // Спим немного для экономии CPU
                try {
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                    // Ignore
                }
            }
        }
    }
}