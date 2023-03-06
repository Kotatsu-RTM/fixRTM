package com.anatawa12.fixRtm.network

import io.netty.buffer.ByteBuf
import jp.ngt.ngtlib.network.PacketNBT
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

class PacketEntityLoaded(private var id: Int = 0): IMessage {
    override fun fromBytes(buffer: ByteBuf) {
        id = buffer.readInt()
    }

    override fun toBytes(buffer: ByteBuf) {
        buffer.writeInt(id)
    }

    companion object: IMessageHandler<PacketEntityLoaded, Nothing?> {
        override fun onMessage(packet: PacketEntityLoaded, context: MessageContext): Nothing? {
            if (context.side.isServer)
                context.serverHandler.player.serverWorld.getEntityByID(packet.id)
                    ?.let { PacketNBT.sendToClient(it, context.serverHandler.player) }
            return null
        }
    }
}
