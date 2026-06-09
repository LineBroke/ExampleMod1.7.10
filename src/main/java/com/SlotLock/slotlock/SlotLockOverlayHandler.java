package com.SlotLock.slotlock;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class SlotLockOverlayHandler {

    private static Field guiLeftField;
    private static Field guiTopField;

    static {
        try {
            guiLeftField = GuiContainer.class.getDeclaredField("guiLeft");
            guiTopField = GuiContainer.class.getDeclaredField("guiTop");
        } catch (Exception e) {
            try {
                guiLeftField = GuiContainer.class.getDeclaredField("field_147003_i");
                guiTopField = GuiContainer.class.getDeclaredField("field_147009_r");
            } catch (Exception ignored) {}
        }

        if (guiLeftField != null) {
            guiLeftField.setAccessible(true);
        }

        if (guiTopField != null) {
            guiTopField.setAccessible(true);
        }
    }

    @SubscribeEvent
    public void onDrawGui(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiContainer)) {
            return;
        }

        GuiContainer gui = (GuiContainer) event.gui;

        int guiLeft = getGuiLeft(gui);
        int guiTop = getGuiTop(gui);

        for (Object obj : gui.inventorySlots.inventorySlots) {
            if (!(obj instanceof Slot)) {
                continue;
            }

            Slot slot = (Slot) obj;

            if (!SlotLockManager.isLocked(slot)) {
                continue;
            }

            int x = guiLeft + slot.xDisplayPosition;
            int y = guiTop + slot.yDisplayPosition;

            drawLockBox(x, y);
        }
    }

    @SubscribeEvent
    public void onDrawHotbar(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.HOTBAR) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();

        if (mc.thePlayer == null) {
            return;
        }

        int screenWidth = event.resolution.getScaledWidth();
        int screenHeight = event.resolution.getScaledHeight();

        int hotbarLeft = screenWidth / 2 - 91;
        int hotbarTop = screenHeight - 22;

        for (int i = 0; i < 9; i++) {
            if (!SlotLockManager.isLockedPlayerIndex(i)) {
                continue;
            }

            int x = hotbarLeft + i * 20 + 2;
            int y = hotbarTop + 3;

            drawLockBox(x, y);
        }
    }

    private int getGuiLeft(GuiContainer gui) {
        try {
            return guiLeftField.getInt(gui);
        } catch (Exception e) {
            return 0;
        }
    }

    private int getGuiTop(GuiContainer gui) {
        try {
            return guiTopField.getInt(gui);
        } catch (Exception e) {
            return 0;
        }
    }

    private void drawLockBox(int x, int y) {
        Gui.drawRect(x, y, x + 16, y + 16, 0x80FF0000);

        Gui.drawRect(x, y, x + 16, y + 1, 0xFFFFFF00);
        Gui.drawRect(x, y + 15, x + 16, y + 16, 0xFFFFFF00);
        Gui.drawRect(x, y, x + 1, y + 16, 0xFFFFFF00);
        Gui.drawRect(x + 15, y, x + 16, y + 16, 0xFFFFFF00);
    }
}
