package com.slotlock.slotlock.mixin;

import net.minecraft.client.gui.GuiIngame;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.slotlock.slotlock.SlotLockManager;
import com.slotlock.slotlock.SlotLockOverlayHandler;

@Mixin(GuiIngame.class)
public abstract class MixinGuiIngame {

    /**
     * hotbar 每个格子绘制前，先画淡绿色背景。
     */
    @Inject(method = "renderInventorySlot(IIIF)V", at = @At("HEAD"))
    private void slotlock$drawHotbarBackground(int slotIndex, int x, int y, float partialTicks, CallbackInfo ci) {
        if (!SlotLockManager.isLockedPlayerIndex(slotIndex)) {
            return;
        }

        SlotLockOverlayHandler.drawLockedBackground(x, y);
    }

    /**
     * hotbar 每个格子绘制后，再画右上角小锁。
     */
    @Inject(method = "renderInventorySlot(IIIF)V", at = @At("TAIL"))
    private void slotlock$drawHotbarIcon(int slotIndex, int x, int y, float partialTicks, CallbackInfo ci) {
        if (!SlotLockManager.isLockedPlayerIndex(slotIndex)) {
            return;
        }

        SlotLockOverlayHandler.drawLockedIcon(x, y);
    }
}
