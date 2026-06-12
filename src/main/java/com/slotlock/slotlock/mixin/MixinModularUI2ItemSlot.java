package com.slotlock.slotlock.mixin;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.slotlock.slotlock.ClientProxy;
import com.slotlock.slotlock.SlotLockManager;
import com.slotlock.slotlock.SlotLockOverlayHandler;

@Mixin(value = ItemSlot.class, remap = false)
public abstract class MixinModularUI2ItemSlot {

    @Shadow
    public abstract ModularSlot getSlot();

    @Inject(method = "draw", at = @At("HEAD"), remap = false)
    private void slotlock$drawLockedBackground(ModularGuiContext context, WidgetThemeEntry widgetTheme,
        CallbackInfo ci) {
        ModularSlot slot = getSlot();

        if (SlotLockManager.isLocked(slot)) {
            SlotLockOverlayHandler.drawLockedBackground(1, 1);
        }
    }

    @Inject(method = "draw", at = @At("TAIL"), remap = false)
    private void slotlock$drawLockedIcon(ModularGuiContext context, WidgetThemeEntry widgetTheme, CallbackInfo ci) {
        ModularSlot slot = getSlot();

        if (SlotLockManager.isLocked(slot)) {
            SlotLockOverlayHandler.drawLockedIcon(1, 1);
        }
    }

    @Inject(method = "onMousePressed", at = @At("HEAD"), cancellable = true, remap = false)
    private void slotlock$handleLockedSlotMousePressed(int mouseButton,
        CallbackInfoReturnable<Interactable.Result> cir) {
        ModularSlot slot = getSlot();

        if (!SlotLockManager.isPlayerInventorySlot(slot)) {
            return;
        }

        if (slotlock$isLockKeyDown() && mouseButton == 0) {
            SlotLockManager.toggle(slot);
            cir.setReturnValue(Interactable.Result.SUCCESS);
            return;
        }

        if (SlotLockManager.isLocked(slot)) {
            cir.setReturnValue(Interactable.Result.SUCCESS);
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true, remap = false)
    private void slotlock$preventScrollOnLockedSlot(UpOrDown scrollDirection, int amount,
        CallbackInfoReturnable<Boolean> cir) {
        ModularSlot slot = getSlot();

        if (SlotLockManager.isLocked(slot)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }

    private static boolean slotlock$isLockKeyDown() {
        if (ClientProxy.lockKey == null) {
            return false;
        }

        int keyCode = ClientProxy.lockKey.getKeyCode();

        if (keyCode >= 0) {
            return Keyboard.isKeyDown(keyCode);
        }

        return Mouse.isButtonDown(keyCode + 100);
    }
}
