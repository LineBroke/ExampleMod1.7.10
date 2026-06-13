package com.slotlock.slotlock;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public final class SlotLockDummyHelper {

    private static final ItemStack DUMMY_STACK;
    private static final Slot DUMMY_SLOT;

    static {
        DUMMY_STACK = new ItemStack(Items.stick, 64);

        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("SlotLockDummy", true);
        DUMMY_STACK.setTagCompound(tag);

        DUMMY_SLOT = new Slot(new InventoryBasic("slotlock_dummy", false, 1), 0, 0, 0) {

            @Override
            public boolean isItemValid(ItemStack stack) {
                return false;
            }

            @Override
            public ItemStack getStack() {
                return DUMMY_STACK.copy();
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
            public boolean canTakeStack(EntityPlayer player) {
                return false;
            }

            @Override
            public ItemStack decrStackSize(int amount) {
                return null;
            }
        };
    }

    private SlotLockDummyHelper() {}

    public static ItemStack getDummyStack() {
        return DUMMY_STACK.copy();
    }

    public static Slot getDummySlot() {
        return DUMMY_SLOT;
    }
}
