package com.SlotLock.slotlock.mixin;

import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.inventory.Slot;

import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.SlotLock.slotlock.SlotLockManager;

@Mixin(GuiContainerCreative.class)
public abstract class MixinGuiContainerCreative {

    @Inject(method = "handleMouseClick", at = @At("HEAD"), cancellable = true)
    private void slotlock$lockCreativeSlot(Slot slot, int slotId, int mouseButton, int clickType, CallbackInfo ci) {
        boolean ctrlDown = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);

        if (ctrlDown && mouseButton == 0 && clickType == 0 && SlotLockManager.isPlayerInventorySlot(slot)) {
            SlotLockManager.toggle(slot);
            ci.cancel();
            return;
        }

        if (SlotLockManager.isLocked(slot)) {
            ci.cancel();
            return;
        }

        // number key swap
        if (clickType == 2 && mouseButton >= 0 && mouseButton <= 8) {
            if (SlotLockManager.isLockedPlayerIndex(mouseButton)) {
                ci.cancel();
                return;
            }
        }

        // shift click
        if (clickType == 1 && SlotLockManager.hasAnyLock()) {
            ci.cancel();
            return;
        }

        // drag
        if (clickType == 5 && SlotLockManager.hasAnyLock()) {
            ci.cancel();
        }
    }
}
