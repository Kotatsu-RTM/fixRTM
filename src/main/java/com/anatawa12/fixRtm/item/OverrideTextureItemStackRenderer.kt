package com.anatawa12.fixRtm.item

import com.anatawa12.fixRtm.asm.config.MainConfig
import jp.ngt.rtm.RTMItem
import jp.ngt.rtm.item.ItemInstalledObject
import jp.ngt.rtm.item.ItemWithModel
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GLAllocation
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL13

object OverrideTextureItemStackRenderer : TileEntityItemStackRenderer() {
    private val COLOR = floatArrayOf(0.8f, 0.8f, 0.8f)
    private const val FRONT_Z = 0.5f + 1.0f / 32.0f
    private const val BACK_Z = 0.5f - 1.0f / 32.0f
    private val VERTICES = floatArrayOf(
        0.0f, 1.0f, 0.0f, 0.0f, FRONT_Z,
        1.0f, 1.0f, 1.0f, 0.0f, FRONT_Z,
        0.0f, 0.0f, 0.0f, 1.0f, FRONT_Z,
        1.0f, 0.0f, 1.0f, 1.0f, FRONT_Z,
        1.0f, 0.0f, 1.0f, 1.0f, BACK_Z,
        1.0f, 1.0f, 1.0f, 0.0f, BACK_Z,
        0.0f, 1.0f, 0.0f, 0.0f, BACK_Z,
        0.0f, 0.0f, 0.0f, 1.0f, BACK_Z,
    )
    private val INDICES = intArrayOf(0, 1, 2, 3, 4, 1, 5, 0, 6, 2, 7, 4, 6, 5)

    private val DISPLAY_LIST by lazy {
        val id = GLAllocation.generateDisplayLists(1)

        GL11.glNewList(id, GL11.GL_COMPILE)

        GlStateManager.pushAttrib()
        RenderHelper.disableStandardItemLighting()
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_DECAL)

        GL11.glBegin(GL11.GL_TRIANGLE_STRIP)
        for (i in INDICES) {
            var j = 5 * i
            GL11.glColor3f(COLOR[0], COLOR[1], COLOR[2])
            GL11.glTexCoord2f(VERTICES[j++], VERTICES[j++])
            GL11.glVertex3f(VERTICES[j++], VERTICES[j++], VERTICES[j++])
        }
        GL11.glEnd()

        GlStateManager.popAttrib()
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT)
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE)

        GL11.glEndList()

        id
    }

    override fun renderByItem(itemStack: ItemStack, partialTick: Float) {
        val renderItem = Minecraft.getMinecraft().renderItem

        val model = renderItem.getItemModelWithOverrides(itemStack, null, null)
        if (model !is OverrideTextureModel) {
            renderItem.renderItem(itemStack, model)
            return
        }

        val customTexture = getCustomIcon(itemStack)
        val isRRS = itemStack.item == RTMItem.installedObject
                && itemStack.itemDamage == ItemInstalledObject.IstlObjType.RAILLOAD_SIGN.id.toInt()
        if (customTexture == null || (isRRS && !MainConfig.rrsImageAsIcon)) {
            GL11.glTranslatef(0.5f, 0.5f, 0.5f)
            renderItem.renderItem(itemStack, model.baseModel)
            return
        }

        Minecraft.getMinecraft().textureManager.bindTexture(customTexture)
        GL11.glCallList(DISPLAY_LIST)
    }

    private fun getCustomIcon(itemStack: ItemStack): ResourceLocation? {
        val item = itemStack.item
        if (item !is ItemWithModel<*>) {
            return null
        }
        val resourceState = item.getModelState(itemStack) ?: return null
        return resourceState.resourceSet.config.customIcon
    }
}
