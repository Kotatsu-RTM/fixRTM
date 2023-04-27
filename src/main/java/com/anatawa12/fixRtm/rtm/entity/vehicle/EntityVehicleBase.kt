/// Copyright (c) 2020 anatawa12 and other contributors
/// This file is part of fixRTM, released under GNU LGPL v3 with few exceptions
/// See LICENSE at https://github.com/fixrtm/fixRTM for more details

package com.anatawa12.fixRtm.rtm.entity.vehicle

import com.anatawa12.fixRtm.addEntityCrashInfoAboutModelSet
import com.anatawa12.fixRtm.network.NetworkHandler
import com.anatawa12.fixRtm.network.NotifyUntracked
import jp.ngt.rtm.entity.train.EntityTrainBase
import jp.ngt.rtm.entity.train.parts.EntityFloor
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase
import net.minecraft.crash.CrashReportCategory
import net.minecraft.util.math.ChunkPos
import java.nio.ByteBuffer
import java.util.UUID

@Suppress("EXTENSION_SHADOWED_BY_MEMBER", "unused")
fun EntityVehicleBase<*>.onRemovedFromWorld() {
    if (world.isRemote)
        NetworkHandler.sendPacketServer(NotifyUntracked(entityId))
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER", "unused")
fun EntityVehicleBase<*>.addEntityCrashInfo(category: CrashReportCategory) =
    addEntityCrashInfoAboutModelSet(category) { resourceState?.resourceSet?.config }

fun getChunkPosArray(entity: EntityVehicleBase<*>) =
    mutableSetOf<ChunkPos>().apply {
        addAll(entity.floors.map { ChunkPos(it.position.x shr 4, it.position.z shr 4) })
        if (entity !is EntityTrainBase) return@apply
        addAll(entity.bogieController.bogies.filterNotNull().map { ChunkPos(it.position.x shr 4, it.position.z shr 4) })
    }.flatMap { listOf(it.x, it.z) }.toIntArray()

fun intArrayToChunks(array: IntArray) =
    mutableSetOf<ChunkPos>().apply {
        for (i in array.indices step 2) {
            add(ChunkPos(array[i], array[i + 1]))
        }
    }

fun floorsUuidToByteArray(vehicleFloors: List<EntityFloor>) =
    vehicleFloors.map(EntityFloor::getUniqueID).flatMap {
        ByteBuffer.allocate(16).apply {
            putLong(it.mostSignificantBits)
            putLong(it.leastSignificantBits)
        }.array().toList()
    }.toByteArray()

fun byteArrayToUuidArray(byteArray: ByteArray): Array<UUID> {
    if (byteArray.size % 16 != 0) throw IllegalArgumentException("ByteArray size must be multiple of 16")

    val uuids = mutableListOf<UUID>()
    val buffer = ByteBuffer.wrap(byteArray)

    for (i in byteArray.indices step 16) {
        uuids.add(UUID(buffer.getLong(i), buffer.getLong(i + 8)))
    }

    return uuids.toTypedArray()
}
