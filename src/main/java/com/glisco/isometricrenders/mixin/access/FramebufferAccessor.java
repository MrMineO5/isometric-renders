package com.glisco.isometricrenders.mixin.access;

import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Framebuffer.class)
public interface FramebufferAccessor {

     @Accessor("depthAttachment")
     void isometric$setDepthAttachment(GpuTexture depthAttachment);

     @Accessor("colorAttachment")
    void isometric$setColorAttachment(GpuTexture colorAttachment);

//     @Accessor("fbo")
//     void isometric$setFbo(int fbo);
//
//     @Accessor("fbo")
//     int isometric$getFbo();

}
