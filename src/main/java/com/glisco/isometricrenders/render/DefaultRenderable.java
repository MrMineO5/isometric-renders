package com.glisco.isometricrenders.render;

import com.glisco.isometricrenders.mixin.access.CameraInvoker;
import com.glisco.isometricrenders.property.DefaultPropertyBundle;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public abstract class DefaultRenderable<P extends DefaultPropertyBundle> implements Renderable<P> {
    final GpuBuffer buffer;
    final int uboSize = new Std140SizeCalculator().putVec3().putVec3().get();

    {
        var gpuDevice = RenderSystem.getDevice();
        var roundedUboSize = MathHelper.roundUpToMultiple(uboSize, gpuDevice.getUniformOffsetAlignment());
        this.buffer = gpuDevice.createBuffer(() -> "Light Direction Buffer", 136, roundedUboSize);
    }

    @Override
    public void setupLighting(Matrix4f modelViewMatrix) {
        // Apply inverse transform to lighting to keep it consistent
        final var lightDirection = getLightDirection();
        final var lightTransform = new Matrix4f(modelViewMatrix);
        lightTransform.invert();
        lightDirection.mul(lightTransform);

        final var transformedLightDirection = new Vector3f(lightDirection.x, lightDirection.y, lightDirection.z);

        try (MemoryStack memoryStack = MemoryStack.stackPush()) {
            ByteBuffer byteBuffer = Std140Builder.onStack(memoryStack, uboSize)
                    .putVec3(transformedLightDirection)
                    .putVec3(transformedLightDirection)
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), byteBuffer);
        }

        RenderSystem.setShaderLights(buffer.slice());
    }

    @Override
    public void draw(Matrix4f modelViewMatrix) {
        // Draw all buffers
        MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers().draw();
    }

    protected void renderParticles(Matrix4f transform, float tickDelta) {
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(transform);

        var client = MinecraftClient.getInstance();
        this.withParticleCamera(camera -> {
            client.particleManager.renderParticles(
                    camera,
                    tickDelta,
                    MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers()
            );
        });

        modelView.popMatrix();
    }

    protected void withParticleCamera(Consumer<Camera> action) {
        Camera camera = MinecraftClient.getInstance().getEntityRenderDispatcher().camera;
        float previousYaw = camera.getYaw(), previousPitch = camera.getPitch();

        ((CameraInvoker) camera).isometric$setRotation(this.properties().rotation.get() + 180 + this.properties().rotationOffset(), this.properties().slant.get());
        action.accept(camera);

        ((CameraInvoker) camera).isometric$setRotation(previousYaw, previousPitch);
    }

    protected Vector4f getLightDirection() {
        return new Vector4f(this.properties().lightAngle.get() / 90f, .35f, 1, 0);
    }
}
