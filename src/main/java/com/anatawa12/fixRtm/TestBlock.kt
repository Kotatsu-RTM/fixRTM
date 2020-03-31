package com.anatawa12.fixRtm

import com.anatawa12.fixRtm.dummies.DummyModelObject
import com.anatawa12.fixRtm.dummies.DummyModelObject.Companion.resourceLocation
import jp.ngt.ngtlib.block.BlockContainerCustom
import jp.ngt.ngtlib.renderer.TesselatorVertexState
import net.minecraft.block.Block
import net.minecraft.block.BlockContainer
import net.minecraft.block.material.Material
import net.minecraft.block.state.IBlockState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.block.model.ItemCameraTransforms
import net.minecraft.client.renderer.tileentity.TileEntityBannerRenderer
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.item.ItemBlock
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.world.World
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import java.nio.ByteBuffer
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.crash.CrashReport
import net.minecraft.crash.CrashReportCategory
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumBlockRenderType
import net.minecraft.util.ReportedException
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.IBlockAccess
import net.minecraft.world.WorldType
import net.minecraftforge.client.ForgeHooksClient


object TestBlock : BlockContainerCustom(Material.WOOD) {
    init {
        setRegistryName("test")
		//setLightLevel(1.0f)
    }

    override fun createNewTileEntity(worldIn: World, meta: Int): TileEntity? = TestTileEntity()
}

object TestItem : ItemBlock(TestBlock){
    init {
        setRegistryName("test")
    }
}

class TestTileEntity : TileEntity()


fun FontRenderer.drawCenterString(value: String, x: Int, y: Int, color: Int = 0, dropShadow: Boolean = false) {
    val with = getStringWidth(value)
    drawString(value, x - with / 2.0f, y - 4f, color, dropShadow)
}
object TestSPRenderer: TileEntitySpecialRenderer<TestTileEntity>() {
    val dummyModelObject = DummyModelObject(AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0),
            "test block",
            EnumFacing.VALUES.toSet())
    override fun render(te: TestTileEntity, x: Double, y: Double, z: Double, partialTicks: Float, destroyStage: Int, alpha: Float) {
        GL11.glPushMatrix()
        GL11.glTranslated(x, y, z)

        dummyModelObject.renderAll(false)

        GL11.glPopMatrix()
    }
}