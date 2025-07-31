package com.kostyamops.buildersplots.network.packets

import java.io.Serializable

data class BlockChangePacket(
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val material: String,
    val blockData: String
) : Packet, Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}