package com.slotlock.slotlock.mixin;

import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.slotlock.slotlock.SlotLockManager;

import codechicken.nei.NEIClientUtils;
import codechicken.nei.PanelWidget;

@Mixin(value = PanelWidget.class, remap = false)
public abstract class MixinNEIPanelWidget {

    /**
     * NEI drag path:
     *
     * PanelWidget.handleDraggedClick(...)
     * -> handleGUIContainerClick(...)
     * -> GuiInfo.guiHandlers
     * -> handler.handleDragNDrop(...)
     *
     * This path can bypass normal Container.slotClick protection.
     */
    @Inject(method = "handleGUIContainerClick", at = @At("HEAD"), cancellable = true, remap = false)
    private void slotlock$blockNEIDragIntoLockedSlot(ItemStack draggedStack, int mouseX, int mouseY, int button,
        CallbackInfoReturnable<Boolean> cir) {
        if (draggedStack == null) {
            return;
        }

        GuiContainer gui = NEIClientUtils.getGuiContainer();

        if (gui == null) {
            return;
        }

        Slot slot = slotlock$getSlotAtPosition(gui, mouseX, mouseY);

        if (slot == null) {
            return;
        }

        if (SlotLockManager.isLocked(slot)) {
            /*
             * true = tell NEI this drag event has been handled.
             * Do NOT set draggedStack.stackSize = 0.
             * Keeping stackSize non-zero makes NEI keep the dragged stack on cursor,
             * so the player can still place it into an unlocked slot.
             */
            cir.setReturnValue(Boolean.TRUE);
        }
    }

    @Unique
    private Slot slotlock$getSlotAtPosition(GuiContainer gui, int mouseX, int mouseY) {
        if (gui == null) {
            return null;
        }

        Container container = gui.inventorySlots;

        if (container == null || container.inventorySlots == null) {
            return null;
        }

        int guiLeft = slotlock$getIntField(gui, 0, "guiLeft", "field_147003_i");
        int guiTop = slotlock$getIntField(gui, 0, "guiTop", "field_147009_r");

        List<?> slots = container.inventorySlots;

        /*
         * Vanilla GuiContainer#getSlotAtPosition checks all slots.
         * We reimplement it because that method is private in MC 1.7.10.
         */
        for (Object object : slots) {
            if (!(object instanceof Slot)) {
                continue;
            }

            Slot slot = (Slot) object;

            if (slotlock$isMouseOverSlot(slot, guiLeft, guiTop, mouseX, mouseY)) {
                return slot;
            }
        }

        return null;
    }

    @Unique
    private boolean slotlock$isMouseOverSlot(Slot slot, int guiLeft, int guiTop, int mouseX, int mouseY) {
        int slotX = guiLeft + slot.xDisplayPosition;
        int slotY = guiTop + slot.yDisplayPosition;

        return mouseX >= slotX - 1 && mouseX < slotX + 17 && mouseY >= slotY - 1 && mouseY < slotY + 17;
    }

    @Unique
    private int slotlock$getIntField(Object target, int fallback, String... names) {
        if (target == null || names == null) {
            return fallback;
        }

        Class<?> clazz = target.getClass();

        while (clazz != null) {
            for (String name : names) {
                try {
                    Field field = clazz.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.getInt(target);
                } catch (Throwable ignored) {}
            }

            clazz = clazz.getSuperclass();
        }

        return fallback;
    }
}
