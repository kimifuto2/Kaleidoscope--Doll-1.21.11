package com.github.ysbbbbbb.kaleidoscopedoll.render;

import com.github.ysbbbbbb.kaleidoscopedoll.KaleidoscopeDoll;
import com.github.ysbbbbbb.kaleidoscopedoll.item.DollEntityItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;
import java.util.function.Consumer;

/**
 * 实体玩偶物品（{@code doll_entity_item}）的 special model 渲染器。
 * <p>
 * 为什么需要它：这些玩偶物品原本用 {@code DataComponents.ITEM_MODEL = kaleidoscope_doll:doll_N}
 * 套用<b>方块模型</b>。方块模型走方块图集与 CUTOUT 层，在 Iris 光影下的世界物品渲染路径（GROUND，含基座/掉落物）
 * 会被处理成透明（同一物品在 GUI 里正常、放成方块也正常）。这里改成自己画：
 * <ul>
 *   <li>贴图用<b>每只玩偶自己的独立 PNG</b>（{@code kaleidoscope_doll:textures/block/doll/doll_N.png}），</li>
 *   <li>RenderType 用 {@link RenderTypes#entityCutoutNoCull(Identifier)}（与 GeckoLib 玩家玩偶同一条管线），</li>
 *   <li>几何直接取方块模型的 {@link BakedQuad}，把顶点 UV 从方块图集空间归一化到 sprite 本地 0..1。</li>
 * </ul>
 * 方块状态从物品的 {@code doll_entity} 组件的 {@code doll_block_state} 里读。
 */
public class DollBlockSpecialRenderer implements SpecialModelRenderer<BlockState> {
    /**
     * 物品模型的 display 变换用固定种子取样即可（玩偶模型没有可变部分）。
     */
    private static final long MODEL_SEED = 42L;

    @Override
    public void submit(BlockState state, ItemDisplayContext displayContext, PoseStack poseStack,
                       SubmitNodeCollector collector, int light, int overlay, boolean foil, int outlineColor) {
        if (state == null || state.isAir()) {
            return;
        }
        BlockStateModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        if (model == null) {
            return;
        }
        List<BlockModelPart> parts = model.collectParts(RandomSource.create(MODEL_SEED));
        if (parts.isEmpty()) {
            return;
        }

        RenderType renderType = RenderTypes.entityCutoutNoCull(textureOf(state));

        poseStack.pushPose();
        collector.submitCustomGeometry(poseStack, renderType,
                (pose, buffer) -> emitParts(buffer, pose, parts, light, overlay));
        poseStack.popPose();
    }

    private static void emitParts(VertexConsumer buffer, PoseStack.Pose pose, List<BlockModelPart> parts,
                                  int light, int overlay) {
        for (BlockModelPart part : parts) {
            for (Direction direction : Direction.values()) {
                emitQuads(buffer, pose, part.getQuads(direction), light, overlay);
            }
            // 无朝向（不剔除）的面
            emitQuads(buffer, pose, part.getQuads(null), light, overlay);
        }
    }

    private static void emitQuads(VertexConsumer buffer, PoseStack.Pose pose, @Nullable List<BakedQuad> quads,
                                  int light, int overlay) {
        if (quads == null || quads.isEmpty()) {
            return;
        }
        for (BakedQuad quad : quads) {
            TextureAtlasSprite sprite = quad.sprite();
            float spriteU0 = sprite.getU0();
            float spriteV0 = sprite.getV0();
            float spanU = sprite.getU1() - spriteU0;
            float spanV = sprite.getV1() - spriteV0;

            Direction direction = quad.direction();
            float normalX = direction == null ? 0.0F : direction.getStepX();
            float normalY = direction == null ? 0.0F : direction.getStepY();
            float normalZ = direction == null ? 0.0F : direction.getStepZ();

            for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                Vector3fc position = quad.position(vertex);
                long packedUv = quad.packedUV(vertex);
                // 顶点 UV 是方块图集空间；独立贴图需要 sprite 本地 0..1
                float localU = spanU == 0.0F ? 0.0F : (UVPair.unpackU(packedUv) - spriteU0) / spanU;
                float localV = spanV == 0.0F ? 0.0F : (UVPair.unpackV(packedUv) - spriteV0) / spanV;

                buffer.addVertex(pose, position.x(), position.y(), position.z())
                        .setColor(255, 255, 255, 255)
                        .setUv(localU, localV)
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(pose, normalX, normalY, normalZ);
            }
        }
    }

    @Override
    public void getExtents(Consumer<Vector3fc> consumer) {
        consumer.accept(new Vector3f(0.0F, 0.0F, 0.0F));
        consumer.accept(new Vector3f(1.0F, 1.0F, 1.0F));
    }

    @Override
    public BlockState extractArgument(ItemStack stack) {
        return DollEntityItem.getBlockStateFromStack(stack);
    }

    /**
     * 方块 id → 该玩偶的独立贴图（完整路径，entityCutoutNoCull 要的就是完整路径）。
     */
    private static Identifier textureOf(BlockState state) {
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String namespace = blockId == null ? KaleidoscopeDoll.MOD_ID : blockId.getNamespace();
        String path = blockId == null ? "doll_1" : blockId.getPath();
        return Identifier.fromNamespaceAndPath(namespace, "textures/block/doll/" + path + ".png");
    }

    /**
     * special model 的「未烘焙」部分：无字段，JSON 里只写 {@code {"type":"kaleidoscope_doll:doll_block"}}。
     */
    public static final class Unbaked implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext context) {
            return new DollBlockSpecialRenderer();
        }

        @Override
        public MapCodec<? extends SpecialModelRenderer.Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
