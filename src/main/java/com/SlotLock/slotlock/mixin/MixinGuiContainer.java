package com.SlotLock.slotlock.mixin;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;

import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.SlotLock.slotlock.SlotLockManager;

@Mixin(GuiContainer.class)
public abstract class MixinGuiContainer {

    /**
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
    private void mymodid$lockSlot(Slot slot, int slotId, int mouseButton, int clickType, CallbackInfo ci) {
        boolean ctrlDown = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean altDown = Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);

        // Windows/Linux: Ctrl + 左键
        // Mac: Option/Alt + 左键
        boolean lockKeyDown = ctrlDown || altDown;

        // 切换锁定状态
        if (lockKeyDown && mouseButton == 0
            && clickType == 0
            && slot != null
            && SlotLockManager.isPlayerInventorySlot(slot)) {
            SlotLockManager.toggle(slot);
            ci.cancel();
            return;
        }

        // 点空白处，放行
        if (slot == null) {
            return;
        }

        // 直接操作锁定格：阻止
        if (SlotLockManager.isLocked(slot)) {
            ci.cancel();
            return;
        }

        // 数字键 hotbar 交换：
        // clickType == 2 时，mouseButton 是快捷栏 index，范围 0-8
        // 如果目标 hotbar 被锁，阻止交换
        if (clickType == 2 && mouseButton >= 0 && mouseButton <= 8) {
            if (SlotLockManager.isLockedPlayerIndex(mouseButton)) {
                ci.cancel();
                return;
            }
        }

        // 不再全局禁用 Shift-click。
        // Shift-click 自动跳过锁定格的逻辑交给 MixinContainer.mergeItemStack 处理。

        // 拖拽：
        // 为了安全，有锁定格时先禁用拖拽分配。
        if (clickType == 5 && SlotLockManager.hasAnyLock()) {
            ci.cancel();
            return;
        }

        // 双击收集同类物品：
        // 防止把锁定格里的同类物品收走。
        if (clickType == 6 && SlotLockManager.hasAnyLock()) {
            ci.cancel();
            return;
        }
    }

}
