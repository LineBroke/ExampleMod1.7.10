package com.slotlock.slotlock.mixin;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.slotlock.slotlock.ClientProxy;
import com.slotlock.slotlock.SlotLockManager;

@Mixin(GuiContainer.class)
public abstract class MixinGuiContainer {

<<<<<<< Updated upstream
=======
    /**
     * 1.7.10 GuiContainer 的核心 slot 点击处理方法。
     *
     * 参数含义：
     * slot = 当前操作的格子
     * slotId = container slot id
     * mouseButton = 鼠标按钮 / 数字键编号
     * clickType = 点击类型
     *
     * clickType 常见值：
     * 0 = 普通点击
     * 1 = shift-click
     * 2 = 数字键换位 hotbar swap
     * 3 = creative middle click
     * 4 = Q 丢弃
     * 5 = 拖拽
     * 6 = 双击收集同类物品
     */
>>>>>>> Stashed changes
    @Inject(method = "handleMouseClick", at = @At("HEAD"), cancellable = true)
    private void mymodid$lockSlot(Slot slot, int slotId, int mouseButton, int clickType, CallbackInfo ci) {

<<<<<<< Updated upstream
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
=======
        // 1. CTRL + 左键：切换锁定状态
        if (ctrlDown && mouseButton == 0
>>>>>>> Stashed changes
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
<<<<<<< Updated upstream
=======
        // 包括普通点击、右键点击、Shift 点击、Q 丢弃等从锁定格起手的操作
>>>>>>> Stashed changes
        if (SlotLockManager.isLocked(slot)) {
            ci.cancel();
            return;
        }

        // 3. 数字键 hotbar 交换
<<<<<<< Updated upstream
=======
        // clickType == 2 时，mouseButton 是快捷栏 index，范围 0-8。
        // 如果目标快捷栏格子被锁，阻止交换。
>>>>>>> Stashed changes
        if (clickType == 2 && mouseButton >= 0 && mouseButton <= 8) {
            if (SlotLockManager.isLockedPlayerIndex(mouseButton)) {
                ci.cancel();
                return;
            }
        }

        // 4. 双击收集同类物品
<<<<<<< Updated upstream
=======
        // 为了防止双击把锁定格里的同类物品收走，
        // 只要存在锁定格，就禁用双击收集。
>>>>>>> Stashed changes
        if (clickType == 6 && SlotLockManager.hasAnyLock()) {
            ci.cancel();
            return;
        }

<<<<<<< Updated upstream
        // 5. 拖拽分配
        if (clickType == 5 && SlotLockManager.hasAnyLock()) {
            ci.cancel();
            return;
        }
=======
        // 注意：
        // 这里不再全局禁用 shift-click 和拖拽。
        // 这样和 GTNH 里的 AE / GT / NEI 兼容性更好。
        //
        // 但是要重点测试：
        // 1. 箱子 Shift-click 物品会不会进入锁定格
        // 2. AE 终端 Shift-click 物品会不会进入锁定格
        // 3. 拖拽物品经过锁定格会不会放进去
        //
        // 如果这些又出现问题，再把 clickType == 1 或 clickType == 5 的保护加回来。
>>>>>>> Stashed changes
    }

}
