package com.github.ysbbbbbb.kaleidoscopedoll.render;

import com.github.ysbbbbbb.kaleidoscopedoll.entity.PlayerDollEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

/**
 * 玩家实体玩偶渲染器。
 * <p>
 * 这里<b>直接使用原版 {@link EntityRenderState}</b> 作为渲染状态：GeckoLib 通过 mixin 把
 * {@code GeoRenderState} 注入到了 {@code EntityRenderState} 上（编译期 loom 的 interface injection 也能看到），
 * 数据 map 由 mixin 自己维护。
 * <p>
 * 之前自建 render state 子类 + 手动委托 {@code GeoRenderState.Impl} 会踩坑：
 * mixin 给 {@code EntityRenderState} 加的 {@code addGeckolibData} 写在它自己的字段里，
 * 而自建类覆盖的 {@code getDataMap()} 返回另一张 map，导致
 * {@code AnimationProcessor.extractControllerStates} 拿不到 ANIMATABLE_MANAGER 而 NPE（客户端崩）。
 * <p>
 * 另外 {@link GeoEntityRenderer#createRenderState()} 是 final 且返回 null，
 * 必须覆写 {@link #createRenderState(PlayerDollEntity, Void)}。
 * <p>
 * 骑乘朝向在 {@link #extractRenderState} 里处理（与上游 {@code DollEntityRender} 一致：在 render 期取载具 yaw）。
 * 注意 1.21.11 的 <b>{@code EntityRenderState} 没有 {@code yRot} 字段</b>（{@code yRot/bodyRot/xRot} 在子类
 * {@code LivingEntityRenderState} 上），而 GeckoLib 的 {@code GeoEntityRenderer#applyRotations} 读的是渲染数据
 * {@code DataTickets.ENTITY_BODY_YAW}（默认值 0），所以真正要改的是这个 ticket。
 */
public class PlayerDollEntityRenderer extends GeoEntityRenderer<PlayerDollEntity, EntityRenderState> {
    public PlayerDollEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerDollEntityModel());
    }

    @Override
    public EntityRenderState createRenderState(PlayerDollEntity entity, Void relatedObject) {
        return new EntityRenderState();
    }

    /**
     * 骑乘时按载具朝向渲染：上游 {@code DollEntityRender} 在 {@code vehicle != null} 时直接用
     * {@code Mth.lerp(partialTick, vehicle.yRotO, vehicle.getYRot())} 作为整体 yaw。
     * <p>
     * GeckoLib 的默认 {@code calculateYRot} 只在载具是 {@code LivingEntity}（牛/猪/马…）时才取载具 yaw，
     * 船/矿车这类非生物载具会退回用玩偶自己的 yaw；这里统一覆盖成载具 yaw，两种载具都跟随。
     * <p>
     * {@code ENTITY_YAW} 给动画用，{@code ENTITY_BODY_YAW} 给姿态旋转用（{@code applyRotations} 里
     * {@code rotationDegrees(180 - bodyYaw)}），两个都写成同一个值。
     */
    @Override
    public void extractRenderState(PlayerDollEntity entity, EntityRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);

        Entity vehicle = entity.getVehicle();
        if (vehicle == null) {
            return;
        }
        float vehicleYaw = Mth.lerp(partialTick, vehicle.yRotO, vehicle.getYRot());
        state.addGeckolibData(DataTickets.ENTITY_YAW, vehicleYaw);
        state.addGeckolibData(DataTickets.ENTITY_BODY_YAW, vehicleYaw);
    }

    @Override
    public void addRenderData(PlayerDollEntity animatable, Void relatedObject,
                              EntityRenderState renderState, float partialTick) {
        super.addRenderData(animatable, relatedObject, renderState, partialTick);
        // 档案压进 GeckoLib 渲染状态，模型在 getTextureResource 里取
        renderState.addGeckolibData(PlayerDollDataTickets.PROFILE, animatable.getOwnerProfile());
        // 右键弹跳的时间戳：渲染端据此做 Q 弹形变
        renderState.addGeckolibData(PlayerDollDataTickets.BOUNCE_TIME, animatable.getBounceTime());
    }

    @Override
    public void preRenderPass(RenderPassInfo<EntityRenderState> info, SubmitNodeCollector collector) {
        super.preRenderPass(info, collector);
        applyBounce(info);
    }

    /**
     * 弹跳压扁：公式照抄上游 {@code DollEntityRender}（damping 0.6 / frequency 6 / strength 0.6，500ms）。
     */
    private static void applyBounce(RenderPassInfo<EntityRenderState> info) {
        Long bounceTime = info.renderState().getGeckolibData(PlayerDollDataTickets.BOUNCE_TIME);
        if (bounceTime == null) {
            return;
        }
        long time = bounceTime - System.currentTimeMillis();
        if (time <= 0) {
            return;
        }

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

        info.poseStack().scale(scaleXZ, scaleY, scaleXZ);
    }
}
