package com.glisco.isometricrenders.render;

import com.glisco.isometricrenders.IsometricRenders;
import com.glisco.isometricrenders.mixin.access.FramebufferAccessor;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.ProjectionMatrix3;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;

public class RenderableDispatcher {
    static final GpuBuffer buffer;
    static final GpuBufferSlice slice;

    static {
        GpuDevice gpuDevice = RenderSystem.getDevice();
        buffer = gpuDevice.createBuffer(() -> "Projection matrix UBO isometric", 136, RenderSystem.PROJECTION_MATRIX_UBO_SIZE);
        slice = buffer.slice(0, RenderSystem.PROJECTION_MATRIX_UBO_SIZE);
    }

    /**
     * Renders the given renderable into the current framebuffer,
     * with the projection matrix adjusted to compensate for the buffer's
     * aspect ratio
     *
     * @param renderable  The renderable to draw
     * @param aspectRatio The aspect ratio of the current framebuffer
     * @param tickDelta   The tick delta to use
     */
    public static void drawIntoActiveFramebuffer(Renderable<?> renderable, float aspectRatio, float tickDelta, Consumer<Matrix4fStack> transformer) {

        renderable.prepare();

        // Prepare model view matrix
        final var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();

        transformer.accept(modelViewStack);

        renderable.properties().applyToViewMatrix(modelViewStack);

        RenderSystem.backupProjectionMatrix();
        Matrix4f projectionMatrix = new Matrix4f().setOrtho(-aspectRatio, aspectRatio, -1, 1, -1000, 3000);

        // Unproject to get the camera position for vertex sorting
        var camPos = new Vector4f(0, 0, 0, 1);
        camPos.mul(new Matrix4f(projectionMatrix).invert()).mul(new Matrix4f(modelViewStack).invert());

        try (MemoryStack memoryStack = MemoryStack.stackPush()) {
            ByteBuffer byteBuffer = Std140Builder.onStack(memoryStack, RenderSystem.PROJECTION_MATRIX_UBO_SIZE).putMat4f(projectionMatrix).get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), byteBuffer);
        }

        RenderSystem.setProjectionMatrix(slice, ProjectionType.ORTHOGRAPHIC);

        IsometricRenders.beginRenderableDraw();

        renderable.setupLighting(modelViewStack);

        // TODO replacement?
//        RenderSystem.runAsFancy(() -> {
            // Emit untransformed vertices
            renderable.emitVertices(
                    new MatrixStack(),
                    MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers(),
                    tickDelta
            );

            // --> Draw
            renderable.draw(modelViewStack);
//        });

        IsometricRenders.endRenderableDraw();

        modelViewStack.popMatrix();

        renderable.cleanUp();
        RenderSystem.restoreProjectionMatrix();
    }

    /**
     * Directly draws the given renderable into a {@link NativeImage} at the given resolution.
     * This method is essentially just a shorthand for {@code copyFramebufferIntoImage(drawIntoTexture(renderable, size))}
     *
     * @param renderable The renderable to draw
     * @param size       The resolution to render at
     * @return The created image
     */
    public static NativeImage drawIntoImage(Renderable<?> renderable, float tickDelta, int size) {
        return copyFramebufferIntoImage(drawIntoTexture(renderable, tickDelta, size));
    }

    /**
     * Draws the given renderable into a new framebuffer. The FBO and depth attachment
     * are deleted afterwards to save video memory, only the color attachment remains
     *
     * @param renderable The renderable to render
     * @param size       The resolution to render aat
     * @return The framebuffer object holding the pointer to the color attachment
     */
    @SuppressWarnings("ConstantConditions")
    public static Framebuffer drawIntoTexture(Renderable<?> renderable, float tickDelta, int size) {
        final var framebuffer = new SimpleFramebuffer("isometric_render", size, size, true);

        GlStateManager._enableBlend();
        GlStateManager._clear(16640);

        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(framebuffer.getColorAttachment(), 0, framebuffer.getDepthAttachment(), 1.0);

        RenderSystem.outputColorTextureOverride = framebuffer.getColorAttachmentView();
        RenderSystem.outputDepthTextureOverride = framebuffer.getDepthAttachmentView();

        IsometricRenders.mainTargetOverride = framebuffer;

        drawIntoActiveFramebuffer(renderable, 1, tickDelta, matrixStack -> {});

        RenderSystem.outputColorTextureOverride = null;
        RenderSystem.outputDepthTextureOverride = null;

        IsometricRenders.mainTargetOverride = null;

        // Release depth attachment and FBO to save on VRAM - we only need
        // the color attachment texture to later turn into an image
        framebuffer.getDepthAttachment().close();

        return framebuffer;
    }

    /**
     * Copies the given framebuffer's color attachment from video
     * memory in to system memory, wrapped in a {@link NativeImage}
     *
     * @param framebuffer The framebuffer to copy
     * @return The created image
     */
    public static NativeImage copyFramebufferIntoImage(Framebuffer framebuffer) {
        final NativeImage img = new NativeImage(framebuffer.textureWidth, framebuffer.textureHeight, false);

        // This method gets the pixels from the currently bound texture
        GlStateManager._bindTexture(((GlTexture) framebuffer.getColorAttachment()).getGlId());
        GlStateManager._pixelStore(GlConst.GL_PACK_ALIGNMENT, img.getFormat().getChannelCount());
        GL11.glGetTexImage(GlConst.GL_TEXTURE_2D, 0, GlConst.GL_RGBA, GlConst.GL_UNSIGNED_BYTE, img.imageId());
        mirrorVertically(img);

        framebuffer.delete();

        return img;
    }

    private static void mirrorVertically(NativeImage img) {
        int i = img.getFormat().getChannelCount();
        int j = img.getWidth() * i;
        long l = MemoryUtil.nmemAlloc(j);

        try {
            for (int k = 0; k < img.getHeight() / 2; k++) {
                int m = k * img.getWidth() * i;
                int n = (img.getHeight() - 1 - k) * img.getWidth() * i;
                MemoryUtil.memCopy(img.imageId() + (long)m, l, (long)j);
                MemoryUtil.memCopy(img.imageId() + (long)n, img.imageId() + (long)m, (long)j);
                MemoryUtil.memCopy(l, img.imageId() + (long)n, (long)j);
            }
        } finally {
            MemoryUtil.nmemFree(l);
        }
    }
}
