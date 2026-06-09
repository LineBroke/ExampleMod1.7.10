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
     * 1.7.10 GuiContainer 的核心 slot 点击处理方法。
     *
     * 参数含义大概是：
     * slot = 当前操作的格子
     * slotId = container slot id
     * mouseButton = 鼠标按钮 / 数字键编号
     * clickType = 点击类型
     *
     * clickType 常见值：
     * 0 = 普通点击
     * 1 = shift-click
     * 2 = 数字键换位 hotbar swap
     * 3 = creative middle click
     * 4 = Q 丢弃
     * 5 = 拖拽
     */
    @Inject(method = "handleMouseClick", at = @At("HEAD"), cancellable = true)
    private void mymodid$lockSlot(Slot slot, int slotId, int mouseButton, int clickType, CallbackInfo ci) {
        boolean ctrlDown = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);

        // CTRL + 左键：切换锁定状态
        if (ctrlDown && mouseButton == 0 && clickType == 0 && SlotLockManager.isPlayerInventorySlot(slot)) {
            SlotLockManager.toggle(slot);
            ci.cancel();
            return;
        }

        // 如果直接点到了被锁格子，阻止
        if (SlotLockManager.isLocked(slot)) {
            ci.cancel();
            return;
        }

        // 数字键换位：
        // clickType == 2 时，mouseButton 通常是 hotbar index，范围 0-8
        // 如果目标快捷栏格子被锁，也阻止
        if (clickType == 2 && mouseButton >= 0 && mouseButton <= 8) {
            if (SlotLockManager.isLockedPlayerIndex(mouseButton)) {
                ci.cancel();
                return;
            }
        }

        // shift-click 有可能把物品自动塞进被锁的空格子。
        // 第一版为了安全：只要有锁定格子，就禁用 shift-click。
        if (clickType == 1 && SlotLockManager.hasAnyLock()) {
            ci.cancel();
            return;
        }

        // 拖拽也可能经过锁定格子。
        // 第一版为了安全：有锁定格子时禁用拖拽分配。
        if (clickType == 5 && SlotLockManager.hasAnyLock()) {
            ci.cancel();
        }
    }
}
