package com.slotlock.slotlock.mixin;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.slotlock.slotlock.SlotLockClickHandler;

@Mixin(GuiContainer.class)
public abstract class MixinGuiContainer {

    /**
     * 1.7.10 GuiContainer 的核心 slot 点击处理方法。
     *
     * clickType 常见值：
     * 0 = 普通点击
     * 1 = shift-click
     * 2 = 数字键换位 hotbar swap
     * 3 = creative middle click
     * 4 = Q 丢弃
     * 5 = 拖拽
     * 6 = 双击收集同类物品
     */
    @Inject(method = "handleMouseClick", at = @At("HEAD"), cancellable = true)
    private void slotlock$handleMouseClick(Slot slot, int slotId, int mouseButton, int clickType, CallbackInfo ci) {
        if (SlotLockClickHandler.handleSlotClick(slot, mouseButton, clickType)) {
            ci.cancel();
        }
    }
}
