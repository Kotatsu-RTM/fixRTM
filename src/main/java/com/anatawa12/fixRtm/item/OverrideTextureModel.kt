package com.anatawa12.fixRtm.item

import net.minecraft.block.state.IBlockState
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.IBakedModel
import net.minecraft.client.renderer.block.model.ItemCameraTransforms
import net.minecraft.client.renderer.block.model.ItemOverrideList
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.util.EnumFacing
import org.apache.commons.lang3.tuple.Pair
import javax.vecmath.Matrix4f

class OverrideTextureModel(val baseModel: IBakedModel) : IBakedModel {
    override fun getQuads(state: IBlockState?, side: EnumFacing?, rand: Long): List<BakedQuad> =
        baseModel.getQuads(state, side, rand)

    override fun isAmbientOcclusion(): Boolean = baseModel.isAmbientOcclusion

    override fun isGui3d(): Boolean = baseModel.isGui3d

    override fun isBuiltInRenderer(): Boolean = true

    override fun getParticleTexture(): TextureAtlasSprite = baseModel.particleTexture

    override fun getOverrides(): ItemOverrideList = baseModel.overrides

    override fun handlePerspective(transformType: ItemCameraTransforms.TransformType): Pair<out IBakedModel?, Matrix4f?> {
        val matrix = baseModel.handlePerspective(transformType).right
        return Pair.of(this, matrix)
    }
}
