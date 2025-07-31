package com.kostyamops.buildersplots.network.packets

import java.io.Serializable
import java.util.UUID

// Новый тип пакета для синхронизации целой области
data class AreaSyncPacket(
    val plotId: UUID,
    val blockData: List<BlockInfo>
) : Packet, Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }

    // Вспомогательный класс для хранения информации о блоке
    data class BlockInfo(
        val x: Int,
        val y: Int,
        val z: Int,
        val material: String,
        val blockData: String
    ) : Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }
}