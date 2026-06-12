package com.slotlock.slotlock.mixin;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.bogosorter.common.sort.SlotGroup;
import com.cleanroommc.bogosorter.common.sort.SortHandler;
import com.cleanroommc.bogosorter.mixins.early.minecraft.SlotAccessor;
import com.slotlock.slotlock.SlotLockManager;

@Mixin(value = SortHandler.class, remap = false)
public abstract class MixinBogoSorterSortHandler {

    @Shadow
    @Final
    private Container container;

    /**
     * BogoSorter 普通整理时会调用 getSortableSlots。
     *
     * 这里不要取消整个排序。
     * 只把 SlotLock 锁定的玩家背包槽从可整理列表中移除。
     */
    @Inject(method = "getSortableSlots", at = @At("RETURN"), cancellable = true, remap = false)
    private void slotlock$removeLockedSlotsFromSort(SlotGroup slotGroup,
        CallbackInfoReturnable<List<SlotAccessor>> cir) {
        if (!SlotLockManager.hasAnyLock()) {
            return;
        }

        List<SlotAccessor> original = cir.getReturnValue();

        if (original == null || original.isEmpty()) {
            return;
        }

        List<SlotAccessor> filtered = new ArrayList<SlotAccessor>(original.size());

        for (SlotAccessor slotAccessor : original) {
            if (!slotlock$isLockedPlayerSlot(slotAccessor)) {
                filtered.add(slotAccessor);
            }
        }

        cir.setReturnValue(filtered);
    }

    private boolean slotlock$isLockedPlayerSlot(SlotAccessor slotAccessor) {
        if (slotAccessor == null || container == null) {
            return false;
        }

        int slotNumber = slotAccessor.getSlotNumber();

        if (slotNumber < 0 || slotNumber >= container.inventorySlots.size()) {
            return false;
        }

        Object slotObject = container.inventorySlots.get(slotNumber);

        if (!(slotObject instanceof Slot)) {
            return false;
        }

        Slot slot = (Slot) slotObject;

        if (!SlotLockManager.isPlayerInventorySlot(slot)) {
            return false;
        }

        int playerIndex = SlotLockManager.getPlayerSlotIndex(slot);

        return SlotLockManager.isLockedPlayerIndex(playerIndex);
    }
}
