package com.kostyamops.buildersplots.network.model

/**
 * Базовая модель сетевого сообщения
 */
data class Message(
    val type: String,
    val data: Any
)

/**
 * Данные об изменении блока
 */
data class BlockChangeData(
    val type: String, // "PLACE" or "BREAK"
    val material: String,
    val blockData: String,
    val plotName: String,
    val x: Int,
    val y: Int,
    val z: Int
)

/**
 * Данные о блоке плота
 */
data class PlotBlockData(
    val plotName: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val material: String,
    val blockData: String
)

/**
 * Прогресс сканирования плота
 */
data class PlotScanProgress(
    val plotName: String,
    val scannedChunks: Int,
    val totalChunks: Int
) {
    fun getPercentComplete(): Int {
        return if (totalChunks > 0) {
            (scannedChunks * 100) / totalChunks
        } else {
            0
        }
    }
}

/**
 * Данные для ping-сообщения
 */
data class PingData(
    val timestamp: Long = System.currentTimeMillis(),
    val serverType: String
)

/**
 * Константы типов сообщений
 */
object MessageType {
    const val AUTH = "AUTH"
    const val AUTH_SUCCESS = "AUTH_SUCCESS"
    const val ERROR = "ERROR"

    const val PLOT_CREATE = "PLOT_CREATE"
    const val PLOT_DELETE = "PLOT_DELETE"
    const val REQUEST_PLOT_BLOCKS = "REQUEST_PLOT_BLOCKS"
    const val PLOT_BLOCKS = "PLOT_BLOCKS"
    const val PLOT_SCAN_PROGRESS = "PLOT_SCAN_PROGRESS"
    const val PLOT_SCAN_COMPLETE = "PLOT_SCAN_COMPLETE"

    const val BLOCK_CHANGE = "BLOCK_CHANGE"

    // Новые типы сообщений для пинга
    const val PING = "PING"
    const val PING_RESPONSE = "PING_RESPONSE"
}