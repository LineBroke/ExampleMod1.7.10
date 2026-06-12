package com.slotlock.slotlock.mixin;

import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.slotlock.slotlock.SlotLockClickHandler;

@Mixin(GuiContainerCreative.class)
public abstract class MixinGuiContainerCreative {

    /**
     * Creative inventory has its own GuiContainerCreative class.
     * We still use the shared SlotLockClickHandler to avoid duplicated click logic.
     */
    @Inject(method = "handleMouseClick", at = @At("HEAD"), cancellable = true)
    private void slotlock$handleCreativeMouseClick(Slot slot, int slotId, int mouseButton, int clickType,
        CallbackInfo ci) {
        if (SlotLockClickHandler.handleSlotClick(slot, mouseButton, clickType)) {
            ci.cancel();
        }
    }
}
