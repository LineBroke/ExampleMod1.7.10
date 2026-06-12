package com.slotlock.slotlock;

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
    private static String lastSavedText = "";

    /**
     * Delayed save:
     * Lock/unlock only marks dirty.
     * Actual file write happens after SAVE_DELAY_MS without further changes.
     */
    private static boolean dirty = false;
    private static long lastChangeTime = 0L;
    private static final long SAVE_DELAY_MS = 2000L;

    /**
     * Creative inventory wraps player slots inside GuiContainerCreative.CreativeSlot.
     * We cache the wrapped Slot field so we do not reflect-scan every time.
     */
    private static Field cachedCreativeSlotField = null;
    private static boolean hasSearchedCreativeSlotField = false;

    public static void setSaveFile(File configDir) {
        File slotLockDir = new File(configDir, "slotlock");
        saveFile = new File(slotLockDir, "locked_slots.cfg");

        MyMod.LOG.info("SlotLock save file: " + saveFile.getAbsolutePath());

        load();
    }

    public static Slot unwrapSlot(Slot slot) {
        if (slot == null) {
            return null;
        }

        if (slot.getClass()
            .getName()
            .equals("net.minecraft.client.gui.inventory.GuiContainerCreative$CreativeSlot")) {
            if (!hasSearchedCreativeSlotField) {
                Field[] fields = slot.getClass()
                    .getDeclaredFields();

                for (Field field : fields) {
                    try {
                        if (Slot.class.isAssignableFrom(field.getType())) {
                            field.setAccessible(true);
                            cachedCreativeSlotField = field;
                            break;
                        }
                    } catch (Exception ignored) {}
                }

                hasSearchedCreativeSlotField = true;
            }

            if (cachedCreativeSlotField != null) {
                try {
                    Object value = cachedCreativeSlotField.get(slot);

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

        markDirty();
    }

    public static boolean hasAnyLock() {
        return !LOCKED_PLAYER_SLOTS.isEmpty();
    }

    public static Set<Integer> getLockedSlots() {
        return LOCKED_PLAYER_SLOTS;
    }

    public static void markDirty() {
        dirty = true;
        lastChangeTime = System.currentTimeMillis();

        MyMod.LOG.info("SlotLock marked dirty");
    }

    public static void saveIfDirtyAfterDelay() {
        if (!dirty) {
            return;
        }

        long now = System.currentTimeMillis();

        if (now - lastChangeTime < SAVE_DELAY_MS) {
            return;
        }

        MyMod.LOG.info("SlotLock delayed save triggered");

        saveNow();
    }

    public static void saveNow() {
        if (!dirty) {
            return;
        }

        MyMod.LOG.info("SlotLock saveNow called");

        dirty = false;
        save();
    }

    public static void load() {
        LOCKED_PLAYER_SLOTS.clear();
        dirty = false;

        if (saveFile == null) {
            MyMod.LOG.warn("SlotLock load skipped: saveFile is null");
            return;
        }

        if (!saveFile.exists()) {
            MyMod.LOG.info("SlotLock load skipped: file does not exist yet");
            lastSavedText = buildConfigText();
            return;
        }

        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new FileReader(saveFile));

            StringBuilder fullTextBuilder = new StringBuilder();
            String lockedSlotsText = null;

            String line;

            while ((line = reader.readLine()) != null) {
                fullTextBuilder.append(line)
                    .append("\n");

                String trimmed = line.trim();

                if (trimmed.length() == 0) {
                    continue;
                }

                if (trimmed.startsWith("#")) {
                    continue;
                }

                if (trimmed.startsWith("lockedSlots=")) {
                    lockedSlotsText = trimmed.substring("lockedSlots=".length())
                        .trim();
                    continue;
                }

                /*
                 * Backward compatibility:
                 * Old config format was just:
                 * 0,1,2,3
                 */
                if (lockedSlotsText == null && looksLikeOldSlotList(trimmed)) {
                    lockedSlotsText = trimmed;
                }
            }

            if (lockedSlotsText != null && lockedSlotsText.length() > 0) {
                readSlotList(lockedSlotsText);
            }

            List<Integer> sorted = new ArrayList<Integer>(LOCKED_PLAYER_SLOTS);
            Collections.sort(sorted);

            MyMod.LOG.info("SlotLock loaded slots: " + sorted);

            lastSavedText = buildConfigText();

            /*
             * If old format was loaded, rewrite it once into the new format.
             * This is a rare one-time write.
             */
            String oldText = fullTextBuilder.toString();

            if (!oldText.equals(lastSavedText)) {
                save();
            }
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

        String text = buildConfigText();

        if (text.equals(lastSavedText) && saveFile.exists()) {
            MyMod.LOG.info("SlotLock save skipped: no changes");
            return;
        }

        FileWriter writer = null;
        File tempFile = new File(saveFile.getAbsolutePath() + ".tmp");

        try {
            if (saveFile.getParentFile() != null) {
                saveFile.getParentFile()
                    .mkdirs();
            }

            writer = new FileWriter(tempFile);
            writer.write(text);
            writer.flush();
            writer.close();
            writer = null;

            if (saveFile.exists() && !saveFile.delete()) {
                MyMod.LOG.warn("SlotLock could not delete old save file, trying direct overwrite");
                writeDirectly(saveFile, text);
            } else if (!tempFile.renameTo(saveFile)) {
                MyMod.LOG.warn("SlotLock could not rename temp save file, trying direct overwrite");
                writeDirectly(saveFile, text);
            }

            lastSavedText = text;

            List<Integer> sorted = new ArrayList<Integer>(LOCKED_PLAYER_SLOTS);
            Collections.sort(sorted);

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

            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    private static void readSlotList(String text) {
        String[] parts = text.split(",");

        for (String part : parts) {
            try {
                String trimmed = part.trim();

                if (trimmed.length() == 0) {
                    continue;
                }

                int index = Integer.parseInt(trimmed);

                if (index >= 0 && index <= 35) {
                    LOCKED_PLAYER_SLOTS.add(index);
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    private static boolean looksLikeOldSlotList(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if ((c >= '0' && c <= '9') || c == ',' || c == ' ') {
                continue;
            }

            return false;
        }

        return true;
    }

    private static String buildConfigText() {
        StringBuilder builder = new StringBuilder();

        builder.append("# SlotLock config\n");
        builder.append("# InventoryPlayer index: 0-8 = hotbar, 9-35 = main inventory\n");
        builder.append("# Example: lockedSlots=0,1,2,9,10\n");

        builder.append("lockedSlots=");

        List<Integer> sorted = new ArrayList<Integer>(LOCKED_PLAYER_SLOTS);
        Collections.sort(sorted);

        boolean first = true;

        for (Integer index : sorted) {
            if (!first) {
                builder.append(",");
            }

            builder.append(index);
            first = false;
        }

        builder.append("\n");

        return builder.toString();
    }

    private static void writeDirectly(File file, String text) throws Exception {
        FileWriter writer = null;

        try {
            writer = new FileWriter(file);
            writer.write(text);
            writer.flush();
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }
}
