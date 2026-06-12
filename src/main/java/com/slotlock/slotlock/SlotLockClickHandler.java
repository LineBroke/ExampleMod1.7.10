package com.slotlock.slotlock;

import net.minecraft.inventory.Slot;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class SlotLockClickHandler {

    private SlotLockClickHandler() {}

    /**
     * 返回 true = 本次点击应该被取消。
     * 返回 false = 放行原版逻辑。
     */
    public static boolean handleSlotClick(Slot slot, int mouseButton, int clickType) {

        // 1. 锁定键 + 左键：切换锁定状态
        if (isLockKeyDown() && mouseButton == 0
            && clickType == 0
            && slot != null
            && SlotLockManager.isPlayerInventorySlot(slot)) {

            SlotLockManager.toggle(slot);
            return true;
        }

        // 点击空白处，放行原版逻辑
        if (slot == null) {
            return false;
        }

        // 2. 直接点击被锁定的槽，阻止
        if (SlotLockManager.isLocked(slot)) {
            return true;
        }

        // 3. 数字键 hotbar swap
        // clickType == 2 时，mouseButton 是快捷栏 index，范围 0-8
        if (clickType == 2 && mouseButton >= 0 && mouseButton <= 8) {
            if (SlotLockManager.isLockedPlayerIndex(mouseButton)) {
                return true;
            }
        }

        // 4. 双击收集同类物品
        // 防止双击把锁定格里的同类物品收走
        if (clickType == 6 && SlotLockManager.hasAnyLock()) {
            return true;
        }

        // 5. 拖拽分配
        // 防止拖拽时物品被放进锁定格
        if (clickType == 5 && SlotLockManager.hasAnyLock()) {
            return true;
        }

        return false;
    }

    private static boolean isLockKeyDown() {
        if (ClientProxy.lockKey == null) {
            return false;
        }

        int keyCode = ClientProxy.lockKey.getKeyCode();

        // 1.7.10 中，keyCode < 0 表示鼠标按键
        if (keyCode >= 0) {
            return Keyboard.isKeyDown(keyCode);
        }

        return Mouse.isButtonDown(keyCode + 100);
    }
}
