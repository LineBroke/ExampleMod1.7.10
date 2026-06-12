package com.slotlock.slotlock.mixin;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.slotlock.slotlock.SlotLockNEICompat;

import codechicken.nei.NEIClientUtils;

@Mixin(value = NEIClientUtils.class, remap = false)
public abstract class MixinNEIClientUtils {

    /**
     * 拦截 NEI Cheat 给物品。
     *
     * 原 NEI giveStack 会发送 GIVE_ITEM，让服务端自己找背包空位。
     * 这样可能进入锁定槽。
     *
     * 我们改成：
     * 只把物品精确写入未锁定玩家背包槽。
     */
    @Inject(
        method = "giveStack(Lnet/minecraft/item/ItemStack;IZ)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void slotlock$giveStackUnlockedOnly(ItemStack base, int amount, boolean infinite, CallbackInfo ci) {
        if (SlotLockNEICompat.giveStackUnlockedOnly(base, amount, infinite)) {
            ci.cancel();
        }
    }
}
