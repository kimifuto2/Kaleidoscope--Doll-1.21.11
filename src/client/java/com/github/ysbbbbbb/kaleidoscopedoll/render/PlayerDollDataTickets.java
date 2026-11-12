package com.github.ysbbbbbb.kaleidoscopedoll.render;

import net.minecraft.world.item.component.ResolvableProfile;
import software.bernie.geckolib.constant.dataticket.DataTicket;

/**
 * 自定义渲染数据：把玩偶主人档案带进 GeckoLib 渲染状态。
 */
public final class PlayerDollDataTickets {
    public static final DataTicket<ResolvableProfile> PROFILE =
            DataTicket.create("kaleidoscope_doll:profile", ResolvableProfile.class);

    private PlayerDollDataTickets() {
    }
}
