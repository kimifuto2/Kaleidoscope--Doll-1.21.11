package com.github.ysbbbbbb.kaleidoscopedoll.render;

import com.github.ysbbbbbb.kaleidoscopedoll.entity.DollEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * 实体版玩偶（{@link DollEntity}）的渲染器，渲染成所存储方块的模型。
 * <p>
 * 1.21.11 的实体渲染已改成 render state 管线：
 * <ul>
 *     <li>{@link #createRenderState()} 每帧创建一次渲染状态；</li>
 *     <li>{@link #extractRenderState} 在抽取阶段把实体上需要的数据（方块、位移、缩放、朝向、弹跳时间）拷进状态，</li>
 *     <li>{@link #submit} 在提交阶段只读渲染状态，用 {@link SubmitNodeCollector#submitBlock} 提交方块。</li>
 * </ul>
 * 因此提交阶段拿不到 {@code DollEntity}，所有实体侧数据都必须在抽取阶段写入 {@link DollEntityRenderState}。
 */
public class DollEntityRender extends EntityRenderer<DollEntity, DollEntityRender.DollEntityRenderState> {

    public DollEntityRender(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public DollEntityRenderState createRenderState() {
        return new DollEntityRenderState();
    }

    @Override
    public void extractRenderState(DollEntity dollEntity, DollEntityRenderState state, float partialTick) {
        // 必须调用父类，父类会写入 lightCoords / outlineColor（submitBlock 需要用到）
        super.extractRenderState(dollEntity, state, partialTick);

        state.blockState = dollEntity.getDisplayBlockState();

        // 同步数据里存的是可变对象，拷进渲染状态，避免与实体共享引用
        state.translation.set(dollEntity.getDisplayTranslation());
        state.scale.set(dollEntity.getDisplayScale());

        // 骑乘时跟随载具的朝向，否则用实体自身朝向；pitch 始终取实体自身
        Entity vehicle = dollEntity.getVehicle();
        if (vehicle != null) {
            state.yRot = Mth.lerp(partialTick, vehicle.yRotO, vehicle.getYRot());
        } else {
            state.yRot = Mth.lerp(partialTick, dollEntity.yRotO, dollEntity.getYRot());
        }
        state.xRot = Mth.lerp(partialTick, dollEntity.xRotO, dollEntity.getXRot());

        state.bounceTime = dollEntity.getBounceTime();
    }

    @Override
    public void submit(DollEntityRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        BlockState blockState = state.blockState;
        if (blockState == null || blockState.isAir()) {
            return;
        }

        poseStack.pushPose();

        // 应用位移变换
        Vector3f translation = state.translation;
        poseStack.translate(translation.x, translation.y, translation.z);

        // 应用 Y 轴旋转（骑乘时用载具 yaw，否则用实体 yaw，方向取反）
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yRot));
        // 应用 X 轴旋转（pitch）
        poseStack.mulPose(Axis.XP.rotationDegrees(state.xRot));

        // 应用缩放变换
        Vector3f scale = state.scale;
        poseStack.scale(scale.x, scale.y, scale.z);

        // 如果是弹跳时间，那么应用弹跳效果
        long time = state.bounceTime - System.currentTimeMillis();
        if (time > 0) {
            // 阻尼系数 - 控制弹跳衰减速度
            float dampingFactor = 0.6f;
            // 弹跳频率 - 控制弹跳次数
            float frequency = 6f;
            // 压缩强度
            float bounceStrength = 0.6f;

            // 计算弹跳进度 (0-1) 500ms持续时间
            float bounceProgress = 1.0f - ((float) time / 500.0f);
            // 使用指数衰减函数模拟阻尼
            float dampedAmplitude = (float) Math.exp(-dampingFactor * bounceProgress * 8.0f);
            // 压缩变形效果 - 模拟弹性物体的形变
            float compressionIntensity = dampedAmplitude * bounceStrength;

            // 当接触地面时（弹跳到最低点），Y轴压缩，X/Z轴扩张
            float groundContact = (float) Math.max(0, -Math.sin(bounceProgress * frequency * Math.PI));
            float scaleY = 1.0f - groundContact * compressionIntensity;
            float scaleXZ = 1.0f + groundContact * compressionIntensity;

            poseStack.scale(scaleXZ, scaleY, scaleXZ);
        }

        // 将方块中心对齐到实体位置
        poseStack.translate(-0.5, 0, -0.5);

        // 提交方块：光照/叠加取自渲染状态，最后一个参数是描边色（0 = 无描边，发光实体才非 0）
        submitNodeCollector.submitBlock(poseStack, blockState, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);

        poseStack.popPose();

        // 交给父类处理拴绳与名字标签
        super.submit(state, poseStack, submitNodeCollector, cameraRenderState);
    }

    /**
     * 玩偶实体的渲染状态：承载提交阶段需要的全部数据（1.21.11 起渲染拆成「抽取 / 提交」两阶段，
     * 提交阶段不能再访问实体）。
     */
    public static class DollEntityRenderState extends EntityRenderState {
        /**
         * 要显示的方块状态；为 null 或空气时不渲染
         */
        public BlockState blockState;
        /**
         * 位移偏移
         */
        public final Vector3f translation = new Vector3f();
        /**
         * 缩放
         */
        public final Vector3f scale = new Vector3f(1.0F, 1.0F, 1.0F);
        /**
         * 插值后的 yaw（骑乘时来自载具，否则来自实体自身）
         */
        public float yRot;
        /**
         * 插值后的 pitch
         */
        public float xRot;
        /**
         * 弹跳结束的时间戳（毫秒），与 {@link System#currentTimeMillis()} 比较判断是否还在弹跳
         */
        public long bounceTime;
    }
}
