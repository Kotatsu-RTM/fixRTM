package com.anatawa12.fixRtm.rtm.world

import jp.ngt.rtm.RTMCore
import jp.ngt.rtm.entity.train.EntityTrainBase
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.World
import net.minecraft.world.WorldServer
import net.minecraftforge.common.ForgeChunkManager
import net.minecraftforge.common.ForgeChunkManager.LoadingCallback
import net.minecraftforge.common.ForgeChunkManager.Ticket
import org.apache.logging.log4j.LogManager

object NewChunkManager: LoadingCallback {
    private val tickets = mutableMapOf<EntityVehicleBase<*>, Ticket>()
    private val loadingChunksPair = mutableMapOf<EntityVehicleBase<*>, MutableSet<ChunkPos>>()

    fun loadChunks(vehicle: EntityVehicleBase<*>, chunks: Set<ChunkPos>) {
        val ticket = getTicket(vehicle)

        chunks.forEach { ForgeChunkManager.forceChunk(ticket, it) }
        getLoadingChunks(vehicle).addAll(chunks)
    }

    fun updateChunks(vehicle: EntityVehicleBase<*>) {
        val world = checkSide(vehicle.world)
        val ticket = getTicket(vehicle)
        val loadingChunks = getLoadingChunks(vehicle)
        val currentChunks = mutableSetOf<ChunkPos>().apply {
            add(world.getChunk(vehicle.position).pos)
            addAll(vehicle.floors.map { world.getChunk(it.position).pos })
            if (vehicle is EntityTrainBase)
                addAll(vehicle.bogieController.bogies.map { world.getChunk(it.position).pos })
        }

        //Old chunks
        with(loadingChunks.filterNot { currentChunks.contains(it) }) {
            forEach { ForgeChunkManager.unforceChunk(ticket, it) }
            loadingChunks.removeAll(this.toSet())

            LogManager.getLogger().info("Old chunks: $this")
        }
        //New chunks
        with(currentChunks.filterNot { loadingChunks.contains(it) }) {
            forEach { ForgeChunkManager.forceChunk(ticket, it) }
            loadingChunks.addAll(this.toSet())

            LogManager.getLogger().info("New chunks: $this")
        }
    }

    fun removeEntity(vehicle: EntityVehicleBase<*>) {
        val ticket = tickets[vehicle] ?: return

        loadingChunksPair[vehicle]?.forEach { ForgeChunkManager.unforceChunk(ticket, it) }
        loadingChunksPair.remove(vehicle)

        ForgeChunkManager.releaseTicket(ticket)
        tickets.remove(vehicle)
    }

    override fun ticketsLoaded(tickets: MutableList<Ticket>, world: World) {
        tickets.forEach {
            LogManager.getLogger().info(it.entity)
        }
    }

    private fun getTicket(vehicle: EntityVehicleBase<*>) =
        if (tickets.containsKey(vehicle)) tickets[vehicle]!! else {
            ForgeChunkManager.requestTicket(RTMCore.instance, vehicle.world, ForgeChunkManager.Type.ENTITY)
                ?.apply { bindEntity(vehicle) }
                ?.also { tickets[vehicle] = it }
                ?: throw IllegalStateException("Can't get a ticket. You may need to change forgeChunkLoading.cfg limitations.")
        }

    private fun getLoadingChunks(vehicle: EntityVehicleBase<*>) =
        if (loadingChunksPair.containsKey(vehicle)) {
            loadingChunksPair[vehicle]!!
        } else {
            mutableSetOf<ChunkPos>().also { loadingChunksPair[vehicle] = it }
        }

    private fun checkSide(world: World): WorldServer {
        if (world is WorldServer) return world
        throw IllegalAccessException("NewChunkManager methods must be called from server side")
    }
}
