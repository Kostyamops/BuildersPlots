package com.kostyamops.buildersplots.network.packets

data class PlotManagementPacket(
    val action: String, // CREATE, DELETE, ADD_MEMBER, REMOVE_MEMBER, DISABLE_SYNC
    val data: Map<String, Any>
) : Packet