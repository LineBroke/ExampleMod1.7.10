package com.slotlock.slotlock.mixin;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.bogosorter.ClientEventHandler;
import com.cleanroommc.bogosorter.common.sort.GuiSortingContext;
import com.cleanroommc.bogosorter.common.sort.SlotGroup;
import com.cleanroommc.bogosorter.mixins.early.minecraft.SlotAccessor;
import com.slotlock.slotlock.SlotLockManager;

@Mixin(value = ClientEventHandler.class, remap = false)
public abstract class MixinBogoSorterClientEventHandler {

    /**
     * 阻止 InventoryBogoSorter 整理包含锁定槽的玩家背包组。
     *
     * 这样可以避免：
     * - 锁定槽里的物品被排序移动走
     * - 物品被整理进锁定空槽
     */
    @Inject(
        method = "sort(Lnet/minecraft/inventory/Container;Lcom/cleanroommc/bogosorter/mixins/early/minecraft/SlotAccessor;I)Z",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void slotlock$cancelSortIfGroupContainsLockedSlot(Container container, SlotAccessor hoveredSlot,
        int slotNumber, CallbackInfoReturnable<Boolean> cir) {
        if (!SlotLockManager.hasAnyLock()) {
            return;
        }

        if (container == null) {
            return;
        }

        GuiSortingContext sortingContext = GuiSortingContext.getOrCreate(container);

        if (sortingContext == null || sortingContext.isEmpty()) {
            return;
        }

        SlotGroup slotGroup = findTargetSlotGroup(sortingContext, hoveredSlot, slotNumber);

        if (slotGroup == null || slotGroup.isEmpty()) {
            return;
        }

        if (!slotGroupContainsLockedPlayerSlot(container, slotGroup)) {
            return;
        }

        /*
         * 返回 false：
         * 表示这次 InventoryBogoSorter 排序没有执行。
         * 重点是不要让它继续发送 CSort 包。
         */
        cir.setReturnValue(Boolean.FALSE);
    }

    private static SlotGroup findTargetSlotGroup(GuiSortingContext sortingContext, SlotAccessor hoveredSlot,
        int slotNumber) {
        if (hoveredSlot != null) {
            return sortingContext.getSlotGroup(hoveredSlot.getSlotNumber());
        }

        if (slotNumber != -1) {
            return sortingContext.getSlotGroup(slotNumber);
        }

        /*
         * 对应 InventoryBogoSorter 自己的逻辑：
         * 如果没有明确 hover slot，就尝试整理玩家背包组。
         */
        if (sortingContext.hasPlayer() && sortingContext.getNonPlayerSlotGroupAmount() == 0) {
            return sortingContext.getPlayerSlotGroup();
        }

        return null;
    }

    private static boolean slotGroupContainsLockedPlayerSlot(Container container, SlotGroup slotGroup) {
        for (Object object : slotGroup.getSlots()) {
            if (!(object instanceof SlotAccessor)) {
                continue;
            }

            SlotAccessor slotAccessor = (SlotAccessor) object;

            int slotNumber = slotAccessor.getSlotNumber();

            if (slotNumber < 0 || slotNumber >= container.inventorySlots.size()) {
                continue;
            }

            Object slotObject = container.inventorySlots.get(slotNumber);

            if (!(slotObject instanceof Slot)) {
                continue;
            }

            Slot slot = (Slot) slotObject;

            if (!SlotLockManager.isPlayerInventorySlot(slot)) {
                continue;
            }

            int playerIndex = SlotLockManager.getPlayerSlotIndex(slot);

            if (SlotLockManager.isLockedPlayerIndex(playerIndex)) {
                return true;
            }
        }

        return false;
    }
}
