package com.glisco.isometricrenders.mixin.access;

import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.ItemRenderState.LayerRenderState;
import net.minecraft.item.ItemDisplayContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemRenderState.class)
public interface ItemRenderStateAccessor {

    @Accessor("layers")
    LayerRenderState[] isometric$getLayers();

    @Accessor("displayContext")
    void isometric$setDisplayContext(ItemDisplayContext context);

    // ModelTransformationMode was removed in Minecraft 1.21.8
    // @Accessor("modelTransformationMode")
    // void isometric$setTransformationMode(ModelTransformationMode mode);
}
