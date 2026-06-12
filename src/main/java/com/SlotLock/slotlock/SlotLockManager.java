package com.slotlock.slotlock;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.Loader;

public final class SlotLockManager {

    private static final Set<Integer> LOCKED_PLAYER_SLOTS = new HashSet<Integer>();
    private static final Set<Integer> EMPTY_WHEN_LOCKED_PLAYER_SLOTS = new HashSet<Integer>();

    private static final long SAVE_DELAY_MS = 500L;

    private static File saveFile;
    private static boolean dirty = false;
    private static long dirtyTime = 0L;

    private SlotLockManager() {}

    public static void setSaveFile(File configDir) {
        File slotlockDir = new File(configDir, "slotlock");

        if (!slotlockDir.exists()) {
            slotlockDir.mkdirs();
        }

        saveFile = new File(slotlockDir, "locked_slots.cfg");

        MyMod.LOG.info("SlotLock save file: " + saveFile.getAbsolutePath());

        load();
    }

    public static void load() {
        LOCKED_PLAYER_SLOTS.clear();
        EMPTY_WHEN_LOCKED_PLAYER_SLOTS.clear();

        if (saveFile == null) {
            File configDir = new File(
                Loader.instance()
                    .getConfigDir(),
                "slotlock");

            if (!configDir.exists()) {
                configDir.mkdirs();
            }

            saveFile = new File(configDir, "locked_slots.cfg");
        }

        if (!saveFile.exists()) {
            MyMod.LOG.info("SlotLock load skipped: file does not exist yet");
            dirty = false;
            return;
        }

        Properties properties = new Properties();
        FileInputStream inputStream = null;

        try {
            inputStream = new FileInputStream(saveFile);
            properties.load(inputStream);

            String lockedSlots = properties.getProperty("lockedSlots", "");

            if (lockedSlots != null && lockedSlots.trim()
                .length() > 0) {
                String[] parts = lockedSlots.split(",");

                for (String part : parts) {
                    String trimmed = part.trim();

                    if (trimmed.length() == 0) {
                        continue;
                    }

                    try {
                        int index = Integer.parseInt(trimmed);

                        if (index >= 0 && index <= 35) {
                            LOCKED_PLAYER_SLOTS.add(index);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (Exception e) {
            MyMod.LOG.warn("SlotLock failed to load locked slots: " + e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception ignored) {}
            }
        }

        dirty = false;

        MyMod.LOG.info("SlotLock loaded slots: " + getSortedLockedSlotsForLog());
    }

    public static void saveIfDirtyAfterDelay() {
        if (!dirty) {
            return;
        }

        if (System.currentTimeMillis() - dirtyTime >= SAVE_DELAY_MS) {
            saveNow();
        }
    }

    public static void saveNow() {
        if (!dirty) {
            return;
        }

        if (saveFile == null) {
            MyMod.LOG.warn("SlotLock saveNow skipped: saveFile is null");
            return;
        }

        if (writeSaveFile()) {
            dirty = false;
        }
    }

    private static boolean writeSaveFile() {
        File parent = saveFile.getParentFile();

        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        Properties properties = new Properties();

        List<Integer> sorted = new ArrayList<Integer>(LOCKED_PLAYER_SLOTS);
        Collections.sort(sorted);

        StringBuilder builder = new StringBuilder();

        boolean first = true;

        for (Integer index : sorted) {
            if (!first) {
                builder.append(",");
            }

            builder.append(index.intValue());
            first = false;
        }

        properties.setProperty("lockedSlots", builder.toString());

        FileOutputStream outputStream = null;

        try {
            outputStream = new FileOutputStream(saveFile);
            properties.store(outputStream, "SlotLock locked player inventory slots");

            MyMod.LOG.info("SlotLock saved slots: " + sorted);

            return true;
        } catch (Exception e) {
            MyMod.LOG.warn("SlotLock failed to save locked slots: " + e);
            return false;
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private static void markDirty() {
        dirty = true;
        dirtyTime = System.currentTimeMillis();
    }

    public static boolean hasAnyLock() {
        return !LOCKED_PLAYER_SLOTS.isEmpty();
    }

    public static Set<Integer> getLockedSlots() {
        return Collections.unmodifiableSet(LOCKED_PLAYER_SLOTS);
    }

    public static boolean isLocked(Slot slot) {
        int playerIndex = getPlayerSlotIndex(slot);

        if (playerIndex < 0 || playerIndex > 35) {
            return false;
        }

        return isLockedPlayerIndex(playerIndex);
    }

    public static boolean isLockedPlayerIndex(int index) {
        return LOCKED_PLAYER_SLOTS.contains(index);
    }

    public static boolean isPlayerInventorySlot(Slot slot) {
        int index = getPlayerSlotIndex(slot);
        return index >= 0 && index <= 35;
    }

    public static int getPlayerSlotIndex(Slot slot) {
        Slot realSlot = unwrapSlot(slot);

        if (realSlot == null) {
            return -1;
        }

        int index = realSlot.getSlotIndex();

        /*
         * Normal vanilla / normal GuiContainer player inventory slot.
         */
        if (realSlot.inventory instanceof InventoryPlayer) {
            if (index >= 0 && index <= 35) {
                return index;
            }

            return -1;
        }

        /*
         * ModularUI / ModularUI2 / GT machine GUI player inventory slot.
         */
        int modularIndex = getModularUIPlayerSlotIndex(realSlot);

        if (modularIndex >= 0 && modularIndex <= 35) {
            return modularIndex;
        }

        return -1;
    }

    public static boolean isCurrentHotbarSlotLocked(EntityPlayer player) {
        if (player == null || player.inventory == null) {
            return false;
        }

        int current = player.inventory.currentItem;

        if (current < 0 || current > 8) {
            return false;
        }

        return isLockedPlayerIndex(current);
    }

    public static void toggle(Slot slot) {
        int playerIndex = getPlayerSlotIndex(slot);

        if (playerIndex < 0 || playerIndex > 35) {
            return;
        }

        if (LOCKED_PLAYER_SLOTS.contains(playerIndex)) {
            LOCKED_PLAYER_SLOTS.remove(playerIndex);
            EMPTY_WHEN_LOCKED_PLAYER_SLOTS.remove(playerIndex);
        } else {
            LOCKED_PLAYER_SLOTS.add(playerIndex);

            ItemStack stack = slot.getStack();

            if (stack == null) {
                EMPTY_WHEN_LOCKED_PLAYER_SLOTS.add(playerIndex);
            } else {
                EMPTY_WHEN_LOCKED_PLAYER_SLOTS.remove(playerIndex);
            }
        }

        markDirty();
    }

    public static void togglePlayerIndex(int index) {
        if (index < 0 || index > 35) {
            return;
        }

        if (LOCKED_PLAYER_SLOTS.contains(index)) {
            LOCKED_PLAYER_SLOTS.remove(index);
            EMPTY_WHEN_LOCKED_PLAYER_SLOTS.remove(index);
        } else {
            LOCKED_PLAYER_SLOTS.add(index);
        }

        markDirty();
    }

    public static boolean wasEmptyWhenLocked(int playerIndex) {
        return EMPTY_WHEN_LOCKED_PLAYER_SLOTS.contains(playerIndex);
    }

    public static void markEmptyWhenLocked(int playerIndex) {
        if (playerIndex < 0 || playerIndex > 35) {
            return;
        }

        if (LOCKED_PLAYER_SLOTS.contains(playerIndex)) {
            EMPTY_WHEN_LOCKED_PLAYER_SLOTS.add(playerIndex);
        }
    }

    public static void forgetEmptyWhenLocked(int playerIndex) {
        EMPTY_WHEN_LOCKED_PLAYER_SLOTS.remove(playerIndex);
    }

    private static Slot unwrapSlot(Slot slot) {
        if (slot == null) {
            return null;
        }

        /*
         * Creative inventory wraps real Slot in GuiContainerCreative.CreativeSlot.
         */
        try {
            Class<?> creativeSlotClass = Class
                .forName("net.minecraft.client.gui.inventory.GuiContainerCreative$CreativeSlot");

            if (creativeSlotClass.isInstance(slot)) {
                Field field = creativeSlotClass.getDeclaredField("slot");
                field.setAccessible(true);

                Object inner = field.get(slot);

                if (inner instanceof Slot) {
                    return (Slot) inner;
                }
            }
        } catch (Throwable ignored) {}

        return slot;
    }

    private static int getModularUIPlayerSlotIndex(Slot slot) {
        /*
         * ModularUI2 / Cleanroom ModularUI:
         * com.cleanroommc.modularui.widgets.slot.ModularSlot
         * extends com.cleanroommc.modularui.utils.item.SlotItemHandler.
         * Its Slot.inventory is usually dummy InventoryBasic, so we must inspect itemHandler.
         */
        int modularUI2Index = getModularUI2PlayerSlotIndex(slot);

        if (modularUI2Index >= 0 && modularUI2Index <= 35) {
            return modularUI2Index;
        }

        /*
         * Old ModularUI1:
         * com.gtnewhorizons.modularui.api.forge.SlotItemHandler
         */
        int modularUI1Index = getModularUI1PlayerSlotIndex(slot);

        if (modularUI1Index >= 0 && modularUI1Index <= 35) {
            return modularUI1Index;
        }

        return -1;
    }

    private static int getModularUI2PlayerSlotIndex(Slot slot) {
        try {
            Class<?> slotItemHandlerClass = Class.forName("com.cleanroommc.modularui.utils.item.SlotItemHandler");

            if (!slotItemHandlerClass.isInstance(slot)) {
                return -1;
            }

            Object handler = invokeNoArg(slot, "getItemHandler");

            if (handler == null) {
                handler = readObjectField(slot, "itemHandler");
            }

            if (handler == null) {
                return -1;
            }

            int localIndex = slot.getSlotIndex();
            int offset = 0;

            for (int depth = 0; handler != null && depth < 8; depth++) {
                String className = handler.getClass()
                    .getName();

                if ("com.cleanroommc.modularui.utils.item.PlayerArmorInvWrapper".equals(className)) {
                    return -1;
                }

                if ("com.cleanroommc.modularui.utils.item.PlayerMainInvWrapper".equals(className)) {
                    int result = localIndex + offset;

                    if (result >= 0 && result <= 35) {
                        return result;
                    }

                    return -1;
                }

                if ("com.cleanroommc.modularui.utils.item.PlayerInvWrapper".equals(className)) {
                    int result = localIndex + offset;

                    if (result >= 0 && result <= 35) {
                        return result;
                    }

                    return -1;
                }

                if ("com.cleanroommc.modularui.utils.item.InvWrapper".equals(className)) {
                    Object inv = invokeNoArg(handler, "getInv");

                    if (inv == null) {
                        inv = invokeNoArg(handler, "getInventory");
                    }

                    if (inv == null) {
                        inv = findInventoryPlayerInFields(handler);
                    }

                    if (inv instanceof InventoryPlayer) {
                        int result = localIndex + offset;

                        if (result >= 0 && result <= 35) {
                            return result;
                        }
                    }

                    return -1;
                }

                if ("com.cleanroommc.modularui.utils.item.RangedWrapper".equals(className)) {
                    offset += readIntField(handler, "minSlot", 0);

                    Object next = invokeNoArg(handler, "getCompose");

                    if (next == null) {
                        next = invokeNoArg(handler, "getComposeHandler");
                    }

                    if (next == null) {
                        next = readObjectField(handler, "compose");
                    }

                    if (next == null) {
                        next = readObjectField(handler, "handler");
                    }

                    if (next == null) {
                        next = readObjectField(handler, "wrapped");
                    }

                    handler = next;
                    continue;
                }

                return -1;
            }
        } catch (Throwable ignored) {}

        return -1;
    }

    private static int getModularUI1PlayerSlotIndex(Slot slot) {
        try {
            Class<?> slotItemHandlerClass = Class.forName("com.gtnewhorizons.modularui.api.forge.SlotItemHandler");

            if (!slotItemHandlerClass.isInstance(slot)) {
                return -1;
            }

            Object handler = invokeNoArg(slot, "getItemHandler");

            if (handler == null) {
                handler = readObjectField(slot, "itemHandler");
            }

            if (handler == null) {
                return -1;
            }

            int localIndex = slot.getSlotIndex();
            int offset = 0;

            for (int depth = 0; handler != null && depth < 8; depth++) {
                String className = handler.getClass()
                    .getName();

                if ("com.gtnewhorizons.modularui.api.forge.PlayerArmorInvWrapper".equals(className)) {
                    return -1;
                }

                if ("com.gtnewhorizons.modularui.api.forge.PlayerMainInvWrapper".equals(className)) {
                    int result = localIndex + offset;

                    if (result >= 0 && result <= 35) {
                        return result;
                    }

                    return -1;
                }

                if ("com.gtnewhorizons.modularui.api.forge.PlayerInvWrapper".equals(className)) {
                    int result = localIndex + offset;

                    if (result >= 0 && result <= 35) {
                        return result;
                    }

                    return -1;
                }

                if ("com.gtnewhorizons.modularui.api.forge.InvWrapper".equals(className)) {
                    Object inv = tryGetSourceInventory(handler);

                    if (inv == null) {
                        inv = invokeNoArg(handler, "getInv");
                    }

                    if (inv == null) {
                        inv = invokeNoArg(handler, "getInventory");
                    }

                    if (inv == null) {
                        inv = findInventoryPlayerInFields(handler);
                    }

                    if (inv instanceof InventoryPlayer) {
                        int result = localIndex + offset;

                        if (result >= 0 && result <= 35) {
                            return result;
                        }
                    }

                    return -1;
                }

                if ("com.gtnewhorizons.modularui.api.forge.RangedWrapper".equals(className)) {
                    offset += readIntField(handler, "minSlot", 0);

                    Object next = invokeNoArg(handler, "getCompose");

                    if (next == null) {
                        next = invokeNoArg(handler, "getComposeHandler");
                    }

                    if (next == null) {
                        next = readObjectField(handler, "compose");
                    }

                    if (next == null) {
                        next = readObjectField(handler, "handler");
                    }

                    if (next == null) {
                        next = readObjectField(handler, "wrapped");
                    }

                    handler = next;
                    continue;
                }

                return -1;
            }
        } catch (Throwable ignored) {}

        return -1;
    }

    private static Object tryGetSourceInventory(Object handler) {
        if (handler == null) {
            return null;
        }

        Object result = invokeNoArg(handler, "getSourceInventory");

        if (result != null) {
            return result;
        }

        result = invokeNoArg(handler, "getInventoryPlayer");

        if (result != null) {
            return result;
        }

        return null;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null || methodName == null) {
            return null;
        }

        Class<?> clazz = target.getClass();

        while (clazz != null) {
            try {
                Method method = clazz.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (Throwable ignored) {}

            try {
                Method method = clazz.getMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (Throwable ignored) {}

            clazz = clazz.getSuperclass();
        }

        return null;
    }

    private static Object readObjectField(Object target, String fieldName) {
        if (target == null || fieldName == null) {
            return null;
        }

        Class<?> clazz = target.getClass();

        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (Throwable ignored) {}

            clazz = clazz.getSuperclass();
        }

        return null;
    }

    private static int readIntField(Object target, String fieldName, int fallback) {
        if (target == null || fieldName == null) {
            return fallback;
        }

        Class<?> clazz = target.getClass();

        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.getInt(target);
            } catch (Throwable ignored) {}

            clazz = clazz.getSuperclass();
        }

        return fallback;
    }

    private static Object findInventoryPlayerInFields(Object target) {
        if (target == null) {
            return null;
        }

        Class<?> clazz = target.getClass();

        while (clazz != null) {
            Field[] fields = clazz.getDeclaredFields();

            for (Field field : fields) {
                try {
                    field.setAccessible(true);

                    Object value = field.get(target);

                    if (value instanceof InventoryPlayer) {
                        return value;
                    }
                } catch (Throwable ignored) {}
            }

            clazz = clazz.getSuperclass();
        }

        return null;
    }

    private static List<Integer> getSortedLockedSlotsForLog() {
        List<Integer> sorted = new ArrayList<Integer>(LOCKED_PLAYER_SLOTS);
        Collections.sort(sorted);
        return sorted;
    }
}
