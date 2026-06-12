package com.slotlock.slotlock.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.screen.ModularContainer;
import com.slotlock.slotlock.SlotLockManager;

@Mixin(value = ModularContainer.class, remap = false)
public abstract class MixinModularUI2Container {

    @Inject(method = "slotClick", at = @At("HEAD"), cancellable = true)
    private void slotlock$preventLockedSlotClick(int slotId, int mouseButton, int mode, EntityPlayer player,
        CallbackInfoReturnable<ItemStack> cir) {
        if (!SlotLockManager.hasAnyLock()) {
            return;
        }

        /*
         * Number-key swap into locked hotbar slot.
         */
        if (mode == 2 && mouseButton >= 0 && mouseButton <= 8) {
            if (SlotLockManager.isLockedPlayerIndex(mouseButton)) {
                cir.setReturnValue(null);
                return;
            }
        }

        /*
         * Double-click collect.
         */
        if (mode == 6) {
            cir.setReturnValue(null);
            return;
        }

        Slot slot = slotlock$getSlot(slotId);

        if (slot == null) {
            return;
        }

        if (SlotLockManager.isLocked(slot)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "transferStackInSlot", at = @At("HEAD"), cancellable = true, remap = false)
    private void slotlock$preventTransferFromLockedSlot(EntityPlayer player, int index,
        CallbackInfoReturnable<ItemStack> cir) {
        Slot slot = slotlock$getSlot(index);

        if (SlotLockManager.isLocked(slot)) {
            cir.setReturnValue(null);
        }
    }

    private Slot slotlock$getSlot(int slotId) {
        if (slotId < 0) {
            return null;
        }

        Container self = (Container) (Object) this;

        if (self.inventorySlots == null) {
            return null;
        }

        if (slotId >= self.inventorySlots.size()) {
            return null;
        }

        Object object = self.inventorySlots.get(slotId);

        if (!(object instanceof Slot)) {
            return null;
        }

        return (Slot) object;
    }
}
