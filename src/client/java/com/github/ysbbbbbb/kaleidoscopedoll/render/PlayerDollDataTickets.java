package com.github.ysbbbbbb.kaleidoscopedoll.render;

import net.minecraft.world.item.component.ResolvableProfile;
import software.bernie.geckolib.constant.dataticket.DataTicket;

/**
 * 自定义渲染数据：把玩偶主人档案带进 GeckoLib 渲染状态。
 */
public final class PlayerDollDataTickets {
    public static final DataTicket<ResolvableProfile> PROFILE =
            DataTicket.create("kaleidoscope_doll:profile", ResolvableProfile.class);

    /**
     * 弹跳结束时间戳（毫秒），渲染时与 {@link System#currentTimeMillis()} 比较做 Q 弹形变
     */
    public static final DataTicket<Long> BOUNCE_TIME =
            DataTicket.create("kaleidoscope_doll:bounce_time", Long.class);

    private PlayerDollDataTickets() {
    }
}
