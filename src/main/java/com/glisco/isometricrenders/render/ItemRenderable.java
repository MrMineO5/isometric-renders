package com.glisco.isometricrenders.render;

import com.glisco.isometricrenders.mixin.access.ItemRenderStateAccessor;
import com.glisco.isometricrenders.mixin.access.LayerRenderStateAccessor;
import com.glisco.isometricrenders.property.DefaultPropertyBundle;
import com.glisco.isometricrenders.util.ExportPathSpec;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

public class ItemRenderable extends DefaultRenderable<DefaultPropertyBundle> {

    private static final ItemRenderState RENDER_STATE = new ItemRenderState();
    private static final DefaultPropertyBundle PROPERTIES = new DefaultPropertyBundle() {
        @Override
        public void applyToViewMatrix(Matrix4fStack modelViewStack) {
            final float scale = (this.scale.get() / 100f) * 1.75f;
            modelViewStack.scale(scale, scale, scale);

            modelViewStack.translate(this.xOffset.get() / 26000f, this.yOffset.get() / -26000f, 0);

            modelViewStack.rotate(RotationAxis.POSITIVE_X.rotationDegrees(this.slant.get()));
            var bruhMatrices = new MatrixStack();
//            ((LayerRenderStateAccessor) ((ItemRenderStateAccessor) RENDER_STATE).isometric$getLayers()[0]).isometric$getTransform().apply(false, bruhMatrices.peek());
            modelViewStack.mul(bruhMatrices.peek().getPositionMatrix());
            modelViewStack.rotate(RotationAxis.POSITIVE_Y.rotationDegrees(this.rotation.get()));

            this.updateAndApplyRotationOffset(modelViewStack);
        }
    };

    static {
        PROPERTIES.slant.setDefaultValue(0).setToDefault();
        PROPERTIES.rotation.setDefaultValue(0).setToDefault();
    }

    private final ItemStack stack;

    public ItemRenderable(ItemStack stack) {
        this.stack = stack;
    }

    private static final Vector3f DEFAULT_DIFFUSION_LIGHT_0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
    private static final Vector3f DEFAULT_DIFFUSION_LIGHT_1 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();

    @Override
    public void setupLighting(Matrix4f modelViewMatrix) {
        Matrix4f matrix4f;
        if (RENDER_STATE.isSideLit()) {
            matrix4f = new Matrix4f()
                .scaling(1.0F, 1.0F, 1.0F)
                .rotateYXZ(1.0821041F, 3.2375858F, 0.0F)
                .rotateYXZ((float) (-Math.PI / 8), (float) (Math.PI * 3.0 / 4.0), 0.0F);
        } else {
            matrix4f = new Matrix4f().rotationY((float) (-Math.PI / 8)).rotateX((float) (Math.PI * 3.0 / 4.0));
        }

        try (MemoryStack memoryStack = MemoryStack.stackPush()) {
            ByteBuffer byteBuffer = Std140Builder.onStack(memoryStack, uboSize)
                    .putVec3(matrix4f.transformDirection(DEFAULT_DIFFUSION_LIGHT_0, new Vector3f()))
                    .putVec3(matrix4f.transformDirection(DEFAULT_DIFFUSION_LIGHT_1, new Vector3f()))
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), byteBuffer);
        }

        RenderSystem.setShaderLights(buffer.slice());
    }

    @Override
    public void prepare() {
        MinecraftClient.getInstance().getItemModelManager().update(
            RENDER_STATE,
            this.stack,
            ItemDisplayContext.GUI,
            MinecraftClient.getInstance().world,
            null,
            0
        );
    }

    @Override
    public void emitVertices(MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta) {
        ((ItemRenderStateAccessor) RENDER_STATE).isometric$setDisplayContext(ItemDisplayContext.NONE);
        RENDER_STATE.render(matrices, vertexConsumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
    }

    @Override
    public void cleanUp() {
        RENDER_STATE.clear();
    }

    @Override
    public DefaultPropertyBundle properties() {
        return PROPERTIES;
    }

    @Override
    public ExportPathSpec exportPath() {
        return ExportPathSpec.ofIdentified(
            Registries.ITEM.getId(this.stack.getItem()),
            "item"
        );
    }

//    private static class TransformlessBakedModel extends ForwardingBakedModel {
//        public TransformlessBakedModel(BakedModel inner) {
//            this.wrapped = inner;
//        }
//
//        @Override
//        public ModelTransformation getTransformation() {
//            return ModelTransformation.NONE;
//        }
//    }
}
