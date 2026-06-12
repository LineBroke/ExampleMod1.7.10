package com.slotlock.slotlock;

public final class SlotLockBogoShortcutGuard {

    private static long blockVanillaClickUntil = 0L;

    private SlotLockBogoShortcutGuard() {}

    public static void blockVanillaClickFor(long milliseconds) {
        long until = System.currentTimeMillis() + milliseconds;

        if (until > blockVanillaClickUntil) {
            blockVanillaClickUntil = until;
        }
    }

    public static boolean shouldBlockVanillaClick(int mouseButton, int clickType) {
        if (!SlotLockManager.hasAnyLock()) {
            return false;
        }

        if (System.currentTimeMillis() > blockVanillaClickUntil) {
            return false;
        }

        /*
         * clickType 0 + mouseButton 0 = 普通左键点击。
         * 目标：
         * 防止 BogoSorter Alt+左键 shortcut 失败后，
         * 退回成原版左键，把一组物品拿到鼠标上。
         */
        return clickType == 0 && mouseButton == 0;
    }
}
