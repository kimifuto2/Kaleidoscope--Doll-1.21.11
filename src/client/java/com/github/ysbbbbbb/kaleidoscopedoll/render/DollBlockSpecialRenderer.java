package com.github.ysbbbbbb.kaleidoscopedoll.render;

import com.github.ysbbbbbb.kaleidoscopedoll.item.DollEntityItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        // 按模型实际引用的贴图分组：不能按方块 id 拼路径——玩偶模型会复用/改名贴图
        // （例如 doll_0.json 的贴图是 block/doll/doll_1），按 id 拼会得到不存在的贴图 → 黑紫块。
        Map<Identifier, List<BakedQuad>> quadsByTexture = new LinkedHashMap<>();
        for (BlockModelPart part : parts) {
            for (Direction direction : Direction.values()) {
                addQuads(quadsByTexture, part.getQuads(direction));
            }
            // 无朝向（不剔除）的面
            addQuads(quadsByTexture, part.getQuads(null));
        }
        if (quadsByTexture.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        // 方块模型的朝向与物品 display 变换差了 180°（GUI/掉落物面向玩家的一侧反了），
        // 绕方块中心（0.5, y, 0.5）转 180° 校正，避免绕角点旋转导致位置偏移。
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        for (Map.Entry<Identifier, List<BakedQuad>> entry : quadsByTexture.entrySet()) {
            List<BakedQuad> quads = entry.getValue();
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutNoCull(entry.getKey()),
                    (pose, buffer) -> emitQuads(buffer, pose, quads, light, overlay));
        }
        poseStack.popPose();
    }

    private static void addQuads(Map<Identifier, List<BakedQuad>> out, @Nullable List<BakedQuad> quads) {
        if (quads == null) {
            return;
        }
        for (BakedQuad quad : quads) {
            out.computeIfAbsent(textureOf(quad.sprite()), key -> new ArrayList<>()).add(quad);
        }
    }

    private static void emitQuads(VertexConsumer buffer, PoseStack.Pose pose, List<BakedQuad> quads,
                                  int light, int overlay) {
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
     * 图集 sprite → 该贴图的独立文件（完整路径，entityCutoutNoCull 要的就是完整路径）。
     * <p>
     * {@code SpriteContents#name()} 就是方块模型里引用的贴图 id（如 {@code kaleidoscope_doll:block/doll/doll_1}），
     * 比按方块 id 猜路径可靠。
     */
    private static Identifier textureOf(TextureAtlasSprite sprite) {
        Identifier name = sprite.contents().name();
        return Identifier.fromNamespaceAndPath(name.getNamespace(), "textures/" + name.getPath() + ".png");
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
