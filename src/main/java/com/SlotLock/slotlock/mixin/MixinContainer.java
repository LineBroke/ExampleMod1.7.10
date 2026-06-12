package com.slotlock.slotlock.mixin;

import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.slotlock.slotlock.SlotLockManager;

@Mixin(Container.class)
public abstract class MixinContainer {

    /**
     * A full dummy stack.
     *
     * Do NOT return null from the dummy slot.
     * Vanilla mergeItemStack treats null as an empty slot.
     */
    @Unique
    private static final ItemStack slotlock$dummyLockedStack;

    static {
        slotlock$dummyLockedStack = new ItemStack(Items.stick, 64);

        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("SlotLockDummy", true);
        slotlock$dummyLockedStack.setTagCompound(tag);
    }

    /**
     * Fake locked slot.
     *
     * It looks occupied and full, so vanilla mergeItemStack will skip it.
     */
    @Unique
    private static final Slot slotlock$dummyLockedSlot = new Slot(
        new InventoryBasic("slotlock_dummy", false, 1),
        0,
        0,
        0) {

        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;
        }

        @Override
        public ItemStack getStack() {
            return slotlock$dummyLockedStack.copy();
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
     */
    @Redirect(
        method = "mergeItemStack",
        at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;"))
    private Object slotlock$redirectSlotGet(List<?> list, int index) {
        Object obj = list.get(index);

        if (!(obj instanceof Slot)) {
            return obj;
        }

        Slot slot = (Slot) obj;

        if (SlotLockManager.isLocked(slot)) {
            return slotlock$dummyLockedSlot;
        }

        return obj;
    }
}
