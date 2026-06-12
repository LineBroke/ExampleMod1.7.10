package com.slotlock.slotlock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiOpenEvent;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class SlotLockOverlayHandler {

    private static final ResourceLocation LOCK_ICON = new ResourceLocation("slotlock", "textures/gui/lock_icon.png");

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

    public static void drawLockedBackground(int x, int y) {
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_TEXTURE_BIT);

        GL11.glPushMatrix();

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        // 右边之前少 1 像素，所以这里用 x + 17
        Gui.drawRect(x, y, x + 17, y + 16, 0x5599FF99);

        GL11.glPopMatrix();
        GL11.glPopAttrib();

        // 防止影响后面的物品、数量文字、物品名
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void drawLockedIcon(int slotX, int slotY) {
        // 右上角小锁
        drawLockIcon(slotX + 9, slotY - 1, 8, 8);
    }

    private static void drawLockIcon(int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getMinecraft();

        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_TEXTURE_BIT);

        GL11.glPushMatrix();

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        mc.getTextureManager()
            .bindTexture(LOCK_ICON);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();

        double z = 300.0D;

        tessellator.addVertexWithUV(x, y + height, z, 0.0D, 1.0D);
        tessellator.addVertexWithUV(x + width, y + height, z, 1.0D, 1.0D);
        tessellator.addVertexWithUV(x + width, y, z, 1.0D, 0.0D);
        tessellator.addVertexWithUV(x, y, z, 0.0D, 0.0D);

        tessellator.draw();

        GL11.glPopMatrix();
        GL11.glPopAttrib();

        // 防止锁图标影响后面的 HUD 文字
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
