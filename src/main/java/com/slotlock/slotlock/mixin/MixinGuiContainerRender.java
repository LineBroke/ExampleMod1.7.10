package com.slotlock.slotlock.mixin;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.slotlock.slotlock.SlotLockManager;
import com.slotlock.slotlock.SlotLockOverlayHandler;

@Mixin(GuiContainer.class)
public abstract class MixinGuiContainerRender {

    @Inject(method = "func_146977_a(Lnet/minecraft/inventory/Slot;)V", at = @At("HEAD"))
    private void slotlock$drawLockedBackground(Slot slot, CallbackInfo ci) {
        if (slot == null) {
            return;
        }

        if (!SlotLockManager.isLocked(slot)) {
            return;
        }

        SlotLockOverlayHandler.drawLockedBackground(slot.xDisplayPosition, slot.yDisplayPosition);
    }

    @Inject(method = "func_146977_a(Lnet/minecraft/inventory/Slot;)V", at = @At("TAIL"))
    private void slotlock$drawLockIcon(Slot slot, CallbackInfo ci) {
        if (slot == null) {
            return;
        }

        if (!SlotLockManager.isLocked(slot)) {
            return;
        }

        SlotLockOverlayHandler.drawLockedIcon(slot.xDisplayPosition, slot.yDisplayPosition);
    }
}
