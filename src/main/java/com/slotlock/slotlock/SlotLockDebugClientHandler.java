package com.slotlock.slotlock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class SlotLockDebugClientHandler {

    private static final SlotLockDebugClientHandler INSTANCE = new SlotLockDebugClientHandler();

    private boolean lastF4Down = false;
    private boolean lastF5Down = false;

    private boolean watchEnabled = false;

    private Container lastContainer = null;
    private final String[] lastPlayerInventoryKeys = new String[36];

    private SlotLockDebugClientHandler() {}

    public static SlotLockDebugClientHandler instance() {
        return INSTANCE;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (!SlotLockDebug.isEnabled()) {
            return;
        }

        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();

        if (mc == null || mc.thePlayer == null) {
            return;
        }

        EntityPlayer player = mc.thePlayer;
        Container container = player.openContainer;

        boolean f4Down = Keyboard.isKeyDown(Keyboard.KEY_F4);
        boolean f5Down = Keyboard.isKeyDown(Keyboard.KEY_F5);

        if (f4Down && !lastF4Down) {
            SlotLockDebug.dumpCurrentContainer(player, container, mc.currentScreen, "manual F4 dump");

            snapshotPlayerInventory(player);
        }

        if (f5Down && !lastF5Down) {
            watchEnabled = !watchEnabled;

            SlotLockDebug.log("watch mode = " + watchEnabled);

            snapshotPlayerInventory(player);

            if (watchEnabled) {
                SlotLockDebug.dumpCurrentContainer(player, container, mc.currentScreen, "watch enabled");
            }
        }

        lastF4Down = f4Down;
        lastF5Down = f5Down;

        if (!watchEnabled) {
            return;
        }

        if (!(mc.currentScreen instanceof GuiContainer)) {
            lastContainer = null;
            snapshotPlayerInventory(player);
            return;
        }

        if (container != lastContainer) {
            lastContainer = container;

            SlotLockDebug
                .dumpCurrentContainer(player, container, mc.currentScreen, "container changed while watch enabled");

            snapshotPlayerInventory(player);
            return;
        }

        detectPlayerInventoryChanges(player, container, mc.currentScreen);
    }

    private void snapshotPlayerInventory(EntityPlayer player) {
        if (player == null || player.inventory == null) {
            clearSnapshot();
            return;
        }

        for (int i = 0; i <= 35; i++) {
            lastPlayerInventoryKeys[i] = SlotLockDebug.getStackKey(player.inventory.mainInventory[i]);
        }
    }

    private void clearSnapshot() {
        for (int i = 0; i <= 35; i++) {
            lastPlayerInventoryKeys[i] = "null";
        }
    }

    private void detectPlayerInventoryChanges(EntityPlayer player, Container container, Object currentScreen) {
        if (player == null || player.inventory == null) {
            return;
        }

        boolean changed = false;
        boolean lockedChanged = false;

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i <= 35; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            String now = SlotLockDebug.getStackKey(stack);
            String before = lastPlayerInventoryKeys[i];

            if (before == null) {
                before = "null";
            }

            if (!before.equals(now)) {
                boolean locked = SlotLockManager.isLockedPlayerIndex(i);

                if (locked) {
                    lockedChanged = true;
                }

                changed = true;

                builder.append("\n  playerIndex=")
                    .append(i)
                    .append(", locked=")
                    .append(locked)
                    .append(", before=")
                    .append(before)
                    .append(", after=")
                    .append(now)
                    .append(", afterText=")
                    .append(SlotLockDebug.getStackText(stack));
            }

            lastPlayerInventoryKeys[i] = now;
        }

        if (!changed) {
            return;
        }

        String screenClass = currentScreen == null ? "null"
            : currentScreen.getClass()
                .getName();
        String containerClass = container == null ? "null"
            : container.getClass()
                .getName();

        if (lockedChanged) {
            SlotLockDebug.log(
                "LOCKED PLAYER INVENTORY CHANGED!" + "\n  screenClass="
                    + screenClass
                    + "\n  containerClass="
                    + containerClass
                    + builder.toString());

            SlotLockDebug.dumpCurrentContainer(player, container, currentScreen, "locked player inventory changed");
        } else {
            /*
             * 普通槽变化也记录一行，帮助判断操作流程，但不 dump 全容器。
             */
            SlotLockDebug.log(
                "player inventory changed" + "\n  screenClass="
                    + screenClass
                    + "\n  containerClass="
                    + containerClass
                    + builder.toString());
        }
    }
}
