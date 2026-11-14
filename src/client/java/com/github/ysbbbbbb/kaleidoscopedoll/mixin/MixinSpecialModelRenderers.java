package com.github.ysbbbbbb.kaleidoscopedoll.mixin;

import com.github.ysbbbbbb.kaleidoscopedoll.KaleidoscopeDoll;
import com.github.ysbbbbbb.kaleidoscopedoll.render.DollBlockSpecialRenderer;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把自定义 special model 类型 {@code kaleidoscope_doll:doll_block} 注册进原版的 special model 注册表。
 * <p>
 * 做法与 GeckoLib 的 {@code SpecialModelRenderersMixin} 完全一致（同一目标、同一注入点）：
 * {@code SpecialModelRenderers.bootstrap()} 负责把 {@code <id> → MapCodec<? extends Unbaked>}
 * 填进私有的 {@code ID_MAPPER}（{@code ExtraCodecs.LateBoundIdMapper}），
 * 只有在那里追加，物品定义 JSON 里的 {@code {"type":"kaleidoscope_doll:doll_block"}} 才能被解析。
 * <p>
 * 取证：GeckoLib 5.4.5 的 mixin class 里 {@code @Mixin(class_10517)}、{@code @Shadow @Final field_55453}、
 * {@code @Inject(method = "method_65712", at = @At("TAIL"))}；本文件在编译期用 mojmap 名
 * ({@code SpecialModelRenderers / ID_MAPPER / bootstrap}) 书写，由 Loom 负责 remap。
 */
@Mixin(SpecialModelRenderers.class)
public class MixinSpecialModelRenderers {
    @Shadow
    @Final
    private static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends SpecialModelRenderer.Unbaked>> ID_MAPPER;

    @Inject(method = "bootstrap", at = @At("TAIL"))
    private static void kaleidoscopeDoll$registerDollBlockRenderer(CallbackInfo callbackInfo) {
        ID_MAPPER.put(Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "doll_block"),
                DollBlockSpecialRenderer.Unbaked.MAP_CODEC);
    }
}
