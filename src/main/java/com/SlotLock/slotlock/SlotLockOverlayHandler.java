package com.slotlock.slotlock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import com.slotlock.slotlock.mixin.IGuiContainerAccess;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class SlotLockOverlayHandler {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        SlotLockManager.saveIfDirtyAfterDelay();
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.gui == null) {
            SlotLockManager.saveNow();
        }
    }

    @SubscribeEvent
    public void onDrawGui(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiContainer)) {
            return;
        }

        GuiContainer gui = (GuiContainer) event.gui;

        int guiLeft = ((IGuiContainerAccess) gui).getGuiLeft();
        int guiTop = ((IGuiContainerAccess) gui).getGuiTop();

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

    private void drawLockBox(int x, int y) {
        Gui.drawRect(x, y, x + 16, y + 16, 0x80FF0000);

        Gui.drawRect(x, y, x + 16, y + 1, 0xFFFFFF00);
        Gui.drawRect(x, y + 15, x + 16, y + 16, 0xFFFFFF00);
        Gui.drawRect(x, y, x + 1, y + 16, 0xFFFFFF00);
        Gui.drawRect(x + 15, y, x + 16, y + 16, 0xFFFFFF00);
    }

}
