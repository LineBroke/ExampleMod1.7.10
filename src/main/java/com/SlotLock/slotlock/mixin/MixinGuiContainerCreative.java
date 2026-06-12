package com.SlotLock.slotlock.mixin;

import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.inventory.Slot;

import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.SlotLock.slotlock.SlotLockManager;

@Mixin(GuiContainerCreative.class)
public abstract class MixinGuiContainerCreative {

    /**
     * 创造模式物品栏有自己的 GuiContainerCreative，
     * 所以这里也要单独处理锁定逻辑。
     */
    @Inject(method = "handleMouseClick", at = @At("HEAD"), cancellable = true)
    private void mymodid$lockCreativeSlot(Slot slot, int slotId, int mouseButton, int clickType, CallbackInfo ci) {
        boolean ctrlDown = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean altDown = Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);

        // Windows/Linux: Ctrl + 左键
        // Mac: Option/Alt + 左键
        boolean lockKeyDown = ctrlDown || altDown;

        // 切换锁定状态
        if (lockKeyDown && mouseButton == 0
            && clickType == 0
            && slot != null
            && SlotLockManager.isPlayerInventorySlot(slot)) {
            SlotLockManager.toggle(slot);
            ci.cancel();
            return;
        }

        // 点空白处，放行
        if (slot == null) {
            return;
        }

        // 直接操作锁定格：阻止
        if (SlotLockManager.isLocked(slot)) {
            ci.cancel();
            return;
        }

        // 数字键 hotbar 交换：
        // 如果目标 hotbar 被锁，阻止交换
        if (clickType == 2 && mouseButton >= 0 && mouseButton <= 8) {
            if (SlotLockManager.isLockedPlayerIndex(mouseButton)) {
                ci.cancel();
                return;
            }
        }

        // 创造模式里也不再全局禁用 Shift-click。
        // Shift-click 自动跳过锁定格由 MixinContainer 处理。

        // 拖拽：
        // 为了安全，有锁定格时先禁用拖拽分配。
        if (clickType == 5 && SlotLockManager.hasAnyLock()) {
            ci.cancel();
            return;
        }

        // 双击收集同类物品：
        // 防止把锁定格里的同类物品收走。
        if (clickType == 6 && SlotLockManager.hasAnyLock()) {
            ci.cancel();
            return;
        }
    }

}
