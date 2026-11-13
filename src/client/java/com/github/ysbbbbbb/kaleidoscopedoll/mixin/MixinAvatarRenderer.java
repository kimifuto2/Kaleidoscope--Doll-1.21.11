package com.github.ysbbbbbb.kaleidoscopedoll.mixin;

import com.github.ysbbbbbb.kaleidoscopedoll.client.trinkets.TrinketsHatSupport;
import com.github.ysbbbbbb.kaleidoscopedoll.debug.DollDebug;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家戴着 Trinkets 帽子（玩偶）时，隐藏原版头盔槽里的东西，避免和帽子上的玩偶重叠。
 * <p>
 * 1.21.11 里头盔槽有<b>两条独立</b>渲染路径，必须都清掉：
 * <ol>
 *   <li><b>护甲</b>：{@link HumanoidRenderState#headEquipment} → {@code HumanoidArmorLayer}
 *       （按 {@code EQUIPPABLE} 的 equipment asset 画）；</li>
 *   <li><b>非护甲物品</b>（方块物品、头颅、我们的玩偶方块）：
 *       {@link LivingEntityRenderState#headItem}（{@code ItemStackRenderState}）+ {@code wornHeadType} /
 *       {@code wornHeadProfile} → {@code CustomHeadLayer#submit}。javap 取证：
 *       {@code CustomHeadLayer.submit} 读的是 {@code LivingEntityRenderState.headItem} 与
 *       {@code wornHeadType}，<b>完全不读 headEquipment</b>；填充发生在
 *       {@code LivingEntityRenderer.extractRenderState} 里的
 *       {@code ItemModelResolver.updateForLiving(state.headItem, headStack, HEAD, living)}。</li>
 * </ol>
 * 因此只清 {@code headEquipment} 时"护甲消失了但头盔槽的玩偶还在"就完全对应这条路径。
 * <p>
 * 未安装 Trinkets 时 {@link TrinketsHatSupport} 的所有入口都返回 false，此 mixin 等于空操作。
 */
@Mixin(AvatarRenderer.class)
public class MixinAvatarRenderer {
    @Unique
    private static boolean kaleidoscopeDoll$probed;

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
            at = @At("RETURN")
    )
    private void kaleidoscopeDoll$hideHeadEquipmentWhenHat(Entity entity, EntityRenderState state, float partialTick,
                                                           CallbackInfo callbackInfo) {
        if (!kaleidoscopeDoll$probed) {
            kaleidoscopeDoll$probed = true;
            DollDebug.msg("CLIENT", "avatar.mixin",
                    "invoked=true trinkets=" + TrinketsHatSupport.isTrinketsLoaded());
        }
        if (!(entity instanceof LivingEntity living) || !(state instanceof LivingEntityRenderState livingState)) {
            return;
        }
        if (!TrinketsHatSupport.hasDollInHeadSlot(living)) {
            return;
        }

        // 路径 1：头盔槽的护甲
        if (state instanceof HumanoidRenderState humanoid) {
            humanoid.headEquipment = ItemStack.EMPTY;
        }
        // 路径 2：头盔槽的非护甲物品（玩偶方块 / 头颅），由 CustomHeadLayer 读取
        livingState.headItem.clear();
        livingState.wornHeadType = null;
        livingState.wornHeadProfile = null;
    }
}
