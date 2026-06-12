package com.slotlock.slotlock.mixin;

import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.inventory.Slot;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.slotlock.slotlock.ClientProxy;
import com.slotlock.slotlock.SlotLockManager;

@Mixin(GuiContainerCreative.class)
public abstract class MixinGuiContainerCreative {

    /**
     * Creative inventory has its own GuiContainerCreative class.
     * We inject at HEAD to intercept mouse clicks using the dynamic KeyBinding.
     */
    @Inject(method = "handleMouseClick", at = @At("HEAD"), cancellable = true)
    private void mymodid$lockCreativeSlot(Slot slot, int slotId, int mouseButton, int clickType, CallbackInfo ci) {

        // Dynamically get the key code configured by the player in the options menu
        int keyCode = ClientProxy.lockKey.getKeyCode();
        boolean lockKeyDown = false;

        // In LWJGL/Minecraft 1.7.10, key codes < 0 represent mouse buttons (e.g., button 4, button 5)
        // Real mouse button index = keyCode + 100
        if (keyCode >= 0) {
            lockKeyDown = Keyboard.isKeyDown(keyCode);
        } else {
            lockKeyDown = Mouse.isButtonDown(keyCode + 100);
        }

        // 1. Toggle lock state via custom keybind + Left Click
        if (lockKeyDown && mouseButton == 0
            && clickType == 0
            && slot != null
            && SlotLockManager.isPlayerInventorySlot(slot)) {
            SlotLockManager.toggle(slot);
            ci.cancel();
            return;
        }

        // Allow vanilla logic to handle clicks outside slots
        if (slot == null) {
            return;
        }

        // 2. Prevent direct interaction with locked slots
        if (SlotLockManager.isLocked(slot)) {
            ci.cancel();
            return;
        }

        // 3. Hotbar swap (1-9 keys):
        // Prevent swapping if the target hotbar slot is locked
        if (clickType == 2 && mouseButton >= 0 && mouseButton <= 8) {
            if (SlotLockManager.isLockedPlayerIndex(mouseButton)) {
                ci.cancel();
                return;
            }
        }

        // 4. Drag mechanics:
        // Disable drag distribution globally when locks are present to ensure security
        if (clickType == 5 && SlotLockManager.hasAnyLock()) {
            ci.cancel();
            return;
        }

        // 5. Double click (Pick up all matching items):
        // Prevent drawing items out of locked slots
        if (clickType == 6 && SlotLockManager.hasAnyLock()) {
            ci.cancel();
            return;
        }
    }
}
