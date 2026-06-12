package com.SlotLock.slotlock.mixin;

import java.util.List;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.SlotLock.slotlock.SlotLockManager;

@Mixin(Container.class)
public abstract class MixinContainer {

    @Shadow
    public List<Slot> inventorySlots;

    /**
     * 1.7.10 Container.mergeItemStack
     *
     * Shift-click 会通过这个方法把物品合并进目标槽位范围。
     * 这里重写它的逻辑，让 Shift-click 可以继续使用，
     * 但合并时自动跳过 SlotLock 锁定的玩家背包格。
     */
    @Inject(method = "mergeItemStack", at = @At("HEAD"), cancellable = true)
    private void slotlock$mergeItemStackSkipLockedSlots(ItemStack stack, int startIndex, int endIndex,
        boolean reverseDirection, CallbackInfoReturnable<Boolean> cir) {
        boolean changed = false;

        if (stack == null) {
            cir.setReturnValue(false);
            return;
        }

        int index = reverseDirection ? endIndex - 1 : startIndex;

        /*
         * 第一阶段：
         * 优先把物品叠加到已有同类物品的槽位。
         */
        if (stack.isStackable()) {
            while (stack.stackSize > 0 && isIndexInRange(index, startIndex, endIndex, reverseDirection)) {
                Slot slot = this.inventorySlots.get(index);

                if (!SlotLockManager.isLocked(slot)) {
                    ItemStack slotStack = slot.getStack();

                    if (canMerge(slotStack, stack)) {
                        int maxStackSize = Math.min(stack.getMaxStackSize(), slot.getSlotStackLimit());
                        int mergedSize = slotStack.stackSize + stack.stackSize;

                        if (mergedSize <= maxStackSize) {
                            stack.stackSize = 0;
                            slotStack.stackSize = mergedSize;
                            slot.onSlotChanged();
                            changed = true;
                        } else if (slotStack.stackSize < maxStackSize) {
                            int moveAmount = maxStackSize - slotStack.stackSize;

                            stack.stackSize -= moveAmount;
                            slotStack.stackSize = maxStackSize;
                            slot.onSlotChanged();
                            changed = true;
                        }
                    }
                }

                index = nextIndex(index, reverseDirection);
            }
        }

        /*
         * 第二阶段：
         * 如果还有剩余物品，放入未锁定的空槽。
         */
        if (stack.stackSize > 0) {
            index = reverseDirection ? endIndex - 1 : startIndex;

            while (isIndexInRange(index, startIndex, endIndex, reverseDirection)) {
                Slot slot = this.inventorySlots.get(index);

                if (!SlotLockManager.isLocked(slot)) {
                    ItemStack slotStack = slot.getStack();

                    if (slotStack == null && slot.isItemValid(stack)) {
                        int maxStackSize = Math.min(stack.getMaxStackSize(), slot.getSlotStackLimit());

                        ItemStack copy = stack.copy();

                        if (copy.stackSize > maxStackSize) {
                            copy.stackSize = maxStackSize;
                        }

                        slot.putStack(copy);
                        slot.onSlotChanged();

                        stack.stackSize -= copy.stackSize;
                        changed = true;

                        if (stack.stackSize <= 0) {
                            break;
                        }
                    }
                }

                index = nextIndex(index, reverseDirection);
            }
        }

        cir.setReturnValue(changed);
    }

    private boolean isIndexInRange(int index, int startIndex, int endIndex, boolean reverseDirection) {
        if (reverseDirection) {
            return index >= startIndex;
        }

        return index < endIndex;
    }

    private int nextIndex(int index, boolean reverseDirection) {
        if (reverseDirection) {
            return index - 1;
        }

        return index + 1;
    }

    private boolean canMerge(ItemStack current, ItemStack incoming) {
        if (current == null || incoming == null) {
            return false;
        }

        if (current.getItem() != incoming.getItem()) {
            return false;
        }

        if (!current.isStackable()) {
            return false;
        }

        if (current.getHasSubtypes() && current.getItemDamage() != incoming.getItemDamage()) {
            return false;
        }

        if (!ItemStack.areItemStackTagsEqual(current, incoming)) {
            return false;
        }

        return true;
    }

}
