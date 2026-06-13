package com.slotlock.slotlock;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class SlotLockAutoMover {

    /**
     * 搬运状态机。
     */
    private enum MoveState {
        IDLE,
        WAITING_TO_PLACE,
        COOLDOWN
    }

    private MoveState state = MoveState.IDLE;
    private int stateTicks = 0;

    private int pendingWindowId = -1;

    /*
     * 原目标槽：
     * 优先把物品放到这个未锁定空槽。
     */
    private int pendingTargetSlotNumber = -1;
    private int pendingTargetPlayerIndex = -1;

    /*
     * 来源槽：
     * 如果目标槽失效，最后尝试把物品放回来源槽，
     * 避免鼠标上残留一组物品。
     */
    private int pendingSourceSlotNumber = -1;
    private int pendingSourcePlayerIndex = -1;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();

        if (mc == null || mc.theWorld == null || mc.thePlayer == null) {
            resetMoveState();
            return;
        }

        EntityPlayer player = mc.thePlayer;

        /*
         * 如果已经进入“拿起物品，等待放下”的状态，
         * 优先完成搬运。
         * 不要因为打开 GUI 而直接中断，
         * 否则可能出现鼠标上已经拿起物品，但是没有放下的情况。
         */
        if (state == MoveState.WAITING_TO_PLACE) {
            handleWaitingToPlace(mc, player);
            return;
        }

        /*
         * 创造模式不要自动搬。
         * 打开 GUI 时不要启动新的自动搬运。
         * 注意：
         * NEI Cheat 给物品时通常正在打开 GUI。
         * 所以这里不能清除 SlotLockManager 里记录的 EMPTY_WHEN_LOCKED。
         */
        if (mc.currentScreen != null || (mc.playerController != null && mc.playerController.isInCreativeMode())) {
            resetMoveState();
            return;
        }

        if (state == MoveState.COOLDOWN) {
            if (stateTicks > 0) {
                stateTicks--;
                return;
            }

            state = MoveState.IDLE;
        }

        /*
         * IDLE 状态下，只有鼠标上没有物品时，才允许启动新的自动搬运。
         */
        if (player.inventory.getItemStack() != null) {
            return;
        }

        /*
         * 补充记录：
         * 如果某个锁定槽当前是空的，就把它标记为“空锁定槽”。
         * 这个用于处理从配置文件加载出来的锁定槽。
         */
        markCurrentlyEmptyLockedSlots(player.inventory);

        for (int i = 0; i < 36; i++) {
            if (!SlotLockManager.isLockedPlayerIndex(i)) {
                continue;
            }

            ItemStack current = player.inventory.getStackInSlot(i);

            /*
             * 只处理：
             * 这个槽在锁定时是空的，
             * 但现在被 NEI / 服务器 / 其他逻辑塞进了物品。
             */
            if (SlotLockManager.wasEmptyWhenLocked(i) && current != null) {
                boolean started = startMoveLockedSlot(player, i);

                if (started) {
                    return;
                }
            }
        }
    }

    /**
     * 只重置搬运状态。
     */
    private void resetMoveState() {
        state = MoveState.IDLE;
        stateTicks = 0;

        pendingWindowId = -1;

        pendingTargetSlotNumber = -1;
        pendingTargetPlayerIndex = -1;

        pendingSourceSlotNumber = -1;
        pendingSourcePlayerIndex = -1;

        SlotLockInternalBypass.clear();
    }

    /**
     * 如果某个锁定槽当前为空，则标记为“空锁定槽”。
     *
     * 这样即使锁定状态是从配置文件加载的，
     * 只要 AutoMover 曾经看到它是空的，
     * 后面它被塞入物品时也会被搬走。
     */
    private void markCurrentlyEmptyLockedSlots(InventoryPlayer inventory) {
        for (int i = 0; i < 36; i++) {
            if (!SlotLockManager.isLockedPlayerIndex(i)) {
                continue;
            }

            if (inventory.getStackInSlot(i) == null) {
                SlotLockManager.markEmptyWhenLocked(i);
            }
        }
    }

    /**
     * 处理“已经拿起物品，等待放下”的阶段。
     *
     * 这里的重点：
     * AutoMover 一旦主动拿起物品，就必须尽量把它放下。
     *
     * 顺序：
     * 1. 优先放到原目标槽
     * 2. 原目标槽失效时，重新找一个未锁定空槽
     * 3. 还是失败时，尝试放回原来源槽
     */
    private void handleWaitingToPlace(Minecraft mc, EntityPlayer player) {
        stateTicks--;

        if (stateTicks > 0) {
            return;
        }

        if (player.inventory.getItemStack() != null && pendingWindowId != -1) {
            tryPlaceIntoOriginalTarget(mc, player);

            if (player.inventory.getItemStack() != null) {
                tryPlaceIntoNewUnlockedEmptySlot(mc, player);
            }

            if (player.inventory.getItemStack() != null) {
                tryPlaceBackToSource(mc, player);
            }
        }

        state = MoveState.COOLDOWN;
        stateTicks = 10;

        pendingWindowId = -1;

        pendingTargetSlotNumber = -1;
        pendingTargetPlayerIndex = -1;

        pendingSourceSlotNumber = -1;
        pendingSourcePlayerIndex = -1;

        SlotLockInternalBypass.clear();
    }

    /**
     * 尝试放到启动搬运时选中的目标槽。
     */
    private void tryPlaceIntoOriginalTarget(Minecraft mc, EntityPlayer player) {
        if (pendingTargetSlotNumber == -1 || pendingTargetPlayerIndex == -1) {
            return;
        }

        if (player.inventory.getStackInSlot(pendingTargetPlayerIndex) != null) {
            return;
        }

        mc.playerController.windowClick(pendingWindowId, pendingTargetSlotNumber, 0, 0, player);
    }

    /**
     * 如果原目标槽失效，重新找一个未锁定空槽。
     */
    private void tryPlaceIntoNewUnlockedEmptySlot(Minecraft mc, EntityPlayer player) {
        int newTargetIndex = findUnlockedEmptySlot(player.inventory);

        if (newTargetIndex < 0) {
            return;
        }

        Container container = player.inventoryContainer;

        if (container == null) {
            return;
        }

        Slot newTargetSlot = findPlayerSlot(container, player.inventory, newTargetIndex);

        if (newTargetSlot == null) {
            return;
        }

        if (newTargetSlot.getHasStack()) {
            return;
        }

        mc.playerController.windowClick(container.windowId, newTargetSlot.slotNumber, 0, 0, player);
    }

    /**
     * 如果没有任何未锁定空槽可放，最后尝试放回原锁定槽。
     *
     * 这是为了避免鼠标上残留物品。
     */
    private void tryPlaceBackToSource(Minecraft mc, EntityPlayer player) {
        if (pendingSourceSlotNumber == -1 || pendingSourcePlayerIndex == -1) {
            return;
        }

        /*
         * 只有来源槽现在仍然为空，才放回去。
         * 如果来源槽已经被别的同步填了，不要交换物品。
         */
        if (player.inventory.getStackInSlot(pendingSourcePlayerIndex) != null) {
            return;
        }

        /*
         * AutoMover 自己需要临时允许点击这个锁定来源槽。
         * 普通玩家点击 / NEI 模拟点击不会有这个 bypass。
         */
        SlotLockInternalBypass.allowPlayerIndex(pendingSourcePlayerIndex, 1000L);
        mc.playerController.windowClick(pendingWindowId, pendingSourceSlotNumber, 0, 0, player);
    }

    /**
     * 启动搬运逻辑。
     *
     * 这里只执行第一步：从锁定槽拿起物品。
     * 第二步放下会在几 tick 后执行。
     */
    private boolean startMoveLockedSlot(EntityPlayer player, int lockedPlayerIndex) {
        Minecraft mc = Minecraft.getMinecraft();

        if (mc == null || mc.playerController == null) {
            return false;
        }

        if (player.inventory.getItemStack() != null) {
            return false;
        }

        int targetPlayerIndex = findUnlockedEmptySlot(player.inventory);

        if (targetPlayerIndex < 0) {
            return false;
        }

        Container container = player.inventoryContainer;

        if (container == null) {
            return false;
        }

        Slot fromSlot = findPlayerSlot(container, player.inventory, lockedPlayerIndex);
        Slot toSlot = findPlayerSlot(container, player.inventory, targetPlayerIndex);

        if (fromSlot == null || toSlot == null) {
            return false;
        }

        if (!fromSlot.getHasStack()) {
            return false;
        }

        if (toSlot.getHasStack()) {
            return false;
        }

        /*
         * 第一步：
         * 左键拿起锁定槽里的物品。
         * 现在 MixinContainer 会拦截锁定槽的普通左键，
         * 所以 AutoMover 自己要临时 bypass。
         */
        SlotLockInternalBypass.allowPlayerIndex(lockedPlayerIndex, 1000L);
        mc.playerController.windowClick(container.windowId, fromSlot.slotNumber, 0, 0, player);

        /*
         * 如果没有成功拿起来，不进入 WAITING_TO_PLACE。
         */
        if (player.inventory.getItemStack() == null) {
            SlotLockInternalBypass.clear();
            return false;
        }

        /*
         * 第二步：
         * 延迟几个 tick 后，再放到未锁定空槽。
         */
        pendingWindowId = container.windowId;

        pendingTargetSlotNumber = toSlot.slotNumber;
        pendingTargetPlayerIndex = targetPlayerIndex;

        pendingSourceSlotNumber = fromSlot.slotNumber;
        pendingSourcePlayerIndex = lockedPlayerIndex;

        state = MoveState.WAITING_TO_PLACE;
        stateTicks = 3;

        return true;
    }

    /**
     * 找一个未锁定的空槽。
     *
     * 优先放进主背包 9-35，
     * 最后才放进快捷栏 0-8。
     */
    private int findUnlockedEmptySlot(InventoryPlayer inventory) {
        for (int i = 9; i < 36; i++) {
            if (SlotLockManager.isLockedPlayerIndex(i)) {
                continue;
            }

            if (inventory.getStackInSlot(i) == null) {
                return i;
            }
        }

        for (int i = 0; i < 9; i++) {
            if (SlotLockManager.isLockedPlayerIndex(i)) {
                continue;
            }

            if (inventory.getStackInSlot(i) == null) {
                return i;
            }
        }

        return -1;
    }

    private Slot findPlayerSlot(Container container, InventoryPlayer inventory, int playerIndex) {
        if (container == null || inventory == null) {
            return null;
        }

        for (Object object : container.inventorySlots) {
            if (!(object instanceof Slot)) {
                continue;
            }

            Slot slot = (Slot) object;

            if (slot.isSlotInInventory(inventory, playerIndex)) {
                return slot;
            }
        }

        return null;
    }
}
