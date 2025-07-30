package com.kostyamops.buildersplots.network.packets

data class BlockChangePacket(
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val material: String,
    val blockData: String
) : Packet