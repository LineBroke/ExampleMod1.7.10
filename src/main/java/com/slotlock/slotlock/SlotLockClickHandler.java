package com.slotlock.slotlock;

import net.minecraft.inventory.Slot;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class SlotLockClickHandler {

    private SlotLockClickHandler() {}

    public static boolean handleSlotClick(Slot slot, int mouseButton, int clickType) {

        /*
         * Ctrl + 左键：锁定 / 解锁玩家背包槽。
         * 这个逻辑必须放在 Bogo guard 前面，
         * 否则快速 Alt+左键后的保护时间内可能无法锁定/解锁。
         */
        if (isLockKeyDown() && mouseButton == 0
            && clickType == 0
            && slot != null
            && SlotLockManager.isPlayerInventorySlot(slot)) {

            SlotLockManager.toggle(slot);
            return true;
        }

        /*
         * BogoSorter shortcut 保护：
         * 防止快速 Alt+左键后，shortcut 没有完整消费点击，
         * 导致原版左键继续执行，把一组物品拿到鼠标指针上。
         */
        if (SlotLockBogoShortcutGuard.shouldBlockVanillaClick(mouseButton, clickType)) {
            return true;
        }

        /*
         * clickType == 5 是拖拽分配物品。
         * 不能直接禁止整个 clickType 5，
         * 否则左键拖拽均分、右键拖拽逐个放置都会失效。
         * 这里只在“拖拽经过锁定槽并尝试添加这个槽”时拦截。
         */
        if (clickType == 5) {
            return shouldCancelDragSlot(slot, mouseButton);
        }

        if (slot == null) {
            return false;
        }

        /*
         * 普通点击锁定槽：禁止。
         */
        if (SlotLockManager.isLocked(slot)) {
            return true;
        }

        /*
         * 数字键换位到锁定 hotbar：禁止。
         * clickType == 2 是 hotbar swap。
         * mouseButton 0-8 对应快捷栏 1-9。
         */
        if (clickType == 2 && mouseButton >= 0 && mouseButton <= 8) {
            if (SlotLockManager.isLockedPlayerIndex(mouseButton)) {
                return true;
            }
        }

        /*
         * 双击收集：
         * 有锁定槽时禁止，避免把锁定槽也卷入收集逻辑。
         */
        if (clickType == 6 && SlotLockManager.hasAnyLock()) {
            return true;
        }

        return false;
    }

    private static boolean shouldCancelDragSlot(Slot slot, int mouseButton) {
        if (slot == null) {
            return false;
        }

        /*
         * Container drag click 编码：
         * event = mouseButton >> 2 & 3
         * 0 = 开始拖拽
         * 1 = 添加经过的槽
         * 2 = 结束拖拽
         */
        int dragEvent = (mouseButton >> 2) & 3;

        /*
         * 只在“添加经过的槽”阶段拦截锁定槽。
         * 开始和结束阶段不能拦，否则整个拖拽流程会坏掉。
         */
        if (dragEvent != 1) {
            return false;
        }

        return SlotLockManager.isLocked(slot);
    }

    private static boolean isLockKeyDown() {
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
