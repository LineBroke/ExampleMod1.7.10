package com.slotlock.slotlock.mixin;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.slotlock.slotlock.SlotLockManager;

@Mixin(EntityPlayer.class)
public abstract class MixinEntityPlayer {

    @Shadow
    public InventoryPlayer inventory;

    @Inject(method = "dropOneItem", at = @At("HEAD"), cancellable = true)
    private void slotlock$preventDropLockedCurrentItem(boolean dropAll, CallbackInfoReturnable<EntityItem> cir) {
        if (this.inventory == null) {
            return;
        }

        int currentHotbarIndex = this.inventory.currentItem;

        if (SlotLockManager.isLockedPlayerIndex(currentHotbarIndex)) {
            cir.setReturnValue(null);
            cir.cancel();
        }
    }
}
