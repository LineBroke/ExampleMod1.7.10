package com.SlotLock.slotlock.mixin;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.SlotLock.slotlock.ClientProxy;
import com.SlotLock.slotlock.SlotLockManager;

@Mixin(GuiContainer.class)
public abstract class MixinGuiContainer {

    @Inject(method = "handleMouseClick", at = @At("HEAD"), cancellable = true)
    private void mymodid$lockSlot(Slot slot, int slotId, int mouseButton, int clickType, CallbackInfo ci) {

        // 动态获取玩家当前设置的按键代码
        int keyCode = ClientProxy.lockKey.getKeyCode();
        boolean lockKeyDown = false;

        // 在 1.7.10 中，如果按键代码 < 0，代表玩家绑定的是鼠标按键（如鼠标中键、侧键）
        // 鼠标按键的真实代码 = keyCode + 100
        if (keyCode >= 0) {
            lockKeyDown = Keyboard.isKeyDown(keyCode);
        } else {
            lockKeyDown = Mouse.isButtonDown(keyCode + 100);
        }

        // 1. 切换锁定状态
        if (lockKeyDown && mouseButton == 0
            && clickType == 0
            && slot != null
            && SlotLockManager.isPlayerInventorySlot(slot)) {
            SlotLockManager.toggle(slot);
            ci.cancel();
            return;
        }

        // 如果点击到了空白处，直接放行原版逻辑
        if (slot == null) {
            return;
        }

        // 2. 如果鼠标直接点到了被锁定的格子，无条件阻止
        if (SlotLockManager.isLocked(slot)) {
            ci.cancel();
            return;
        }

        // 3. 数字键 hotbar 交换
        if (clickType == 2 && mouseButton >= 0 && mouseButton <= 8) {
            if (SlotLockManager.isLockedPlayerIndex(mouseButton)) {
                ci.cancel();
                return;
            }
        }

        // 4. 双击收集同类物品
        if (clickType == 6 && SlotLockManager.hasAnyLock()) {
            ci.cancel();
            return;
        }

        // 5. 拖拽分配
        if (clickType == 5 && SlotLockManager.hasAnyLock()) {
            ci.cancel();
            return;
        }
    }
}
