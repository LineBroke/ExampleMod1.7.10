package com.slotlock.slotlock.mixin;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.slotlock.slotlock.SlotLockManager;

import appeng.container.AEBaseContainer;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotFake;
import appeng.container.slot.SlotPatternTerm;
import appeng.helpers.InventoryAction;

@Mixin(value = AEBaseContainer.class, remap = false)
public abstract class MixinAEBaseContainer {

    /**
     * AE2 transferStackInSlot 来源保护。
     *
     * 玩家锁定槽不能作为 shift-click / 批量移动来源。
     */
    @Inject(method = "transferStackInSlot", at = @At("HEAD"), cancellable = true, remap = false)
    private void slotlock$preventTransferFromLockedPlayerSlot(EntityPlayer player, int slotIndex,
        CallbackInfoReturnable<ItemStack> cir) {
        if (!SlotLockManager.hasAnyLock()) {
            return;
        }

        Slot slot = slotlock$getSlot(slotIndex);

        if (slotlock$isLockedPlayerSlot(slot)) {
            cir.setReturnValue(null);
        }
    }

    /**
     * 更靠前的一层来源保护。
     *
     * AE2 transferStackInSlot 内部会先调用 isValidSrcSlotForTransfer。
     * 这里直接让锁定玩家槽成为无效来源槽。
     */
    @Inject(method = "isValidSrcSlotForTransfer", at = @At("HEAD"), cancellable = true, remap = false)
    private void slotlock$lockedSlotIsInvalidTransferSource(AppEngSlot clickSlot, CallbackInfoReturnable<Boolean> cir) {
        if (!SlotLockManager.hasAnyLock()) {
            return;
        }

        if (slotlock$isLockedPlayerSlot(clickSlot)) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }

    /**
     * AE2 transferStackInSlot 目标保护。
     *
     * AE2 不完全依赖原版 Container.mergeItemStack，
     * 它会自己调用 getValidDestinationSlots 收集目标槽。
     *
     * 所以这里从 AE2 目标槽列表里移除玩家锁定槽。
     */
    @Inject(method = "getValidDestinationSlots", at = @At("RETURN"), cancellable = true, remap = false)
    private void slotlock$removeLockedPlayerSlotsFromDestinations(boolean isPlayerSideSlot, ItemStack stackInSlot,
        CallbackInfoReturnable<List> cir) {
        if (!SlotLockManager.hasAnyLock()) {
            return;
        }

        List original = cir.getReturnValue();

        if (original == null || original.isEmpty()) {
            return;
        }

        List filtered = new ArrayList(original.size());

        for (Object object : original) {
            if (object instanceof Slot) {
                Slot slot = (Slot) object;

                if (slotlock$isLockedPlayerSlot(slot)) {
                    continue;
                }
            }

            filtered.add(object);
        }

        cir.setReturnValue(filtered);
    }

