package com.slotlock.slotlock;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class SlotLockDebug {

    public static final boolean ENABLED = true;

    private SlotLockDebug() {}

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static void log(String message) {
        if (!ENABLED) {
            return;
        }

        MyMod.LOG.info("[SlotLock DEBUG] " + message);
    }

    public static void dumpCurrentContainer(EntityPlayer player, Container container, Object currentScreen,
        String reason) {
        if (!ENABLED) {
            return;
        }

        if (player == null) {
            log("dump skipped: player is null | reason=" + reason);
            return;
        }

        if (container == null) {
            log("dump skipped: container is null | reason=" + reason);
            return;
        }

        String screenClass = currentScreen == null ? "null"
            : currentScreen.getClass()
                .getName();
        String containerClass = container.getClass()
            .getName();

        log("===== Container dump START =====");
        log("reason=" + reason);
        log("screenClass=" + screenClass);
        log("containerClass=" + containerClass);
        log("lockedSlots=" + SlotLockManager.getLockedSlots());
        log("inventorySlots.size=" + (container.inventorySlots == null ? -1 : container.inventorySlots.size()));

        dumpPlayerInventory(player);

        if (container.inventorySlots != null) {
            dumpContainerSlots(container);
        }

        log("===== Container dump END =====");
    }

    public static void dumpPlayerInventory(EntityPlayer player) {
        if (!ENABLED || player == null || player.inventory == null) {
            return;
        }

        log("----- Player inventory 0-35 -----");

        for (int i = 0; i <= 35; i++) {
            boolean locked = SlotLockManager.isLockedPlayerIndex(i);
            ItemStack stack = player.inventory.mainInventory[i];

            if (!locked && stack == null) {
                continue;
            }

            log("playerIndex=" + i + ", locked=" + locked + ", stack=" + getStackText(stack));
        }

        ItemStack cursor = player.inventory.getItemStack();

        log("cursorStack=" + getStackText(cursor));
        log("----- End player inventory -----");
    }

    public static void dumpContainerSlots(Container container) {
        if (!ENABLED || container == null || container.inventorySlots == null) {
            return;
        }

        log("----- Container slots -----");

        List<?> slots = container.inventorySlots;

        for (int listIndex = 0; listIndex < slots.size(); listIndex++) {
            Object object = slots.get(listIndex);

            if (!(object instanceof Slot)) {
                log(
                    "slotListIndex=" + listIndex
                        + ", objectClass="
                        + object.getClass()
                            .getName());
                continue;
            }

            Slot slot = (Slot) object;

            int playerIndex = SlotLockManager.getPlayerSlotIndex(slot);
            boolean isPlayerSlot = playerIndex >= 0 && playerIndex <= 35;
            boolean locked = SlotLockManager.isLocked(slot);

            /*
             * 为了少刷屏：
             * - 玩家背包槽一定打印
             * - 锁定槽一定打印
             * - 可疑的 CraftingStation / TConstruct 槽也打印
             */
            if (!isPlayerSlot && !locked && !isSuspiciousSlot(slot)) {
                continue;
            }

            ItemStack stack = null;

            try {
                stack = slot.getStack();
            } catch (Throwable t) {
                log("slotListIndex=" + listIndex + " getStack failed: " + t);
            }

            log(
                "slotListIndex=" + listIndex
                    + ", slotNumber="
                    + slot.slotNumber
                    + ", slotIndex="
                    + safeSlotIndex(slot)
                    + ", playerIndex="
                    + playerIndex
                    + ", locked="
                    + locked
                    + ", x="
                    + slot.xDisplayPosition
                    + ", y="
                    + slot.yDisplayPosition
                    + ", slotClass="
                    + slot.getClass()
                        .getName()
                    + ", invClass="
                    + getInventoryClass(slot)
                    + ", stack="
                    + getStackText(stack));
        }

        log("----- End container slots -----");
    }

    private static boolean isSuspiciousSlot(Slot slot) {
        if (slot == null) {
            return false;
        }

        String slotClass = slot.getClass()
            .getName();
        String invClass = getInventoryClass(slot);

        return slotClass.contains("Craft") || slotClass.contains("Station")
            || slotClass.contains("tconstruct")
            || slotClass.contains("TConstruct")
            || invClass.contains("Craft")
            || invClass.contains("Station")
            || invClass.contains("tconstruct")
            || invClass.contains("TConstruct");
    }

    private static int safeSlotIndex(Slot slot) {
        if (slot == null) {
            return -999;
        }

        try {
            return slot.getSlotIndex();
        } catch (Throwable ignored) {
            return -999;
        }
    }

    private static String getInventoryClass(Slot slot) {
        if (slot == null || slot.inventory == null) {
            return "null";
        }

        return slot.inventory.getClass()
            .getName();
    }

    public static String getStackText(ItemStack stack) {
        if (stack == null) {
            return "null";
        }

        String itemName = "unknown";

        try {
            Item item = stack.getItem();
            itemName = String.valueOf(item);
        } catch (Throwable ignored) {}

        return itemName + " x" + stack.stackSize + " dmg=" + stack.getItemDamage() + " tag=" + getTagText(stack);
    }

    public static String getStackKey(ItemStack stack) {
        if (stack == null) {
            return "null";
        }

        int itemId = -1;

        try {
            itemId = Item.getIdFromItem(stack.getItem());
        } catch (Throwable ignored) {}

        String tagText = getTagText(stack);

        return itemId + ":" + stack.getItemDamage() + ":" + stack.stackSize + ":" + tagText;
    }

    private static String getTagText(ItemStack stack) {
        if (stack == null || stack.stackTagCompound == null) {
            return "noTag";
        }

        try {
            return String.valueOf(stack.stackTagCompound)
                .replace('\n', ' ');
        } catch (Throwable ignored) {
            return "tagError";
        }
    }
}
