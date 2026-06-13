package com.slotlock.slotlock.mixin;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.slotlock.slotlock.SlotLockDummyHelper;
import com.slotlock.slotlock.SlotLockInternalBypass;
import com.slotlock.slotlock.SlotLockManager;

@Mixin(Container.class)
public abstract class MixinContainer {

    @Inject(method = "slotClick", at = @At("HEAD"), cancellable = true)
    private void slotlock$preventDirectClickLockedSlot(int slotId, int mouseButton, int mode, EntityPlayer player,
        CallbackInfoReturnable<ItemStack> cir) {
        if (!SlotLockManager.hasAnyLock()) {
            return;
        }

        /*
         * Number-key swap into locked hotbar slot.
         * mode 2 = hotbar key swap
         * mouseButton 0-8 = target hotbar index
         */
        if (mode == 2 && mouseButton >= 0 && mouseButton <= 8) {
            if (SlotLockManager.isLockedPlayerIndex(mouseButton)) {
                cir.setReturnValue(null);
                return;
            }
        }

        /*
         * Double-click collect.
         * If any lock exists, cancelling this is safer because vanilla collect
         * can pull from many matching slots.
         */
        if (mode == 6) {
            cir.setReturnValue(null);
            return;
        }

        Slot slot = slotlock$getSlot(slotId);

        if (slot == null) {
            return;
        }

        if (!SlotLockManager.isLocked(slot)) {
            return;
        }

        /*
         * IMPORTANT:
         * Previously, left click on a locked slot was allowed here for AutoMover.
         * That left a hole:
         * NEI recipe fill / overlay transfer can bypass GuiContainer and call
         * Container.slotClick directly with mode 0, mouseButton 0.
         * So locked slots must block normal left click here too.
         * Only SlotLockAutoMover may temporarily bypass this through
         * SlotLockInternalBypass.
         */
        if (mode == 0 && mouseButton == 0 && SlotLockInternalBypass.isAllowed(slot)) {
            return;
        }

        /*
         * All direct interaction with locked slots is blocked:
         * mode 0 = normal left/right click
         * mode 1 = shift click
         * mode 2 = hotbar swap
         * mode 3 = pick block / middle click style action
         * mode 4 = drop
         * mode 5 = drag
         * mode 6 = double click collect
         */
        cir.setReturnValue(null);
    }

    /**
     * Redirect Container.mergeItemStack's List.get(index).
     *
     * If vanilla tries to access a locked player inventory slot while shift-clicking,
     * return the dummy slot instead.
     *
     * The dummy slot looks full and invalid, so mergeItemStack will skip it.
     */
    @Redirect(
        method = "mergeItemStack",
        at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;"))
    private Object slotlock$redirectSlotGet(List<?> list, int index) {
        Object object = list.get(index);

        if (!(object instanceof Slot)) {
            return object;
        }

        Slot slot = (Slot) object;

        if (SlotLockManager.isLocked(slot)) {
            return SlotLockDummyHelper.getDummySlot();
        }

        return object;
    }

    @Unique
    private Slot slotlock$getSlot(int slotId) {
        if (slotId < 0) {
            return null;
        }

        Container container = (Container) (Object) this;

        if (container.inventorySlots == null) {
            return null;
        }

        if (slotId >= container.inventorySlots.size()) {
            return null;
        }

        Object object = container.inventorySlots.get(slotId);

        if (!(object instanceof Slot)) {
            return null;
        }

        return (Slot) object;
    }
}