    /**
     * AE2 PacketInventoryAction / MOVE_REGION 保护。
     *
     * 重点：
     * Space + 左键触发 MOVE_REGION 时，
     * AE2 原逻辑会遍历所有同 class 槽，然后 transferStackInSlot。
     *
     * 如果不接管这里，锁定槽可能会被批量扫进去。
     *
     * 这里复刻 AE2 的 MOVE_REGION 行为，
     * 但跳过玩家锁定槽，然后取消 AE2 原逻辑。
     */
    @Inject(method = "doAction", at = @At("HEAD"), cancellable = true, remap = false)
    private void slotlock$handleMoveRegionWithoutLockedSlots(EntityPlayerMP player, InventoryAction action,
        int slotIndex, long id, CallbackInfo ci) {
        if (!SlotLockManager.hasAnyLock()) {
            return;
        }

        if (action != InventoryAction.MOVE_REGION) {
            return;
        }

        Slot clickedSlot = slotlock$getSlot(slotIndex);

        if (clickedSlot == null) {
            return;
        }

        /*
         * AE2 原逻辑：
         * fake slot / pattern terminal slot 不参与 MOVE_REGION。
         */
        if (clickedSlot instanceof SlotFake || clickedSlot instanceof SlotPatternTerm) {
            ci.cancel();
            return;
        }

        /*
         * 如果起点本身是锁定槽，直接吞掉。
         */
        if (slotlock$isLockedPlayerSlot(clickedSlot)) {
            slotlock$detectAndSendChanges();
            ci.cancel();
            return;
        }

        List<Slot> from = new LinkedList<Slot>();

        Container self = (Container) (Object) this;

        for (Object object : self.inventorySlots) {
            if (!(object instanceof Slot)) {
                continue;
            }

            Slot slot = (Slot) object;

            /*
             * AE2 原逻辑是同 class 批量移动。
             */
            if (slot.getClass() != clickedSlot.getClass()) {
                continue;
            }

            /*
             * SlotLock 关键过滤：
             * MOVE_REGION 批量扫描时跳过锁定玩家槽。
             */
            if (slotlock$isLockedPlayerSlot(slot)) {
                continue;
            }

            from.add(slot);
        }

        AEBaseContainer aeContainer = (AEBaseContainer) (Object) this;

        for (Slot slot : from) {
            aeContainer.transferStackInSlot(player, slot.slotNumber);
        }

        slotlock$detectAndSendChanges();
        ci.cancel();
    }

    /**
     * 其他 AE2 doAction 的兜底保护。
     *
     * 非 MOVE_REGION 的 action，如果直接作用在玩家锁定槽上，也取消。
     */
    @Inject(method = "doAction", at = @At("HEAD"), cancellable = true, remap = false)
    private void slotlock$preventActionsOnLockedPlayerSlot(EntityPlayerMP player, InventoryAction action, int slotIndex,
        long id, CallbackInfo ci) {
        if (!SlotLockManager.hasAnyLock()) {
            return;
        }

        if (action == InventoryAction.MOVE_REGION) {
            return;
        }

        Slot slot = slotlock$getSlot(slotIndex);

        if (slotlock$isLockedPlayerSlot(slot)) {
            slotlock$detectAndSendChanges();
            ci.cancel();
        }
    }

    /**
     * AE2 自己的槽位交换保护。
     */
    @Inject(method = "swapSlotContents", at = @At("HEAD"), cancellable = true, remap = false)
    private void slotlock$preventSwapWithLockedPlayerSlot(int slotA, int slotB, CallbackInfo ci) {
        if (!SlotLockManager.hasAnyLock()) {
            return;
        }

        Slot a = slotlock$getSlot(slotA);
        Slot b = slotlock$getSlot(slotB);

        if (slotlock$isLockedPlayerSlot(a) || slotlock$isLockedPlayerSlot(b)) {
            slotlock$detectAndSendChanges();
            ci.cancel();
        }
    }

    @Unique
    private Slot slotlock$getSlot(int slotIndex) {
        if (slotIndex < 0) {
            return null;
        }

        Container self = (Container) (Object) this;

        if (self.inventorySlots == null) {
            return null;
        }

        if (slotIndex >= self.inventorySlots.size()) {
            return null;
        }

        Object object = self.inventorySlots.get(slotIndex);

        if (!(object instanceof Slot)) {
            return null;
        }

        return (Slot) object;
    }

    @Unique
    private boolean slotlock$isLockedPlayerSlot(Slot slot) {
        if (slot == null) {
            return false;
        }

        if (!SlotLockManager.isPlayerInventorySlot(slot)) {
            return false;
        }

        int playerIndex = SlotLockManager.getPlayerSlotIndex(slot);

        if (playerIndex < 0 || playerIndex > 35) {
            return false;
        }

        return SlotLockManager.isLockedPlayerIndex(playerIndex);
    }

    @Unique
    private void slotlock$detectAndSendChanges() {
        Container self = (Container) (Object) this;
        self.detectAndSendChanges();
    }
}
