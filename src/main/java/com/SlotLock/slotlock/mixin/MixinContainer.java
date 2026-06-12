package com.SlotLock.slotlock.mixin;

import java.util.List;

import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.SlotLock.slotlock.SlotLockManager;

@Mixin(Container.class)
public abstract class MixinContainer {

    /**
     * A full dummy stack.
     *
     * Important:
     * Do NOT return null from DUMMY_LOCKED_SLOT.getStack().
     *
     * Vanilla 1.7.10 Container.mergeItemStack treats getStack() == null as an empty slot.
     * If the dummy slot looks empty, vanilla may call putStack(copy) and then set the
     * source stack size to 0, causing the shifted item to disappear.
     *
     * So the dummy locked slot must look occupied and full.
     */
    private static final ItemStack DUMMY_LOCKED_STACK = new ItemStack(Blocks.bedrock, 64, 0);

    /**
     * Fake locked slot.
     *
     * This slot is returned only inside Container.mergeItemStack when vanilla tries
     * to access a locked player inventory slot.
     *
     * Behavior:
     * 1. getStack() returns a full non-null stack, so vanilla does not treat it as empty.
     * 2. Because the dummy stack is full, vanilla will not merge into it.
     * 3. isItemValid() returns false.
     * 4. putStack() does nothing, just in case.
     */
    private static final Slot DUMMY_LOCKED_SLOT = new Slot(new InventoryBasic("slotlock_dummy", false, 1), 0, 0, 0) {

        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;
        }

        @Override
        public ItemStack getStack() {
            return DUMMY_LOCKED_STACK.copy();
        }

        @Override
        public boolean getHasStack() {
            return true;
        }

        @Override
        public void putStack(ItemStack stack) {}

        @Override
        public int getSlotStackLimit() {
            return 0;
        }

        @Override
        public boolean canTakeStack(net.minecraft.entity.player.EntityPlayer player) {
            return false;
        }
    };

    /**
     * Redirect Container.mergeItemStack's List.get(index).
     *
     * If vanilla tries to access a locked player inventory slot while shift-clicking,
     * return the dummy slot instead.
     *
     * This lets vanilla keep its original mergeItemStack logic, but makes locked slots
     * look like unusable full slots.
     */
    @Redirect(
        method = "mergeItemStack",
        at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;"))
    private Object slotlock$redirectSlotGet(List<?> list, int index) {
        Object obj = list.get(index);

        if (obj instanceof Slot) {
            Slot slot = (Slot) obj;

            if (SlotLockManager.isLocked(slot)) {
                return DUMMY_LOCKED_SLOT;
            }
        }

        return obj;
    }

}
