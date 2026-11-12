package com.github.ysbbbbbb.kaleidoscopedoll.render;

import com.github.ysbbbbbb.kaleidoscopedoll.debug.DollDebug;
import com.github.ysbbbbbb.kaleidoscopedoll.event.ModRegisterEvent;
import com.github.ysbbbbbb.kaleidoscopedoll.init.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 玩偶的 Trinkets 头饰渲染（照搬 SimpleHats 的根节点 + 头部节点方式，
 * 使动画资源包改变模型根节点/头部姿态时玩偶仍贴合头部）。
 * 反射注册，未安装 Trinkets 时零依赖。
 */
public final class PlayerDollTrinketRenderer implements InvocationHandler {
    public static void register() {
        if (!FabricLoader.getInstance().isModLoaded("trinkets")) {
            return;
        }
        try {
            Class<?> rendererInterface = Class.forName("dev.emi.trinkets.api.client.TrinketRenderer");
            Class<?> registry = Class.forName("dev.emi.trinkets.api.client.TrinketRendererRegistry");
            Object proxy = Proxy.newProxyInstance(rendererInterface.getClassLoader(),
                    new Class<?>[]{rendererInterface}, new PlayerDollTrinketRenderer());
            Method registerMethod = registry.getMethod("registerRenderer", Item.class, rendererInterface);

            registerMethod.invoke(null, ModItems.PLAYER_DOLL, proxy);
            for (Item doll : ModRegisterEvent.DOLL_ITEMS) {
                registerMethod.invoke(null, doll, proxy);
            }
            DollDebug.msg("CLIENT", "trinket.register", "ok=true dolls=" + (ModRegisterEvent.DOLL_ITEMS.size() + 1));
        } catch (Throwable throwable) {
            DollDebug.msg("CLIENT", "trinket.register.error",
                    "err=" + throwable.getClass().getSimpleName() + " msg=" + throwable.getMessage());
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        if ("render".equals(method.getName()) && args != null && args.length >= 7) {
            try {
                render(args);
            } catch (Throwable throwable) {
                DollDebug.msg("CLIENT", "trinket.render.error",
                        "err=" + throwable.getClass().getSimpleName() + " msg=" + throwable.getMessage());
            }
        }
        return null;
    }

    private void render(Object[] args) {
        ItemStack stack = (ItemStack) args[0];
        EntityModel<?> model = (EntityModel<?>) args[2];
        PoseStack poseStack = (PoseStack) args[3];
        SubmitNodeCollector collector = (SubmitNodeCollector) args[4];
        int light = (Integer) args[5];

        if (!(model instanceof HeadedModel headModel)) {
            return;
        }
        // SimpleHats 方案：先应用模型根节点，再应用头部节点，动画包改变骨骼时也能贴合
        model.root().translateAndRotate(poseStack);
        headModel.getHead().translateAndRotate(poseStack);
        poseStack.translate(0.0F, -0.25F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(0.625F, -0.625F, -0.625F);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        ItemStackRenderState renderState = new ItemStackRenderState();
        minecraft.getItemModelResolver().updateForLiving(renderState, stack,
                ItemDisplayContext.HEAD, minecraft.player);
        renderState.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
    }
}
