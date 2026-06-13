package com.slotlock.slotlock;

import net.minecraft.inventory.Slot;

public final class SlotLockInternalBypass {

    private static volatile int allowedPlayerIndex = -1;
    private static volatile long allowUntilTime = 0L;

    private SlotLockInternalBypass() {}

    public static void allowPlayerIndex(int playerIndex, long durationMillis) {
        if (playerIndex < 0 || playerIndex > 35) {
            return;
        }

        allowedPlayerIndex = playerIndex;
        allowUntilTime = System.currentTimeMillis() + durationMillis;
    }

    public static boolean isAllowed(Slot slot) {
        if (slot == null) {
            return false;
        }

        long now = System.currentTimeMillis();

        if (now > allowUntilTime) {
            clear();
            return false;
        }

        int playerIndex = SlotLockManager.getPlayerSlotIndex(slot);

        return playerIndex == allowedPlayerIndex;
    }

    public static void clear() {
        allowedPlayerIndex = -1;
        allowUntilTime = 0L;
    }
}
