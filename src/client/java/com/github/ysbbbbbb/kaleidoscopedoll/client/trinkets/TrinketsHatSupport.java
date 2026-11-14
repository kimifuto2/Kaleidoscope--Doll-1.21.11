package com.github.ysbbbbbb.kaleidoscopedoll.client.trinkets;

import com.github.ysbbbbbb.kaleidoscopedoll.debug.DollDebug;
import com.github.ysbbbbbb.kaleidoscopedoll.item.DollItem;
import com.github.ysbbbbbb.kaleidoscopedoll.item.PlayerDollItem;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * Trinkets 帽子槽检测（纯反射，零编译依赖，未安装 Trinkets 时全部跳过）。
 * <p>
 * 只用到 Trinkets 自己的类/方法名（这些名字不会被 remap），MC 侧只做 {@code instanceof ItemStack} 判断，
 * 因此 dev（命名空间 = mojmap）与正式环境（intermediary）都能跑。
 * <p>
 * 语义照搬 SimpleHats：只要 Trinkets 组件里戴着玩偶（优先判定 head 组），就把原版头盔槽的渲染内容清掉，
 * 避免"帽子槽玩偶 + 头盔槽装备"两个一起渲染。
 */
public final class TrinketsHatSupport {
    private static final String MOD_ID = "trinkets";
    private static final String HEAD_GROUP = "head";
    /** 本模组为玩偶单独注册的 Trinkets 饰品组（data/trinkets/slots/doll）。 */
    private static final String DOLL_GROUP = "doll";
    private static final boolean TRINKETS_LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID);

    private static boolean initialized;
    private static boolean available;
    private static boolean failureLogged;

    /** TrinketsApi#getTrinketComponent(LivingEntity) */
    private static Method mGetComponent;
    /** TrinketComponent#getAllEquipped() -> List<Tuple<SlotReference, ItemStack>> */
    private static Method mGetAllEquipped;
    /** SlotReference#inventory() */
    private static Method mSlotRefInventory;
    /** TrinketInventory#getSlotType() */
    private static Method mInventorySlotType;
    /** SlotType#getGroup() */
    private static Method mSlotTypeGroup;
    /** Tuple#getA() 与 Tuple#getB()：MC 类方法名随映射变化，按返回值类型动态识别后缓存 */
    private static Method mTupleSlot;
    private static Method mTupleStack;

    private TrinketsHatSupport() {
    }

    public static boolean isTrinketsLoaded() {
        return TRINKETS_LOADED;
    }

    /**
     * 该实体是否在 Trinkets 帽子组里戴着玩偶（{@link DollItem} 或 {@link PlayerDollItem}）。
     */
    public static boolean hasDollInHeadSlot(LivingEntity living) {
        if (!TRINKETS_LOADED || living == null) {
            return false;
        }
        ensureInit();
        if (!available) {
            return false;
        }
        try {
            Object optional = mGetComponent.invoke(null, living);
            if (!(optional instanceof Optional<?> opt) || opt.isEmpty()) {
                return false;
            }
            Object component = opt.get();
            Object equippedObj = mGetAllEquipped.invoke(component);
            if (!(equippedObj instanceof List<?> equipped) || equipped.isEmpty()) {
                return false;
            }
            for (Object entry : equipped) {
                ItemStack stack = stackOf(entry);
                if (stack == null || stack.isEmpty() || !isDoll(stack)) {
                    continue;
                }
                String group = groupOf(entry);
                // 取不到组名时退回 SimpleHats 的"任意槽位戴着即算"行为；
                // head 组兼容旧存档（玩偶以前放在帽子槽），doll 组是新的独立玩偶槽
                if (group == null || HEAD_GROUP.equalsIgnoreCase(group) || DOLL_GROUP.equalsIgnoreCase(group)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable throwable) {
            logFailure(throwable);
            return false;
        }
    }

    private static boolean isDoll(ItemStack stack) {
        return stack.getItem() instanceof DollItem || stack.getItem() instanceof PlayerDollItem;
    }

    private static ItemStack stackOf(Object entry) {
        ensureTupleAccessors(entry);
        if (mTupleStack == null) {
            return null;
        }
        try {
            Object value = mTupleStack.invoke(entry);
            return value instanceof ItemStack stack ? stack : null;
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static String groupOf(Object entry) {
        ensureTupleAccessors(entry);
        if (mTupleSlot == null || mSlotRefInventory == null || mInventorySlotType == null || mSlotTypeGroup == null) {
            return null;
        }
        try {
            Object slotRef = mTupleSlot.invoke(entry);
            if (slotRef == null) {
                return null;
            }
            Object inventory = mSlotRefInventory.invoke(slotRef);
            if (inventory == null) {
                return null;
            }
            Object slotType = mInventorySlotType.invoke(inventory);
            if (slotType == null) {
                return null;
            }
            Object group = mSlotTypeGroup.invoke(slotType);
            return group instanceof String name ? name : null;
        } catch (Throwable throwable) {
            return null;
        }
    }

    /**
     * Tuple 的 getA()/getB() 是 MC 类方法，dev 与正式环境的真实名字不同，
     * 因此不按名字查，而是挨个调用无参方法、按返回类型认出"槽位引用"与"物品栈"并缓存。
     */
    private static void ensureTupleAccessors(Object entry) {
        if (entry == null || (mTupleStack != null && mTupleSlot != null)) {
            return;
        }
        for (Method method : entry.getClass().getMethods()) {
            if (method.getParameterCount() != 0 || method.getDeclaringClass() == Object.class) {
                continue;
            }
            try {
                Object value = method.invoke(entry);
                if (value instanceof ItemStack) {
                    mTupleStack = method;
                } else if (value != null && "SlotReference".equals(value.getClass().getSimpleName())) {
                    mTupleSlot = method;
                }
            } catch (Throwable ignored) {
                // 探测失败的方法直接跳过
            }
        }
    }

    private static void ensureInit() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            Class<?> api = Class.forName("dev.emi.trinkets.api.TrinketsApi");
            mGetComponent = api.getMethod("getTrinketComponent", LivingEntity.class);

            Class<?> componentClass = Class.forName("dev.emi.trinkets.api.TrinketComponent");
            mGetAllEquipped = componentClass.getMethod("getAllEquipped");

            Class<?> slotRefClass = Class.forName("dev.emi.trinkets.api.SlotReference");
            mSlotRefInventory = slotRefClass.getMethod("inventory");

            Class<?> inventoryClass = Class.forName("dev.emi.trinkets.api.TrinketInventory");
            mInventorySlotType = inventoryClass.getMethod("getSlotType");

            Class<?> slotTypeClass = Class.forName("dev.emi.trinkets.api.SlotType");
            mSlotTypeGroup = slotTypeClass.getMethod("getGroup");

            available = true;
            DollDebug.msg("CLIENT", "trinkets.hat.compat", "available=true");
        } catch (Throwable throwable) {
            available = false;
            logFailure(throwable);
        }
    }

    private static void logFailure(Throwable throwable) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        DollDebug.msg("CLIENT", "trinkets.hat.compat",
                "available=false err=" + throwable.getClass().getSimpleName() + " msg=" + throwable.getMessage());
    }
}
