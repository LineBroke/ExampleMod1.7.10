package com.slotlock.slotlock.mixin;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.slotlock.slotlock.SlotLockDummyHelper;
import com.slotlock.slotlock.SlotLockManager;

@Pseudo
@Mixin(targets = "tconstruct.tools.inventory.CraftingStationContainer", remap = false)
public abstract class MixinCraftingStationContainer {

    /**
     * Prevent shift-clicking directly from a locked player slot.
     */
    @Inject(method = "transferStackInSlot", at = @At("HEAD"), cancellable = true, require = 0)
    private void slotlock$preventTransferLockedSlot(EntityPlayer player, int slotIndex,
        CallbackInfoReturnable<ItemStack> cir) {
        if (!SlotLockManager.hasAnyLock()) {
            return;
        }

        Slot slot = slotlock$getSlot(slotIndex);

        if (SlotLockManager.isLocked(slot)) {
            cir.setReturnValue(null);
        }
    }

    /**
     * Tinkers Crafting Station uses canMergeSlot / func_94530_a
     * while merging into inventories.
     *
     * If the target slot is locked, make it invalid as a destination.
     */
    @Inject(method = "func_94530_a", at = @At("HEAD"), cancellable = true, require = 0)
    private void slotlock$preventCanMergeIntoLockedSlot(ItemStack stack, Slot slot,
        CallbackInfoReturnable<Boolean> cir) {
        if (SlotLockManager.isLocked(slot)) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }

    /**
     * Hide locked slot contents from Tinkers' custom merge/refill logic.
     *
     * This prevents:
     * - merging crafted result into an already occupied locked slot
     * - treating locked slot contents as usable matching stacks
     * - special crafted tool result logic from using locked slots
     */
    @Redirect(
        method = { "mergeCraftedStack", "mergeItemStackRefill", "mergeItemStackMove" },
        at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;getStack()Lnet/minecraft/item/ItemStack;"),
        require = 0)
    private ItemStack slotlock$hideLockedStack(Slot slot) {
        if (SlotLockManager.isLocked(slot)) {
            return SlotLockDummyHelper.getDummyStack();
        }

        return slot.getStack();
    }

    /**
     * Make locked slots look occupied.
     *
     * This blocks empty locked slots from being selected by custom empty-slot movement.
     */
    @Redirect(
        method = { "mergeCraftedStack", "mergeItemStackRefill", "mergeItemStackMove" },
        at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Slot;getHasStack()Z"),
        require = 0)
    private boolean slotlock$hideEmptyLockedSlot(Slot slot) {
        if (SlotLockManager.isLocked(slot)) {
            return true;
        }

        return slot.getHasStack();
    }

    /**
     * Prevent locked slots from being treated as valid insertion targets.
     */
    @Redirect(
        method = { "mergeCraftedStack", "mergeItemStackRefill", "mergeItemStackMove" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/inventory/Slot;isItemValid(Lnet/minecraft/item/ItemStack;)Z"),
        require = 0)
    private boolean slotlock$preventValidatingLockedSlot(Slot slot, ItemStack stack) {
        if (SlotLockManager.isLocked(slot)) {
            return false;
        }

        return slot.isItemValid(stack);
    }

    /**
     * Replace locked player slots with the shared dummy slot
     * inside Tinkers' custom merge implementations.
     *
     * This is the important part for GTNH TConstruct:
     * mergeItemStack delegates into mergeItemStackRefill / mergeItemStackMove,
     * so targeting only mergeItemStack is not enough.
     */
    @Redirect(
        method = { "mergeCraftedStack", "mergeItemStackRefill", "mergeItemStackMove" },
        at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;"),
        require = 0)
    private Object slotlock$redirectSlotGetInTinkers(List<?> list, int index) {
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
