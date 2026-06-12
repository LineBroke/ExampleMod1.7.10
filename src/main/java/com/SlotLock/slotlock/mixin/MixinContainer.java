package com.slotlock.slotlock.mixin;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.slotlock.slotlock.SlotLockManager;

@Mixin(Container.class)
public abstract class MixinContainer {

    @Shadow
    public List inventorySlots;

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
        public boolean canTakeStack(EntityPlayer player) {
            return false;
        }
    };

    /**
     * Protect locked slots at Container.slotClick level.
     *
     * Mouse Tweaks can bypass GuiContainer.handleMouseClick and trigger quick moves directly.
     * So we must also protect locked slots here.
     *
     * Important:
     * Do NOT block mode == 0 here.
     * SlotLockAutoMover uses normal left-click windowClick to move unexpected items
     * out of empty locked slots.
     */
    @Inject(method = "slotClick", at = @At("HEAD"), cancellable = true)
    private void slotlock$preventLockedSlotQuickActions(int slotId, int mouseButton, int clickType, EntityPlayer player,
        CallbackInfoReturnable<ItemStack> cir) {
        if (!SlotLockManager.hasAnyLock()) {
            return;
        }

        /*
         * Number-key swap:
         * clickType == 2
         * mouseButton 0-8 means hotbar slot 0-8.
         * If the target hotbar slot is locked, block the swap.
         */
        if (clickType == 2 && mouseButton >= 0 && mouseButton <= 8) {
            if (SlotLockManager.isLockedPlayerIndex(mouseButton)) {
                cir.setReturnValue(null);
                return;
            }
        }

        /*
         * Double-click collect.
         * If there is any locked slot, block it to avoid collecting from locked slots.
         */
        if (clickType == 6) {
            cir.setReturnValue(null);
            return;
        }

        Slot slot = slotlock$getSlotById(slotId);

        if (slot == null) {
            return;
        }

        if (!SlotLockManager.isLocked(slot)) {
            return;
        }

        /*
         * Block locked slot as source for quick actions.
         * clickType meanings in 1.7.10:
         * 1 = shift-click / quick move
         * 2 = number-key swap
         * 4 = Q drop
         * 5 = drag
         * 6 = double-click collect
         * Mouse Tweaks shift-left-drag and wheel movement usually behave like quick move.
         */
        if (clickType == 1 || clickType == 2 || clickType == 4 || clickType == 5 || clickType == 6) {

            cir.setReturnValue(null);
        }
    }

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

    @Unique
    private Slot slotlock$getSlotById(int slotId) {
        if (slotId < 0) {
            return null;
        }

        if (this.inventorySlots == null) {
            return null;
        }

        if (slotId >= this.inventorySlots.size()) {
            return null;
        }

        Object object = this.inventorySlots.get(slotId);

        if (!(object instanceof Slot)) {
            return null;
        }

        return (Slot) object;
    }
}
