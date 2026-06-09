package com.SlotLock.slotlock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;

public class SlotLockManager {

    /**
     * InventoryPlayer index:
     * 0-8 = hotbar
     * 9-35 = main inventory
     */
    private static final Set<Integer> LOCKED_PLAYER_SLOTS = new HashSet<Integer>();

    private static File saveFile;

    public static void setSaveFile(File configDir) {
        saveFile = new File(configDir, "slotlock_locked_slots.cfg");

        MyMod.LOG.info("SlotLock save file: " + saveFile.getAbsolutePath());

        load();
    }

    public static Slot unwrapSlot(Slot slot) {
        if (slot == null) {
            return null;
        }

        // Creative inventory wraps player slots inside GuiContainerCreative.CreativeSlot.
        // We unwrap it by reflection so creative mode player inventory/hotbar can be locked too.
        if (slot.getClass()
            .getName()
            .equals("net.minecraft.client.gui.inventory.GuiContainerCreative$CreativeSlot")) {
            Field[] fields = slot.getClass()
                .getDeclaredFields();

            for (Field field : fields) {
                try {
                    field.setAccessible(true);

                    Object value = field.get(slot);

                    if (value instanceof Slot) {
                        return (Slot) value;
                    }
                } catch (Exception ignored) {}
            }
        }

        return slot;
    }

    public static boolean isPlayerInventorySlot(Slot slot) {
        Slot realSlot = unwrapSlot(slot);

        if (realSlot == null) {
            return false;
        }

        int index = realSlot.getSlotIndex();

        if (realSlot.inventory instanceof InventoryPlayer) {
            return index >= 0 && index <= 35;
        }

        // CreativeSlot sometimes does not expose InventoryPlayer directly,
        // but its wrapped player slot index is still 0-35.
        String className = slot.getClass()
            .getName();

        if (className.equals("net.minecraft.client.gui.inventory.GuiContainerCreative$CreativeSlot")) {
            return index >= 0 && index <= 35;
        }

        return false;
    }

    public static int getPlayerSlotIndex(Slot slot) {
        Slot realSlot = unwrapSlot(slot);

        if (realSlot == null) {
            return -1;
        }

        return realSlot.getSlotIndex();
    }

    public static boolean isLocked(Slot slot) {
        if (!isPlayerInventorySlot(slot)) {
            return false;
        }

        return isLockedPlayerIndex(getPlayerSlotIndex(slot));
    }

    public static boolean isLockedPlayerIndex(int index) {
        return LOCKED_PLAYER_SLOTS.contains(index);
    }

    public static boolean isCurrentHotbarSlotLocked(EntityPlayer player) {
        if (player == null || player.inventory == null) {
            return false;
        }

        return isLockedPlayerIndex(player.inventory.currentItem);
    }

    public static void toggle(Slot slot) {
        if (!isPlayerInventorySlot(slot)) {
            return;
        }

        togglePlayerIndex(getPlayerSlotIndex(slot));
    }

    public static void togglePlayerIndex(int index) {
        if (index < 0 || index > 35) {
            return;
        }

        if (LOCKED_PLAYER_SLOTS.contains(index)) {
            LOCKED_PLAYER_SLOTS.remove(index);
        } else {
            LOCKED_PLAYER_SLOTS.add(index);
        }

        save();
    }

    public static boolean hasAnyLock() {
        return !LOCKED_PLAYER_SLOTS.isEmpty();
    }

    public static Set<Integer> getLockedSlots() {
        return LOCKED_PLAYER_SLOTS;
    }

    public static void load() {
        LOCKED_PLAYER_SLOTS.clear();

        if (saveFile == null) {
            MyMod.LOG.warn("SlotLock load skipped: saveFile is null");
            return;
        }

        if (!saveFile.exists()) {
            MyMod.LOG.info("SlotLock load skipped: file does not exist yet");
            return;
        }

        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new FileReader(saveFile));

            String line = reader.readLine();

            if (line == null || line.trim()
                .isEmpty()) {
                MyMod.LOG.info("SlotLock loaded empty slot list");
                return;
            }

            String[] parts = line.split(",");

            for (String part : parts) {
                try {
                    int index = Integer.parseInt(part.trim());

                    if (index >= 0 && index <= 35) {
                        LOCKED_PLAYER_SLOTS.add(index);
                    }
                } catch (NumberFormatException ignored) {}
            }

            List<Integer> sorted = new ArrayList<Integer>(LOCKED_PLAYER_SLOTS);
            Collections.sort(sorted);

            MyMod.LOG.info("SlotLock loaded slots: " + sorted);
        } catch (Exception e) {
            MyMod.LOG.error("SlotLock load failed", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {}
            }
        }
    }

    public static void save() {
        if (saveFile == null) {
            MyMod.LOG.warn("SlotLock save failed: saveFile is null");
            return;
        }

        FileWriter writer = null;

        try {
            if (saveFile.getParentFile() != null) {
                saveFile.getParentFile()
                    .mkdirs();
            }

            List<Integer> sorted = new ArrayList<Integer>(LOCKED_PLAYER_SLOTS);
            Collections.sort(sorted);

            writer = new FileWriter(saveFile);

            boolean first = true;

            for (Integer index : sorted) {
                if (!first) {
                    writer.write(",");
                }

                writer.write(String.valueOf(index));
                first = false;
            }

            writer.flush();

            MyMod.LOG.info("SlotLock saved to: " + saveFile.getAbsolutePath());
            MyMod.LOG.info("SlotLock saved slots: " + sorted);
        } catch (Exception e) {
            MyMod.LOG.error("SlotLock save failed", e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception ignored) {}
            }
        }
    }
}
