package com.anatawa12.fixRtm.network

import io.netty.buffer.ByteBuf
import jp.ngt.rtm.modelpack.ModelPackManager
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext
import java.nio.ByteBuffer

class PacketAllModelSets() : IMessage {
    private lateinit var data: ByteArray

    constructor(data: ByteArray) : this() {
        this.data = data
    }

    override fun fromBytes(buf: ByteBuf) {
        data = ByteArray(buf.readableBytes())
        buf.readBytes(data)
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeBytes(data)
    }

    companion object : IMessageHandler<PacketAllModelSets, IMessage> {
        override fun onMessage(message: PacketAllModelSets, ctx: MessageContext): IMessage? {
            val buffer = ByteBuffer.wrap(message.data)
            var counter = 0

            while (buffer.hasRemaining()) {
                val typeNameLength = buffer.getInt()
                val typeNameBuffer = ByteArray(typeNameLength)
                buffer.get(typeNameBuffer)
                val typeName = typeNameBuffer.decodeToString()

                for (i in 0 until buffer.getInt()) {
                    val modelNameLength = buffer.getInt()
                    val modelNameBuffer = ByteArray(modelNameLength)
                    buffer.get(modelNameBuffer)
                    val modelName = modelNameBuffer.decodeToString()

                    ModelPackManager.INSTANCE.addModelSetName(counter++, typeName, modelName)
                }
            }

            return null
        }
    }
}
