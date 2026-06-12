package com.slotlock.slotlock.mixin;

import net.minecraft.inventory.IInventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cleanroommc.bogosorter.common.dropoff.DropOffHandler;
import com.slotlock.slotlock.SlotLockManager;

@Mixin(value = DropOffHandler.class, remap = false)
public abstract class MixinBogoSorterDropOffHandler {

    /**
     * InventoryBogoSorter DropOff protection.
     *
     * DropOff does not use Container.slotClick.
     * It directly scans player.inventory.mainInventory and mutates the ItemStack array.
     *
     * So locked player slots must be blocked here.
     */
    @Inject(method = "movePlayerStack", at = @At("HEAD"), cancellable = true, remap = false)
    private void slotlock$preventDropOffLockedPlayerSlot(int playerStackIndex, IInventory toInventory,
        CallbackInfo ci) {
        if (!SlotLockManager.hasAnyLock()) {
            return;
        }

        if (playerStackIndex < 0 || playerStackIndex > 35) {
            return;
        }

        if (SlotLockManager.isLockedPlayerIndex(playerStackIndex)) {
            ci.cancel();
        }
    }
}
