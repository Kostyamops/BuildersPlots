package com.kostyamops.buildersplots.network.packets

import java.io.Serializable
import java.util.UUID

data class PlotManagementPacket(
    val action: String, // CREATE, DELETE, ADD_MEMBER, REMOVE_MEMBER, DISABLE_SYNC
    val data: Map<String, Any>,
    val plotId: UUID? = null,  // Для совместимости со старым кодом
    val playerUUID: UUID? = null,  // Для совместимости со старым кодом
    val extraData: Any? = null  // Для совместимости со старым кодом
) : Packet, Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }

    // Валидация сериализуемости элементов Map
    init {
        for ((key, value) in data) {
            if (value != null && value !is Serializable) {
                throw IllegalArgumentException("Значение для ключа '$key' не сериализуемо: ${value.javaClass.name}")
            }
        }
    }
}