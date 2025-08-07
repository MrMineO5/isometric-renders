package com.glisco.isometricrenders.mixin.access;

import net.minecraft.client.render.item.ItemRenderState.LayerRenderState;
import net.minecraft.client.render.model.json.Transformation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LayerRenderState.class)
public interface LayerRenderStateAccessor {
    @Accessor("transform")
    Transformation isometric$getTransform();
}