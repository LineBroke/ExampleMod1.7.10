package com.slotlock.slotlock.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.slotlock.slotlock.SlotLockManager;

@Mixin(value = ModularSlot.class, remap = false)
public abstract class MixinModularUI2ModularSlot {

    @Inject(method = "isItemValid", at = @At("HEAD"), cancellable = true, remap = false)
    private void slotlock$preventInsertIntoLockedSlot(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (SlotLockManager.isLocked((Slot) (Object) this)) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }

    @Inject(method = "canTakeStack", at = @At("HEAD"), cancellable = true, remap = false)
    private void slotlock$preventTakeFromLockedSlot(EntityPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (SlotLockManager.isLocked((Slot) (Object) this)) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }

    @Inject(method = "canDragIntoSlot", at = @At("HEAD"), cancellable = true, remap = false)
    private void slotlock$preventDragIntoLockedSlot(CallbackInfoReturnable<Boolean> cir) {
        if (SlotLockManager.isLocked((Slot) (Object) this)) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}
