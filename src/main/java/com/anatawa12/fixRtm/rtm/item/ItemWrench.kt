package com.anatawa12.fixRtm.rtm.item

import com.anatawa12.fixRtm.Loggers
import com.anatawa12.fixRtm.rtm.rail.BlockMarker
import jp.ngt.ngtlib.item.ItemArgHolderBase
import jp.ngt.ngtlib.item.ItemCustom
import jp.ngt.rtm.RTMBlock
import jp.ngt.rtm.rail.TileEntityLargeRailBase
import jp.ngt.rtm.rail.TileEntityMarker
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

object ItemWrench : ItemCustom() {
    private val LOGGER = Loggers.getLogger("ItemWrench")

    override fun onItemUse(
        holder: ItemArgHolderBase.ItemArgHolder,
        hitX: Float,
        hitY: Float,
        hitZ: Float
    ): ActionResult<ItemStack> {
        if (holder.world.isRemote) {
            return holder.pass()
        }

        convertRailToMarker(holder.world, holder.blockPos, holder.player)
        return holder.success()
    }

    @SideOnly(Side.SERVER)
    private fun convertRailToMarker(world: World, pos: BlockPos, player: EntityPlayer) {
        val tileEntity = world.getTileEntity(pos)
        if (tileEntity !is TileEntityLargeRailBase) return

        val railCore = tileEntity.railCore ?: run {
            LOGGER.warn("getRailCore() returns null, potentially a bug.")
            return
        }

        world.setBlockToAir(railCore.pos)

        for (railPosition in railCore.railPositions) {
            if (railPosition == null) {
                LOGGER.warn("railPosition is null, potentially a bug.")
                continue
            }

            val markerPos = BlockPos(railPosition.blockX, railPosition.blockY, railPosition.blockZ)
            val block = if (railPosition.switchType.toInt() == 0) RTMBlock.marker else RTMBlock.markerSwitch
            // RailPosition.direction rotates in the opposite direction of Entity.rotationYaw.
            val meta = BlockMarker.getFaceMeta((8 - railPosition.direction and 7) * 45.0f)
            val state = block.getStateFromMeta(meta)

            world.setBlockState(markerPos, state)

            val marker = world.getTileEntity(markerPos)
            if (marker !is TileEntityMarker) {
                LOGGER.error("Placed block didn't have TileEntityMarker. This is a bug!")
                world.setBlockToAir(markerPos)
                continue
            }

            marker.markerRP = railPosition
            marker.setOwner(player.uniqueID)
        }
    }
}
